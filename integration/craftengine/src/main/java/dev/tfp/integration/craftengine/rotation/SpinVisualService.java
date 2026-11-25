/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.rotation;

import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.machine.RotationAnglesSpin.Track;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.LinkedHashSet;

/**
 * Drives the spinning shafts of the rotation network as display packets (PAR-MECH-02), lazily: nothing runs per tick per shaft.
 * A shaft's spin is a {@link Track} (a straight line in time); the service sends one packet when the speed changes or a viewer
 * starts seeing the shaft, then one per segment ({@link RotationAnglesSpin#plan}: 37 ticks at 30 rpm) from a queue ordered by due
 * tick, so a world of idle or steady shafts costs a peek at the queue head per tick.
 *
 * <p>Thread contract: not thread-safe on purpose. Like the rotation networks, one serial owner (the world's network actor, or a
 * single scheduler task) calls every method; the sink does the (thread-safe) sending. Callers snapshot, this decides, the sink
 * sends: no world access here, so there is nothing to apply on a region thread.
 */
public final class SpinVisualService {

    private final SpinSink sink;
    private final IntSupplier entityIds;
    private final Map<Key, Shaft> shafts = new HashMap<>();
    private final PriorityQueue<Due> due = new PriorityQueue<>();
    private long seq;
    private long packets;

    /**
     * @param sink      where the packets go
     * @param entityIds a source of display entity ids that never collide with the server's
     */
    public SpinVisualService(SpinSink sink, IntSupplier entityIds) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.entityIds = Objects.requireNonNull(entityIds, "entityIds");
    }

    /** Packets sent through {@link SpinSink#spawn} and {@link SpinSink#update} so far (for the stats and the tests). */
    public long packets() {
        return packets;
    }

    public int shafts() {
        return shafts.size();
    }

    /**
     * A shaft's rotation changed (network speed or hand) or the shaft appeared: it turns by {@code speed} (the source's signed
     * radians per tick) with the block's {@code direction}. The angle is kept continuous. {@code speed} 0 stops it.
     *
     * @param phase the source's unbounded angle at {@code tick} (shared by every block of the network, so shafts stay in step)
     */
    public void setRotation(UUID world, BlockPos pos, RotationAxis axis, Direction direction, float speed, double phase, long tick) {
        Key key = new Key(world, pos);
        Shaft s = shafts.computeIfAbsent(key, k -> new Shaft(k, axis));
        s.axis = axis;
        double rate = RotationAnglesSpin.rate(direction, speed);
        double phi = RotationAnglesSpin.rate(direction, 1f) * phase;
        // the angle is the network's: a shaft that joins another network may differ from what its viewers show, and the first
        // segment then turns the short way to the new line (a speed change of the same network is continuous)
        s.track = new Track(tick, phi, rate);
        s.generation++;
        for (Map.Entry<UUID, Integer> v : s.viewers.entrySet()) {
            send(s, v.getKey(), v.getValue(), tick, false);
        }
        schedule(s, tick);
    }

    /** The shaft is gone (broken, unloaded): every viewer loses its display. */
    public void removeShaft(UUID world, BlockPos pos) {
        Shaft s = shafts.remove(new Key(world, pos));
        if (s == null) {
            return;
        }
        s.generation++;
        s.viewers.forEach((viewer, id) -> sink.remove(viewer, id));
    }

    /** A player can now see the shaft (its chunk was sent): spawn at the angle it has now and start the segment. */
    public void addViewer(UUID world, BlockPos pos, UUID viewer, long tick) {
        Shaft s = shafts.get(new Key(world, pos));
        if (s == null || s.viewers.containsKey(viewer)) {
            return;
        }
        int id = entityIds.getAsInt();
        s.viewers.put(viewer, id);
        send(s, viewer, id, tick, true);
    }

    /** The player no longer sees the shaft (left, chunk unloaded, changed world). */
    public void removeViewer(UUID world, BlockPos pos, UUID viewer) {
        Shaft s = shafts.get(new Key(world, pos));
        if (s == null) {
            return;
        }
        Integer id = s.viewers.remove(viewer);
        if (id != null) {
            sink.remove(viewer, id);
        }
    }

    /** A player left the server: forget it in every shaft without sending (its connection is gone). */
    public void forgetViewer(UUID viewer) {
        shafts.values().forEach(s -> s.viewers.remove(viewer));
    }

    /** Sends the segments due at or before {@code tick}. Call once per tick (or less often: late segments are still correct). */
    public void tick(long tick) {
        Due head;
        while ((head = due.peek()) != null && head.tick <= tick) {
            due.poll();
            Shaft s = head.shaft;
            if (shafts.get(s.key) != s || head.generation != s.generation) {
                continue; // removed or retargeted since: a newer entry exists
            }
            for (Map.Entry<UUID, Integer> v : s.viewers.entrySet()) {
                send(s, v.getKey(), v.getValue(), tick, false);
            }
            schedule(s, tick);
        }
    }

    /** The viewers of a shaft, for the tests and the debug readout. */
    public Set<UUID> viewers(UUID world, BlockPos pos) {
        Shaft s = shafts.get(new Key(world, pos));
        return s == null ? Set.of() : new LinkedHashSet<>(s.viewers.keySet());
    }

    private void send(Shaft s, UUID viewer, int id, long tick, boolean spawn) {
        RotationAnglesSpin.Update u = RotationAnglesSpin.plan(s.track, tick);
        if (spawn) {
            sink.spawn(viewer, s.key.world, s.key.pos, s.axis, id, u);
        } else {
            sink.update(viewer, id, u);
        }
        packets++;
    }

    private void schedule(Shaft s, long tick) {
        long next = RotationAnglesSpin.plan(s.track, tick).nextTick();
        if (next >= 0) {
            due.add(new Due(next, seq++, s, s.generation));
        }
    }

    private record Key(UUID world, BlockPos pos) {
    }

    private static final class Shaft {
        final Key key;
        RotationAxis axis;
        Track track = Track.rest(0, 0);
        int generation;
        final Map<UUID, Integer> viewers = new HashMap<>();

        Shaft(Key key, RotationAxis axis) {
            this.key = key;
            this.axis = axis;
        }
    }

    private record Due(long tick, long seq, Shaft shaft, int generation) implements Comparable<Due> {
        @Override
        public int compareTo(Due o) {
            int c = Long.compare(tick, o.tick);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }

    /** All viewers' packet count helper for tests: the shafts a viewer sees. */
    List<Integer> idsOf(UUID world, BlockPos pos) {
        Shaft s = shafts.get(new Key(world, pos));
        return s == null ? List.of() : new ArrayList<>(s.viewers.values());
    }
}
