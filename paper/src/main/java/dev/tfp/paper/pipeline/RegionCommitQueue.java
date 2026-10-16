/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The "apply sync" half: per-chunk queues of decided {@link BlockChange}s, drained on the chunk's owner
 * thread under a {@link CommitBudget}. Never decides anything itself.
 *
 * <ul>
 *   <li>A change whose {@code expectedOld} no longer matches the live block is <b>stale</b>: it is dropped
 *       and its position handed to {@code onStale} (the pipeline re-decides it asynchronously).</li>
 *   <li>Two decisions for the same position: the one from the newer snapshot wins, both while queued and
 *       against a decision applied earlier ({@code superseded}).</li>
 *   <li>Work left when the budget runs out continues next tick.</li>
 * </ul>
 * All per-chunk state is only touched from the owner thread of that chunk (enforced: violations are
 * counted and the call is re-routed to the owner).
 */
public final class RegionCommitQueue<S> {
    /** How long the snapshot time of an applied change is remembered for newest-wins checks. */
    static final long APPLIED_RETENTION_NANOS = 30_000_000_000L;

    private final RegionExecutor regions;
    private final BlockAccess<S> world;
    private final CommitBudget budget;
    private final Consumer<BlockPos> onStale;
    private final PipelineMetrics metrics;
    private final Map<Long, ChunkQueue<S>> chunks = new ConcurrentHashMap<>();

    public RegionCommitQueue(RegionExecutor regions, BlockAccess<S> world, CommitBudget budget,
            Consumer<BlockPos> onStale, PipelineMetrics metrics) {
        this.regions = regions;
        this.world = world;
        this.budget = budget;
        this.onStale = onStale;
        this.metrics = metrics;
    }

    /** Owner thread of the chunk all {@code changes} belong to. */
    public void enqueue(List<BlockChange<S>> changes) {
        if (changes.isEmpty()) {
            return;
        }
        BlockPos first = changes.getFirst().pos();
        int cx = first.chunkX();
        int cz = first.chunkZ();
        if (!regions.isOwner(cx, cz)) {
            metrics.threadViolations.increment();
            regions.run(cx, cz, () -> enqueue(changes));
            return;
        }
        long key = ChunkPos.key(cx, cz);
        ChunkQueue<S> q = chunks.computeIfAbsent(key, k -> new ChunkQueue<>());
        for (BlockChange<S> c : changes) {
            if (c.pos().chunkKey() != key) {
                throw new IllegalArgumentException("changes must belong to one chunk: " + c.pos() + " vs " + first);
            }
            BlockChange<S> queued = q.pending.get(c.pos());
            if (queued != null) {
                metrics.queueCoalesced.increment();
                if (queued.snapshotNanos() > c.snapshotNanos()) {
                    continue;
                }
            }
            q.pending.put(c.pos(), c);
            metrics.enqueued.increment();
        }
        if (!q.drainScheduled) {
            q.drainScheduled = true;
            regions.run(cx, cz, () -> drain(key, cx, cz));
        }
    }

    private void drain(long key, int cx, int cz) {
        ChunkQueue<S> q = chunks.get(key);
        if (q == null) {
            return;
        }
        q.drainScheduled = false;
        Iterator<BlockChange<S>> it = q.pending.values().iterator();
        while (it.hasNext()) {
            if (!budget.tryStart()) {
                metrics.budgetDeferrals.increment();
                q.drainScheduled = true;
                regions.runNextTick(cx, cz, () -> drain(key, cx, cz));
                return;
            }
            long start = budget.now();
            BlockChange<S> c = it.next();
            it.remove();
            apply(q, c);
            budget.charge(budget.now() - start);
        }
        prune(q);
        if (q.pending.isEmpty() && q.applied.isEmpty()) {
            chunks.remove(key, q);
        }
    }

    private void apply(ChunkQueue<S> q, BlockChange<S> c) {
        long[] last = q.applied.get(c.pos());
        if (last != null && last[0] > c.snapshotNanos()) {
            metrics.superseded.increment();
            return;
        }
        S live = world.read(c.pos());
        if (live == null) {
            metrics.unloaded.increment(); // chunk unloaded meanwhile: its load path re-evaluates
            return;
        }
        if (!Objects.equals(live, c.expectedOld())) {
            metrics.stale.increment();
            onStale.accept(c.pos());
            return;
        }
        world.write(c.pos(), c.next());
        long now = budget.now();
        q.applied.put(c.pos(), new long[] {c.snapshotNanos(), now});
        metrics.applied.increment();
        metrics.latency(now - c.snapshotNanos());
    }

    private void prune(ChunkQueue<S> q) {
        long cutoff = budget.now() - APPLIED_RETENTION_NANOS;
        q.applied.values().removeIf(v -> v[1] < cutoff);
    }

    /** True if nothing is queued anywhere (for tests and self-tests; racy by nature). */
    public boolean isEmpty() {
        return chunks.values().stream().allMatch(q -> q.pending.isEmpty());
    }

    private static final class ChunkQueue<S> {
        final LinkedHashMap<BlockPos, BlockChange<S>> pending = new LinkedHashMap<>();
        /** pos -> {snapshotNanos, appliedAtNanos} */
        final Map<BlockPos, long[]> applied = new HashMap<>();
        boolean drainScheduled;
    }
}
