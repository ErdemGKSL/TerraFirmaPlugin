/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * The budgeted region-thread half of the catch-up trigger (PAR-INFRA-11) for one world: per-chunk queues of indexed
 * blocks waiting to be snapshotted. A chunk with 10 000 lazy blocks must not capture 10 000 snapshots in one tick, so
 * each drain hands blocks to the {@link Sink} only while the {@link CommitBudget} of this thread's tick window lasts;
 * the rest continue next tick. A block queued twice before it is handed on is handed on once. A chunk that is
 * unloaded meanwhile is dropped ({@link #drop}, or found unloaded on drain) without loading it.
 *
 * <p>Per-chunk state is touched only on the chunk's owner thread (calls from another thread are re-routed there).
 */
final class CatchUpFeeder {
    /** Where a block goes once it is its turn: the kind's runner (snapshot + async decide). Owner thread. */
    @FunctionalInterface
    interface Sink {
        void accept(BlockPos pos, String kind);
    }

    /** Whether the chunk is loaded; never loads it. Owner thread. */
    @FunctionalInterface
    interface Loaded {
        boolean test(int chunkX, int chunkZ);
    }

    private final RegionExecutor regions;
    private final CommitBudget budget;
    private final Loaded loaded;
    private final Sink sink;
    private final Map<Long, Queue> chunks = new ConcurrentHashMap<>();
    final LongAdder enqueued = new LongAdder();
    final LongAdder handed = new LongAdder();
    final LongAdder deferrals = new LongAdder();
    final LongAdder dropped = new LongAdder();

    CatchUpFeeder(RegionExecutor regions, CommitBudget budget, Loaded loaded, Sink sink) {
        this.regions = regions;
        this.budget = budget;
        this.loaded = loaded;
        this.sink = sink;
    }

    /** Queues {@code blocks} (all in chunk {@code chunkX}, {@code chunkZ}). Owner thread. */
    void enqueue(int chunkX, int chunkZ, Map<BlockPos, String> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        if (!regions.isOwner(chunkX, chunkZ)) {
            Map<BlockPos, String> copy = Map.copyOf(blocks);
            regions.run(chunkX, chunkZ, () -> enqueue(chunkX, chunkZ, copy));
            return;
        }
        long key = ChunkPos.key(chunkX, chunkZ);
        Queue q = chunks.computeIfAbsent(key, k -> new Queue());
        for (Map.Entry<BlockPos, String> e : blocks.entrySet()) {
            if (q.pending.putIfAbsent(e.getKey(), e.getValue()) == null) {
                enqueued.increment();
            }
        }
        if (!q.scheduled) {
            q.scheduled = true;
            regions.run(chunkX, chunkZ, () -> drain(key, chunkX, chunkZ));
        }
    }

    /** Forgets what is queued for the chunk (it unloaded). Owner thread. */
    void drop(int chunkX, int chunkZ) {
        Queue q = chunks.remove(ChunkPos.key(chunkX, chunkZ));
        if (q != null) {
            dropped.add(q.pending.size());
            q.pending.clear();
        }
    }

    private void drain(long key, int chunkX, int chunkZ) {
        Queue q = chunks.get(key);
        if (q == null) {
            return;
        }
        q.scheduled = false;
        if (!loaded.test(chunkX, chunkZ)) {
            drop(chunkX, chunkZ);
            return;
        }
        Iterator<Map.Entry<BlockPos, String>> it = q.pending.entrySet().iterator();
        while (it.hasNext()) {
            if (!budget.tryStart()) {
                deferrals.increment();
                q.scheduled = true;
                regions.runNextTick(chunkX, chunkZ, () -> drain(key, chunkX, chunkZ));
                return;
            }
            long start = budget.now();
            Map.Entry<BlockPos, String> e = it.next();
            it.remove();
            try {
                sink.accept(e.getKey(), e.getValue());
            } finally {
                handed.increment();
                budget.charge(budget.now() - start);
            }
        }
        chunks.remove(key, q);
    }

    /** True if nothing is queued (tests; racy by nature). */
    boolean isEmpty() {
        return chunks.values().stream().allMatch(q -> q.pending.isEmpty());
    }

    /** Blocks queued, all chunks (debug; racy). */
    int pending() {
        return chunks.values().stream().mapToInt(q -> q.pending.size()).sum();
    }

    private static final class Queue {
        final LinkedHashMap<BlockPos, String> pending = new LinkedHashMap<>();
        boolean scheduled;
    }
}
