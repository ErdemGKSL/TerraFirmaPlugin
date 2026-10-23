/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.blockdata.InMemoryChunkDataStore;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.testing.MockServerTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-11: one chunk with 10 240 indexed lazy blocks loads. With a fake clock where every snapshot read and every
 * write costs 10 µs, no tick spends more than the feed budget (+ one block) capturing or more than the commit budget
 * (+ one change) writing, and every block is caught up exactly once within a bounded number of ticks.
 */
class CatchUpBudgetMockBukkitTest extends MockServerTest {
    static final String KIND = "tfp:test/heavy";
    static final long COST = 10_000L; // 10 µs per read or write
    static final int LAYERS = 40; // 16 × 16 × 40 = 10 240 blocks

    @Test
    void budgetHeldWithTenThousandIndexedBlocks() {
        ManualRegions regions = new ManualRegions();
        InMemoryChunkDataStore store = new InMemoryChunkDataStore();
        AtomicLong now = new AtomicLong(5_000);
        CatchUpSettings settings = new CatchUpSettings(600, 4, 1_000_000L, 1_000_000L);
        CatchUpService service = new CatchUpService(new CatchUpKinds(), store, w -> regions, Runnable::run, now::get,
                regions.clock::get, settings, Logger.getLogger("tfp-test"));
        Map<BlockPos, Integer> states = new ConcurrentHashMap<>();
        Map<Integer, int[]> perTick = new ConcurrentHashMap<>(); // tick -> {snapshot reads, writes}
        service.kinds().register(KIND, (w, pos) -> {
            regions.clock.addAndGet(COST);
            perTick.computeIfAbsent(regions.ticks, t -> new int[2])[0]++;
            return states.getOrDefault(pos, 0);
        }, (w, pos, s) -> {
            regions.clock.addAndGet(COST);
            perTick.computeIfAbsent(regions.ticks, t -> new int[2])[1]++;
            states.put(pos, s);
        }, (s, t) -> (int) (t / 1000));

        World world = server.addSimpleWorld("heavy");
        Chunk chunk = world.getChunkAt(3, -2);
        Map<BlockPos, String> entries = new LinkedHashMap<>();
        for (int y = 0; y < LAYERS; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    entries.put(new BlockPos(48 + x, y, -32 + z), KIND);
                }
            }
        }
        store.save(chunk, CatchUpService.INDEX_KEY, LazyBlockIndex.of(3, -2, entries));
        int total = entries.size();
        assertThat(total).isEqualTo(10_240);

        service.chunkLoaded(chunk);
        int ticks = regions.settle(1_000);

        assertThat(states).hasSize(total);
        assertThat(states.values()).containsOnly(5);
        AsyncDecisionPipeline<?, ?> p = service.pipeline(world, KIND).orElseThrow();
        assertThat(p.metrics().submits.sum()).isEqualTo(total);
        assertThat(p.metrics().applied.sum()).isEqualTo(total);

        // per tick: the snapshot reads are capped by the feed budget, the writes by the commit budget (the commit
        // queue re-reads each block before writing, which it charges to the commit budget)
        long perWindow = settings.feedBudgetNanos() / COST;
        perTick.forEach((tick, c) -> {
            assertThat(c[1]).as("writes in tick %d", tick).isLessThanOrEqualTo((int) (settings.commitBudgetNanos()
                    / (2 * COST)) + 1);
            assertThat(c[0] - c[1]).as("snapshot reads in tick %d", tick).isLessThanOrEqualTo((int) perWindow + 1);
        });
        assertThat(service.feedBudget().maxWindowNanos()).isLessThanOrEqualTo(settings.feedBudgetNanos() + COST);
        assertThat(service.commitBudget().maxWindowNanos())
                .isLessThanOrEqualTo(settings.commitBudgetNanos() + 2 * COST);
        // 10 240 snapshots at ~100 per tick and 10 240 commits at ~50 per tick: bounded, not one giant tick
        assertThat(ticks).isBetween(total / 60, total / 40);
        assertThat(service.stats().feedDeferrals()).isPositive();
        assertThat(service.isIdle()).isTrue();
    }
}
