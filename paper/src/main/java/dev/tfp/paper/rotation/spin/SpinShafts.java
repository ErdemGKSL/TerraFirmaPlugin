/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (angle(partialTick): every block of a network
 *       turns by the source's angle times its direction's axis step)
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.machine.RotationAnglesSpin.Track;
import dev.tfp.core.machine.RotationAnglesSpin.Update;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import java.util.ArrayDeque;
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
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * The spinning axle shafts on the wire (PAR-MECH-12), lazily: nothing runs per tick per shaft. Every axle is one packet-only
 * {@code item_display} per viewer (also at rest: the axle block draws no static element). A shaft's angle is a straight line in
 * time ({@link Track}); the shafts of one network share the network's phase (the source's unbounded angle, TFC's
 * {@code Rotation.angle}), so they stay in step. One packet goes out when the speed changes or a viewer starts seeing the shaft,
 * then one per segment ({@link RotationAnglesSpin#plan}: 37 ticks at 30 rpm) from a queue ordered by due tick.
 *
 * <p>A freshly spawned display cannot interpolate in its first client tick (it has no render state yet), so a spawn shows the
 * angle of now and the segment's target follows one tick later with the remaining duration.
 *
 * <p>Viewers come from the chunk packets (a shaft is seen by every player its chunk was sent to). The item (the axle's wood) is
 * read once per new shaft on its region thread through the {@link ItemResolver}; the shaft is shown once it is known.
 *
 * <p>Thread contract: every public method is {@code synchronized}; callers are the rotation actors (reports), the Netty threads
 * (chunk packets), the global scheduler ({@link #tick}) and the resolver callbacks. No world access here; the wire only sends.
 * This is the platform side of the engine PAR-MECH-02 wrote as {@code integration/craftengine/rotation/SpinVisualService}, which
 * the paper module cannot reach (the integration module depends on it, not the reverse).
 */
public final class SpinShafts {

    /** Reads the display item of the axle at {@code pos} on its region thread; {@code done} gets it, or empty when gone. */
    @FunctionalInterface
    public interface ItemResolver {
        void resolve(UUID world, BlockPos pos, Consumer<Optional<String>> done);
    }

    private final SpinWire wire;
    private final IntSupplier entityIds;
    private final ItemResolver items;
    private final Map<Key, Shaft> shafts = new HashMap<>();
    private final Map<UUID, Map<Long, Set<BlockPos>>> byChunk = new HashMap<>();
    private final Map<NetKey, Phase> phases = new HashMap<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final PriorityQueue<Due> due = new PriorityQueue<>();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private long now;
    private long seq;
    private long packets;

    public SpinShafts(SpinWire wire, IntSupplier entityIds, ItemResolver items) {
        this.wire = Objects.requireNonNull(wire, "wire");
        this.entityIds = Objects.requireNonNull(entityIds, "entityIds");
        this.items = Objects.requireNonNull(items, "items");
    }

    /** The spin clock: ticks since the shafts started. */
    public synchronized long now() {
        return now;
    }

    /** Spawn and segment packets sent so far. */
    public synchronized long packets() {
        return packets;
    }

    public synchronized int shafts() {
        return shafts.size();
    }

    public synchronized int networks() {
        return phases.size();
    }

    /** The viewers of the shaft at {@code pos} (tests and debug). */
    public synchronized Set<UUID> viewers(UUID world, BlockPos pos) {
        Shaft s = shafts.get(new Key(world, pos));
        return s == null ? Set.of() : new LinkedHashSet<>(s.viewers.keySet());
    }

    /** The angle the shaft at {@code pos} shows now (radians about its positive axis), empty when unknown. */
    public synchronized Optional<Double> angle(UUID world, BlockPos pos) {
        Shaft s = shafts.get(new Key(world, pos));
        return s == null ? Optional.empty() : Optional.of(s.track.phiAt(now));
    }

    /**
     * PAR-MECH-26: the source-angle line ({@code Rotation.angle}, unbounded) of the network the shaft at {@code pos} turns in,
     * empty when the shaft is unknown or in no network. A crank next to it reads its phase here.
     */
    public synchronized Optional<Track> sourceTrack(UUID world, BlockPos pos) {
        Shaft s = shafts.get(new Key(world, pos));
        if (s == null || s.network < 0) {
            return Optional.empty();
        }
        Phase p = phases.get(new NetKey(world, s.network));
        return p == null ? Optional.empty() : Optional.of(p.track);
    }

    /** PAR-MECH-26: the display item of the shaft at {@code pos}, empty when unknown or not resolved yet. */
    public synchronized Optional<String> item(UUID world, BlockPos pos) {
        Shaft s = shafts.get(new Key(world, pos));
        return s == null ? Optional.empty() : Optional.ofNullable(s.item);
    }

    /** One game tick: the clock advances, the delayed first segments and the due segments go out. Global scheduler. */
    public synchronized void tick() {
        now++;
        Pending p;
        while ((p = pending.peek()) != null && p.tick <= now) {
            pending.poll();
            Shaft s = p.shaft;
            Integer id = s.viewers.get(p.viewer);
            if (shafts.get(s.key) != s || s.generation != p.generation || id == null || id != p.entityId) {
                continue;
            }
            int remaining = (int) (s.segment.tick() + s.segment.duration() - now);
            if (remaining > 0) {
                wire.turn(p.viewer, id, s.axis, s.segment.halfAngle(), remaining);
                packets++;
            }
        }
        Due head;
        while ((head = due.peek()) != null && head.tick <= now) {
            due.poll();
            Shaft s = head.shaft;
            if (shafts.get(s.key) != s || head.generation != s.generation) {
                continue; // removed or retargeted since: a newer entry exists
            }
            schedule(s);
            s.viewers.forEach((viewer, id) -> turn(s, viewer, id));
        }
    }

    /**
     * The rotation network actor's report for one world: the shafts that appeared or changed and the ones that are gone. Any
     * thread.
     */
    public synchronized void shafts(UUID world, List<ShaftReport> changed, List<BlockPos> removed) {
        for (BlockPos pos : removed) {
            removeShaft(new Key(world, pos));
        }
        for (ShaftReport r : changed) {
            Key key = new Key(world, r.pos());
            Shaft s = shafts.get(key);
            if (s != null && s.axis != r.axis()) {
                removeShaft(key); // the block was replaced: a new display
                s = null;
            }
            if (s == null) {
                Shaft fresh = new Shaft(key, r.axis(), now);
                shafts.put(key, fresh);
                byChunk.computeIfAbsent(world, w -> new HashMap<>())
                    .computeIfAbsent(r.pos().chunkKey(), c -> new HashSet<>()).add(r.pos());
                retarget(fresh, r);
                items.resolve(world, r.pos(), item -> resolved(fresh, item));
            } else {
                retarget(s, r);
            }
        }
    }

    /** The viewer is now in {@code world} (join, respawn, world change): it sees none of its old chunks. Netty thread. */
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

    /** The chunk was sent to the viewer: its shafts spawn. Netty thread, after the chunk packet. */
    public synchronized void chunkSent(UUID viewer, int chunkX, int chunkZ) {
        Viewer v = viewers.get(viewer);
        if (v == null || v.world == null) {
            return;
        }
        long chunk = ChunkPos.key(chunkX, chunkZ);
        v.chunks.add(chunk);
        for (BlockPos pos : inChunk(v.world, chunk)) {
            Shaft s = shafts.get(new Key(v.world, pos));
            if (s != null) {
                addViewer(s, viewer);
            }
        }
    }

    /** The viewer's client dropped the chunk: its displays go. Netty thread. */
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

    /** The viewer left: forgotten everywhere without packets (its connection is gone). */
    public synchronized void quit(UUID viewer) {
        Viewer v = viewers.remove(viewer);
        if (v != null && v.world != null) {
            for (long chunk : v.chunks) {
                for (BlockPos pos : inChunk(v.world, chunk)) {
                    Shaft s = shafts.get(new Key(v.world, pos));
                    if (s != null) {
                        s.viewers.remove(viewer);
                    }
                }
            }
        }
        wire.forget(viewer);
    }

    private void hide(UUID viewer, Viewer v, long chunk, boolean send) {
        for (BlockPos pos : inChunk(v.world, chunk)) {
            Shaft s = shafts.get(new Key(v.world, pos));
            Integer id = s == null ? null : s.viewers.remove(viewer);
            if (id != null && send) {
                wire.remove(viewer, id);
            }
        }
    }

    private Set<BlockPos> inChunk(UUID world, long chunk) {
        Map<Long, Set<BlockPos>> m = byChunk.get(world);
        Set<BlockPos> in = m == null ? null : m.get(chunk);
        return in == null ? Set.of() : Set.copyOf(in);
    }

    private synchronized void resolved(Shaft s, Optional<String> item) {
        if (shafts.get(s.key) != s || item.isEmpty()) {
            return;
        }
        s.item = item.get();
        long chunk = s.key.pos.chunkKey();
        viewers.forEach((viewer, v) -> {
            if (s.key.world.equals(v.world) && v.chunks.contains(chunk)) {
                addViewer(s, viewer);
            }
        });
    }

    private void addViewer(Shaft s, UUID viewer) {
        if (s.item == null || s.viewers.containsKey(viewer)) {
            return;
        }
        int id = entityIds.getAsInt();
        s.viewers.put(viewer, id);
        wire.spawn(viewer, id, s.key.pos, s.item, s.axis, s.track.phiAt(now) / 2);
        packets++;
        if (s.segment.duration() > 0) {
            pending.add(new Pending(now + 1, s, s.generation, viewer, id));
        }
    }

    private void turn(Shaft s, UUID viewer, int id) {
        wire.turn(viewer, id, s.axis, s.segment.halfAngle(), s.segment.duration());
        packets++;
    }

    private void retarget(Shaft s, ShaftReport r) {
        double phiNow = s.track.phiAt(now);
        NetKey old = s.network < 0 ? null : new NetKey(s.key.world, s.network);
        NetKey next = r.network() < 0 ? null : new NetKey(s.key.world, r.network());
        if (!Objects.equals(old, next)) {
            leave(old);
            if (next != null) {
                phases.computeIfAbsent(next, k -> new Phase(Track.rest(now, 0))).members++;
            }
        }
        if (next == null) {
            s.track = Track.rest(now, phiNow);
        } else {
            Phase p = phases.get(next);
            if (p.track.rate() != r.speed()) {
                p.track = p.track.retarget(now, r.speed());
            }
            double unit = RotationAnglesSpin.rate(r.direction(), 1f);
            s.track = new Track(now, unit * p.track.phiAt(now), RotationAnglesSpin.rate(r.direction(), r.speed()));
        }
        s.network = r.network();
        s.generation++;
        schedule(s);
        s.viewers.forEach((viewer, id) -> turn(s, viewer, id));
    }

    private void leave(NetKey key) {
        if (key == null) {
            return;
        }
        Phase p = phases.get(key);
        if (p != null && --p.members <= 0) {
            phases.remove(key);
        }
    }

    private void removeShaft(Key key) {
        Shaft s = shafts.remove(key);
        if (s == null) {
            return;
        }
        Map<Long, Set<BlockPos>> m = byChunk.get(key.world);
        if (m != null) {
            Set<BlockPos> in = m.get(key.pos.chunkKey());
            if (in != null && in.remove(key.pos) && in.isEmpty()) {
                m.remove(key.pos.chunkKey());
            }
        }
        leave(s.network < 0 ? null : new NetKey(key.world, s.network));
        s.generation++;
        s.viewers.forEach(wire::remove);
        s.viewers.clear();
    }

    /** Plans the segment from now for every viewer of the shaft and queues the next one. */
    private void schedule(Shaft s) {
        s.segment = RotationAnglesSpin.plan(s.track, now);
        if (s.segment.nextTick() >= 0) {
            due.add(new Due(s.segment.nextTick(), seq++, s, s.generation));
        }
    }

    private record Key(UUID world, BlockPos pos) {
    }

    private record NetKey(UUID world, long network) {
    }

    private static final class Phase {
        Track track;
        int members;

        Phase(Track track) {
            this.track = track;
        }
    }

    private static final class Shaft {
        final Key key;
        final RotationAxis axis;
        final Map<UUID, Integer> viewers = new HashMap<>();
        Track track;
        Update segment;
        long network = ShaftReport.NO_NETWORK;
        int generation;
        String item;

        Shaft(Key key, RotationAxis axis, long now) {
            this.key = key;
            this.axis = axis;
            this.track = Track.rest(now, 0);
            this.segment = RotationAnglesSpin.plan(track, now);
        }
    }

    private static final class Viewer {
        UUID world;
        final Set<Long> chunks = new HashSet<>();

        Viewer(UUID world) {
            this.world = world;
        }
    }

    private record Pending(long tick, Shaft shaft, int generation, UUID viewer, int entityId) {
    }

    private record Due(long tick, long seq, Shaft shaft, int generation) implements Comparable<Due> {
        @Override
        public int compareTo(Due o) {
            int c = Long.compare(tick, o.tick);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }
}
