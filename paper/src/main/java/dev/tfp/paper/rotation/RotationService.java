/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/RotationNetworkManager.java @ c3b4d9536 (one manager per level)
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.RotationWorld;
import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The rotation networks of every world (PAR-MECH-01), "decide async, apply sync": region-thread callbacks only snapshot a
 * block's state and {@link #put} or {@link #remove} it; each world's {@link RotationWorld} is owned by one serial actor on
 * the CPU pool, which decides the network changes; the blocks that must break go to the {@link BreakSink}, which applies
 * them on their region thread.
 *
 * <p>One actor per world, not one per region: a network may span Folia regions, and the actor never touches the world, so
 * it needs no region. Its operations run in the order they were submitted, so the networks see the block changes in the
 * order the region threads made them (per region; across regions in arrival order, as TFC's single server thread sees them).
 */
public final class RotationService {
    /** Where the blocks that must break are sent; called on the actor's thread. */
    @FunctionalInterface
    public interface BreakSink {
        void breakAt(UUID world, BlockPos pos);
    }

    private final Executor executor;
    private final BreakSink sink;
    private final Logger logger;
    private final Map<UUID, Actor> actors = new ConcurrentHashMap<>();

    public RotationService(Executor executor, BreakSink sink, Logger logger) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** A block was placed, loaded or changed. Any thread; returns at once. */
    public void put(UUID world, BlockPos pos, MechanicalBlock.State state) {
        submit(world, w -> w.put(pos, state));
    }

    /** A block was broken or unloaded. Any thread; returns at once. */
    public void remove(UUID world, BlockPos pos) {
        submit(world, w -> w.remove(pos));
    }

    /** Several blocks unloaded with their chunk. */
    public void removeAll(UUID world, List<BlockPos> positions) {
        List<BlockPos> copy = List.copyOf(positions);
        submit(world, w -> {
            List<BlockPos> broken = new ArrayList<>();
            copy.forEach(p -> broken.addAll(w.remove(p)));
            return broken;
        });
    }

    /** The readout of {@code pos} and of every block of its network, after every change submitted before. */
    public CompletableFuture<Optional<Report>> report(UUID world, BlockPos pos) {
        CompletableFuture<Optional<Report>> out = new CompletableFuture<>();
        submit(world, w -> {
            Optional<RotationWorld.Readout> at = w.readout(pos);
            out.complete(at.map(r -> new Report(r, r.network().isPresent() ? w.members(r.network().getAsLong()) : List.of(r),
                w.networkCount())));
            return List.of();
        });
        return out;
    }

    /**
     * A debug report.
     *
     * @param target   the block asked about
     * @param network  every block of its network (only itself when disconnected), in (x, y, z) order
     * @param networks the number of networks in the world
     */
    public record Report(RotationWorld.Readout target, List<RotationWorld.Readout> network, int networks) {
        public Report {
            network = List.copyOf(network);
        }
    }

    private void submit(UUID world, Function<RotationWorld, List<BlockPos>> op) {
        Actor actor = actors.computeIfAbsent(world, Actor::new);
        actor.queue.add(op);
        actor.schedule();
    }

    /** Forgets a world (unloaded). Pending operations still run. */
    public void forget(UUID world) {
        actors.remove(world);
    }

    private final class Actor implements Runnable {
        final UUID world;
        final RotationWorld state = new RotationWorld();
        final Queue<Function<RotationWorld, List<BlockPos>>> queue = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean();

        Actor(UUID world) {
            this.world = world;
        }

        void schedule() {
            if (running.compareAndSet(false, true)) {
                executor.execute(this);
            }
        }

        @Override
        public void run() {
            try {
                Function<RotationWorld, List<BlockPos>> op;
                while ((op = queue.poll()) != null) {
                    List<BlockPos> broken;
                    try {
                        broken = op.apply(state);
                    } catch (RuntimeException e) {
                        logger.log(Level.SEVERE, "rotation network of world " + world + ": an update failed", e);
                        continue;
                    }
                    for (BlockPos pos : broken) {
                        sink.breakAt(world, pos);
                    }
                }
            } finally {
                running.set(false);
            }
            if (!queue.isEmpty()) {
                schedule();
            }
        }
    }
}
