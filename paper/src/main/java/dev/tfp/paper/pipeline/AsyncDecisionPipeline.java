/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * "Decide async, apply sync" (ARCHITECTURE.md principle 6). One instance per job kind and world.
 *
 * <pre>
 * submit(pos)        owner thread of pos: coalesce, capture a small snapshot, hand off. No neighbour logic.
 * decide(snapshot)   worker pool: pure function returning BlockChanges (may cascade)
 * enqueue            owner thread of each affected chunk: RegionCommitQueue validates and applies
 * </pre>
 *
 * Coalescing never drops a trigger: if a position is submitted while its job is in flight, the job runs
 * again with a fresh snapshot afterwards (the in-flight decision may have read a neighbourhood that
 * changed). Stale changes come back through {@link #submit}. Both rules were found necessary by the
 * M0-ASYNC-01 race test.
 *
 * @param <S> block state type, see {@link BlockAccess}
 * @param <N> snapshot type handed to {@code decide}
 */
public final class AsyncDecisionPipeline<S, N> {
    /** Captures the snapshot for a position; called on its owner thread. */
    @FunctionalInterface
    public interface SnapshotSource<N> {
        CompletableFuture<N> capture(BlockPos pos, long nowNanos);
    }

    private static final Logger LOG = Logger.getLogger("tfp");

    private final String name;
    private final RegionExecutor regions;
    private final Executor workers;
    private final SnapshotSource<N> snapshots;
    private final Function<N, List<BlockChange<S>>> decide;
    private final RegionCommitQueue<S> queue;
    private final CommitBudget budget;
    private final PipelineMetrics metrics;
    private final Map<BlockPos, AtomicBoolean> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong pendingTasks = new AtomicLong();

    public AsyncDecisionPipeline(String name, RegionExecutor regions, BlockAccess<S> world, Executor workers,
            CommitBudget budget, SnapshotSource<N> snapshots, Function<N, List<BlockChange<S>>> decide) {
        this.name = name;
        this.regions = regions;
        this.workers = workers;
        this.snapshots = snapshots;
        this.decide = decide;
        this.budget = budget;
        this.metrics = new PipelineMetrics();
        this.queue = new RegionCommitQueue<>(regions, world, budget, this::submit, metrics);
    }

    /**
     * Convenience: a pipeline whose snapshot is a {@link NeighbourSnapshot} of the given radius.
     */
    public static <S> AsyncDecisionPipeline<S, NeighbourSnapshot<S>> neighbourhood(String name, RegionExecutor regions,
            BlockAccess<S> world, Executor workers, CommitBudget budget, int radius, NeighbourSnapshot.Shape shape,
            Function<NeighbourSnapshot<S>, List<BlockChange<S>>> decide) {
        PipelineMetrics[] m = new PipelineMetrics[1];
        AsyncDecisionPipeline<S, NeighbourSnapshot<S>> p = new AsyncDecisionPipeline<>(name, regions, world, workers,
                budget, (pos, now) -> NeighbourSnapshot.capture(pos, radius, shape, regions, world, now, m[0]), decide);
        m[0] = p.metrics;
        return p;
    }

    /** Owner thread of {@code pos} (re-routed and counted otherwise). Cheap. */
    public void submit(BlockPos pos) {
        metrics.submits.increment();
        if (!regions.isOwner(pos.chunkX(), pos.chunkZ())) {
            metrics.threadViolations.increment();
            hop(pos.chunkX(), pos.chunkZ(), () -> submit(pos));
            return;
        }
        AtomicBoolean rerun = new AtomicBoolean();
        AtomicBoolean existing = inFlight.putIfAbsent(pos, rerun);
        if (existing != null) {
            existing.set(true);
            metrics.coalesced.increment();
            return;
        }
        pendingTasks.incrementAndGet();
        CompletableFuture<N> snap;
        try {
            snap = snapshots.capture(pos, budget.now());
        } catch (Throwable t) {
            inFlight.remove(pos);
            pendingTasks.decrementAndGet();
            throw t;
        }
        snap.thenApplyAsync(decide, workers).whenComplete((changes, err) -> {
            try {
                inFlight.remove(pos);
                if (rerun.get()) {
                    metrics.reruns.increment();
                    hop(pos.chunkX(), pos.chunkZ(), () -> submit(pos));
                }
                if (err != null) {
                    metrics.decideErrors.increment();
                    LOG.log(Level.SEVERE, "[" + name + "] decision failed at " + pos, err);
                    return;
                }
                metrics.decisions.increment();
                if (changes.isEmpty()) {
                    metrics.noops.increment();
                    return;
                }
                Map<Long, List<BlockChange<S>>> byChunk = new HashMap<>();
                for (BlockChange<S> c : changes) {
                    byChunk.computeIfAbsent(c.pos().chunkKey(), k -> new ArrayList<>()).add(c);
                }
                for (List<BlockChange<S>> group : byChunk.values()) {
                    BlockPos p = group.getFirst().pos();
                    hop(p.chunkX(), p.chunkZ(), () -> queue.enqueue(group));
                }
            } finally {
                pendingTasks.decrementAndGet();
            }
        });
    }

    /** Runs on the chunk's owner; counted until it has run, so {@link #isIdle()} sees hops in flight. */
    private void hop(int chunkX, int chunkZ, Runnable task) {
        pendingTasks.incrementAndGet();
        regions.run(chunkX, chunkZ, () -> {
            try {
                task.run();
            } finally {
                pendingTasks.decrementAndGet();
            }
        });
    }

    /**
     * True when no decision is running and no change is queued. Racy (tasks may be in an executor's
     * queue between hops); callers poll it several times, as self-tests do.
     */
    public boolean isIdle() {
        return pendingTasks.get() == 0 && inFlight.isEmpty() && queue.isEmpty();
    }

    public PipelineMetrics metrics() {
        return metrics;
    }

    public CommitBudget budget() {
        return budget;
    }

    public String name() {
        return name;
    }
}
