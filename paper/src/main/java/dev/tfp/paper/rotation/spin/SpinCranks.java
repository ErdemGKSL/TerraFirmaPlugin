/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java @ c3b4d9536 (what is drawn:
 *       the wheel on the base, the axle extension while an axle is on the sink side and the base is connected, the shaft's rod
 *       when the shaft stands in front), src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (a null
 *       rotation reads angle 0)
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.machine.RotationAngles;
import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.machine.RotationAnglesSpin.Track;
import dev.tfp.core.machine.RotationAnglesSpin.Update;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * The crankshafts' moving parts (PAR-MECH-26), lazily like the axles ({@link SpinShafts}): per viewer and crank up to five
 * packet-only {@code item_display}s, the wheel and the axle stub on the base and the slider (connecting box and piston), the
 * arm and the pump attachment on the shaft. The source angle of a crank is a straight line in time ({@link Track}): the network's phase when an axle of the
 * spinning shafts is next to its sink face (so the wheel turns in step with it), else its own.
 *
 * <ul>
 *   <li>The wheel and stub turn about one axis: one packet per {@link RotationAnglesSpin#plan} segment, like an axle.</li>
 *   <li>The shaft's stroke is not linear in the angle: it goes out as keyframes of {@link CrankshaftRules#shaftMovement}
 *       ({@link #KEYFRAMES_PER_TURN} a turn, at most {@link #MAX_SHAFT_STEP} ticks apart) that the client interpolates.</li>
 *   <li>A crank in no network shows TFC's angle 0 (a null rotation): the rest pose.</li>
 * </ul>
 *
 * <p>Thread contract: every public method is {@code synchronized}; callers are the crank feed (region threads), the Netty
 * threads (chunk packets) and the global scheduler ({@link #tick}). The phase lookup may lock the {@link SpinShafts} (never
 * the reverse). No world access here; the wire only sends.
 */
public final class SpinCranks {
    /** Keyframes of the shaft's stroke per turn. */
    public static final int KEYFRAMES_PER_TURN = 16;
    /** The longest gap between two shaft keyframes, in ticks. */
    public static final int MAX_SHAFT_STEP = 20;
    static final int WHEEL = 0;
    static final int STUB = 1;
    static final int SLIDER = 2;
    static final int ARM = 3;
    static final int PUMP = 4;

    /** The source-angle line of the network the axle at {@code pos} turns in (the spinning shafts' clock), if any. */
    @FunctionalInterface
    public interface Phases {
        Phases NONE = (world, pos) -> Optional.empty();

        Optional<Track> source(UUID world, BlockPos pos);
    }

    private final CrankWire wire;
    private final IntSupplier entityIds;
    private final Phases phases;
    private final Map<Key, Crank> cranks = new HashMap<>();
    private final Map<UUID, Map<Long, Set<BlockPos>>> byChunk = new HashMap<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final PriorityQueue<Due> due = new PriorityQueue<>();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private long now;
    private long seq;
    private long packets;

    public SpinCranks(CrankWire wire, IntSupplier entityIds, Phases phases) {
        this.wire = Objects.requireNonNull(wire, "wire");
        this.entityIds = Objects.requireNonNull(entityIds, "entityIds");
        this.phases = Objects.requireNonNull(phases, "phases");
    }

    public synchronized long now() {
        return now;
    }

    public synchronized long packets() {
        return packets;
    }

    public synchronized int cranks() {
        return cranks.size();
    }

    /** The bases known in {@code world} (the feed's sweep). */
    public synchronized List<BlockPos> bases(UUID world) {
        List<BlockPos> out = new ArrayList<>();
        cranks.keySet().forEach(k -> {
            if (k.world.equals(world)) {
                out.add(k.pos);
            }
        });
        return out;
    }

    /** The worlds with known bases. */
    public synchronized Set<UUID> worlds() {
        Set<UUID> out = new HashSet<>();
        cranks.keySet().forEach(k -> out.add(k.world));
        return out;
    }

    /** The viewers of the crank at {@code base}. */
    public synchronized Set<UUID> viewers(UUID world, BlockPos base) {
        Crank c = cranks.get(new Key(world, base));
        return c == null ? Set.of() : new LinkedHashSet<>(c.viewers.keySet());
    }

    /** The source angle the crank at {@code base} reads now, empty when unknown. */
    public synchronized Optional<Double> sourceAngle(UUID world, BlockPos base) {
        Crank c = cranks.get(new Key(world, base));
        return c == null ? Optional.empty() : Optional.of(c.source.phiAt(now));
    }

    /** The wheel's angle now about the positive {@link CrankParts#wheelAxis}, empty when unknown. */
    public synchronized Optional<Double> wheelAngle(UUID world, BlockPos base) {
        Crank c = cranks.get(new Key(world, base));
        return c == null ? Optional.empty() : Optional.of(c.wheel().phiAt(now));
    }

    /** The shaft's movement now (TFC {@code calculateShaftMovement}), empty when unknown. */
    public synchronized Optional<CrankshaftRules.ShaftMovement> movement(UUID world, BlockPos base) {
        Crank c = cranks.get(new Key(world, base));
        return c == null ? Optional.empty() : Optional.of(c.movementAt(now));
    }

    /** One game tick: the clock advances, the delayed first targets and the due segments go out. Global scheduler. */
    public synchronized void tick() {
        now++;
        Pending p;
        while ((p = pending.peek()) != null && p.tick <= now) {
            pending.poll();
            Crank c = p.crank;
            int[] ids = c.viewers.get(p.viewer);
            if (cranks.get(c.key) != c || c.generation != p.generation || ids != p.ids) {
                continue;
            }
            int wheelLeft = (int) (c.wheelSegment.tick() + c.wheelSegment.duration() - now);
            if (wheelLeft > 0) {
                sendWheel(c, p.viewer, ids, wheelLeft);
            }
            int shaftLeft = (int) (c.shaftEnd - now);
            if (c.report.shaft() && shaftLeft > 0) {
                sendShaft(c, p.viewer, ids, shaftLeft);
            }
        }
        Due head;
        while ((head = due.peek()) != null && head.tick <= now) {
            due.poll();
            Crank c = head.crank;
            if (cranks.get(c.key) != c || head.generation != c.generation) {
                continue;
            }
            if (head.part == WHEEL) {
                planWheel(c);
                c.viewers.forEach((viewer, ids) -> sendWheel(c, viewer, ids, c.wheelSegment.duration()));
            } else {
                planShaft(c);
                c.viewers.forEach((viewer, ids) -> sendShaft(c, viewer, ids, (int) (c.shaftEnd - now)));
            }
        }
    }

    /** The feed's reading of a base: it appears, or its rotation, shaft or stub changed. Any thread. */
    public synchronized void crank(UUID world, CrankReport report) {
        Key key = new Key(world, report.base());
        Crank c = cranks.get(key);
        if (c == null) {
            c = new Crank(key, report, Track.rest(now, 0));
            cranks.put(key, c);
            byChunk.computeIfAbsent(world, w -> new HashMap<>()).computeIfAbsent(report.base().chunkKey(), k -> new HashSet<>())
                .add(report.base());
            retarget(c);
            long chunk = report.base().chunkKey();
            Crank fresh = c;
            viewers.forEach((viewer, v) -> {
                if (world.equals(v.world) && v.chunks.contains(chunk)) {
                    addViewer(fresh, viewer);
                }
            });
            return;
        }
        if (c.report.equals(report)) {
            return;
        }
        boolean respawn = c.report.facing() != report.facing() || c.report.shaft() != report.shaft()
            || c.report.pump() != report.pump() || !Objects.equals(c.report.stub(), report.stub());
        c.report = report;
        if (!respawn) {
            retarget(c);
            Crank live = c;
            c.viewers.forEach((viewer, ids) -> {
                sendWheel(live, viewer, ids, live.wheelSegment.duration());
                if (live.report.shaft()) {
                    sendShaft(live, viewer, ids, (int) (live.shaftEnd - now));
                }
            });
            return;
        }
        List<UUID> seeing = new ArrayList<>(c.viewers.keySet());
        c.viewers.forEach(wire::removeParts);
        c.viewers.clear();
        retarget(c);
        for (UUID viewer : seeing) {
            addViewer(c, viewer);
        }
    }

    /** The base at {@code base} is gone (or its chunk unloaded): its displays go. Any thread. */
    public synchronized void remove(UUID world, BlockPos base) {
        Key key = new Key(world, base);
        Crank c = cranks.remove(key);
        if (c == null) {
            return;
        }
        Map<Long, Set<BlockPos>> m = byChunk.get(world);
        if (m != null) {
            Set<BlockPos> in = m.get(base.chunkKey());
            if (in != null && in.remove(base) && in.isEmpty()) {
                m.remove(base.chunkKey());
            }
        }
        c.generation++;
        c.viewers.forEach(wire::removeParts);
        c.viewers.clear();
    }

    /** The bases known in the chunk. */
    public synchronized List<BlockPos> inChunk(UUID world, int chunkX, int chunkZ) {
        return List.copyOf(positions(world, ChunkPos.key(chunkX, chunkZ)));
    }

    /** The viewer is now in {@code world}: it sees none of its old chunks. Netty thread. */
    public synchronized void viewerWorld(UUID viewer, UUID world) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            viewers.put(viewer, new Viewer(world));
            return;
        }
        for (long chunk : List.copyOf(v.chunks)) {
            hide(viewer, v, chunk, false);
        }
        v.chunks.clear();
        v.world = world;
    }

    /** The chunk was sent to the viewer: its cranks spawn. Netty thread, after the chunk packet. */
    public synchronized void chunkSent(UUID viewer, int chunkX, int chunkZ) {
        Viewer v = viewers.get(viewer);
        if (v == null || v.world == null) {
            return;
        }
        long chunk = ChunkPos.key(chunkX, chunkZ);
        v.chunks.add(chunk);
        for (BlockPos pos : positions(v.world, chunk)) {
            Crank c = cranks.get(new Key(v.world, pos));
            if (c != null) {
                addViewer(c, viewer);
            }
        }
    }

    /** The viewer's client dropped the chunk. Netty thread. */
    public synchronized void chunkUnloaded(UUID viewer, int chunkX, int chunkZ) {
        Viewer v = viewers.get(viewer);
        if (v == null) {
            return;
        }
        long chunk = ChunkPos.key(chunkX, chunkZ);
        if (v.chunks.remove(chunk)) {
            hide(viewer, v, chunk, true);
        }
    }

    /** The viewer left: forgotten without packets. */
    public synchronized void quit(UUID viewer) {
        Viewer v = viewers.remove(viewer);
        if (v != null && v.world != null) {
            for (long chunk : v.chunks) {
                for (BlockPos pos : positions(v.world, chunk)) {
                    Crank c = cranks.get(new Key(v.world, pos));
                    if (c != null) {
                        c.viewers.remove(viewer);
                    }
                }
            }
        }
    }

    private void hide(UUID viewer, Viewer v, long chunk, boolean send) {
        for (BlockPos pos : positions(v.world, chunk)) {
            Crank c = cranks.get(new Key(v.world, pos));
            int[] ids = c == null ? null : c.viewers.remove(viewer);
            if (ids != null && send) {
                wire.removeParts(viewer, ids);
            }
        }
    }

    private Set<BlockPos> positions(UUID world, long chunk) {
        Map<Long, Set<BlockPos>> m = byChunk.get(world);
        Set<BlockPos> in = m == null ? null : m.get(chunk);
        return in == null ? Set.of() : Set.copyOf(in);
    }

    /** The source line from the report: the network's phase, else continuous; TFC's angle 0 out of a network. */
    private void retarget(Crank c) {
        CrankReport r = c.report;
        if (!r.connected()) {
            c.source = Track.rest(now, 0);
        } else {
            BlockPos sink = r.base().offset(CrankshaftRules.sinkFace(r.facing()));
            Optional<Track> net = phases.source(c.key.world, sink);
            double phi = net.map(t -> t.phiAt(now)).orElseGet(() -> c.source.phiAt(now));
            c.source = new Track(now, phi, r.speed());
        }
        c.generation++;
        planWheel(c);
        planShaft(c);
    }

    private void planWheel(Crank c) {
        c.wheelSegment = RotationAnglesSpin.plan(c.wheel(), now);
        if (c.wheelSegment.nextTick() >= 0) {
            due.add(new Due(c.wheelSegment.nextTick(), seq++, c, c.generation, WHEEL));
        }
    }

    private void planShaft(Crank c) {
        int step = shaftStep(c.source.rate());
        c.shaftEnd = now + step;
        c.shaftTarget = c.movementAt(c.shaftEnd);
        if (step > 0 && c.report.shaft()) {
            due.add(new Due(c.shaftEnd, seq++, c, c.generation, SLIDER));
        }
    }

    /** Ticks between two shaft keyframes at {@code rate} radians per tick; 0 at rest. */
    static int shaftStep(double rate) {
        if (rate == 0) {
            return 0;
        }
        double period = RotationAngles.TWO_PI / Math.abs(rate);
        return (int) Math.max(1, Math.min(MAX_SHAFT_STEP, Math.round(period / KEYFRAMES_PER_TURN)));
    }

    private void addViewer(Crank c, UUID viewer) {
        if (c.viewers.containsKey(viewer)) {
            return;
        }
        int[] ids = {entityIds.getAsInt(), entityIds.getAsInt(), entityIds.getAsInt(), entityIds.getAsInt(),
            entityIds.getAsInt()};
        c.viewers.put(viewer, ids);
        CrankReport r = c.report;
        double half = c.wheel().phiAt(now) / 2;
        wire.spawnPart(viewer, ids[WHEEL], r.base(), CrankParts.WHEEL_ITEM, CrankParts.wheel(r.facing(), half));
        packets++;
        if (r.stub() != null) {
            wire.spawnPart(viewer, ids[STUB], r.base(), r.stub(), CrankParts.stub(r.facing(), half));
            packets++;
        }
        if (r.shaft()) {
            BlockPos shaft = r.base().offset(r.facing());
            CrankshaftRules.ShaftMovement m = c.movementAt(now);
            wire.spawnPart(viewer, ids[SLIDER], shaft, CrankParts.SLIDER_ITEM, CrankParts.slider(r.facing(), m));
            wire.spawnPart(viewer, ids[ARM], shaft, CrankParts.ARM_ITEM, CrankParts.arm(r.facing(), m));
            packets += 2;
            if (r.pump()) {
                wire.spawnPart(viewer, ids[PUMP], shaft, CrankParts.PUMP_ITEM, CrankParts.slider(r.facing(), m));
                packets++;
            }
        }
        if (c.wheelSegment.duration() > 0 || c.shaftEnd > now) {
            pending.add(new Pending(now + 1, c, c.generation, viewer, ids));
        }
    }

    private void sendWheel(Crank c, UUID viewer, int[] ids, int duration) {
        double half = c.wheelSegment.halfAngle();
        wire.movePart(viewer, ids[WHEEL], CrankParts.wheel(c.report.facing(), half), duration);
        packets++;
        if (c.report.stub() != null) {
            wire.movePart(viewer, ids[STUB], CrankParts.stub(c.report.facing(), half), duration);
            packets++;
        }
    }

    private void sendShaft(Crank c, UUID viewer, int[] ids, int duration) {
        wire.movePart(viewer, ids[SLIDER], CrankParts.slider(c.report.facing(), c.shaftTarget), Math.max(0, duration));
        wire.movePart(viewer, ids[ARM], CrankParts.arm(c.report.facing(), c.shaftTarget), Math.max(0, duration));
        packets += 2;
        if (c.report.pump()) {
            wire.movePart(viewer, ids[PUMP], CrankParts.slider(c.report.facing(), c.shaftTarget), Math.max(0, duration));
            packets++;
        }
    }

    private record Key(UUID world, BlockPos pos) {
    }

    private static final class Crank {
        final Key key;
        final Map<UUID, int[]> viewers = new HashMap<>();
        CrankReport report;
        Track source;
        Update wheelSegment;
        long shaftEnd;
        CrankshaftRules.ShaftMovement shaftTarget;
        int generation;

        Crank(Key key, CrankReport report, Track source) {
            this.key = key;
            this.report = report;
            this.source = source;
        }

        /** The wheel's line: the angle an axle of the same rotation shows, {@code -step(direction) * source}. */
        Track wheel() {
            double unit = RotationAnglesSpin.rate(report.direction(), 1f);
            return new Track(source.tick(), unit * source.phi(), unit * source.rate());
        }

        CrankshaftRules.ShaftMovement movementAt(long tick) {
            return CrankParts.movement(report.direction(), source.phiAt(tick), report.facing());
        }
    }

    private static final class Viewer {
        UUID world;
        final Set<Long> chunks = new HashSet<>();

        Viewer(UUID world) {
            this.world = world;
        }
    }

    private record Pending(long tick, Crank crank, int generation, UUID viewer, int[] ids) {
    }

    private record Due(long tick, long seq, Crank crank, int generation, int part) implements Comparable<Due> {
        @Override
        public int compareTo(Due o) {
            int c = Long.compare(tick, o.tick);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }
}
