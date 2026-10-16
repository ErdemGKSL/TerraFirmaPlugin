/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RegionCommitQueueTest extends PipelineTestBase {
    @Test
    void budgetSpreadsWorkOverTicks() throws Exception {
        AtomicLong fakeNow = new AtomicLong();
        // each write "costs" 0.5 ms of fake time; budget 1.5 ms -> at most 3 writes per window
        BlockAccess<Integer> slow = new BlockAccess<>() {
            @Override
            public Integer read(BlockPos p) {
                return world.read(p);
            }

            @Override
            public void write(BlockPos p, Integer s) {
                fakeNow.addAndGet(500_000);
                world.write(p, s);
            }
        };
        CommitBudget budget = new CommitBudget(1_500_000, fakeNow::get);
        PipelineMetrics m = new PipelineMetrics();
        RegionCommitQueue<Integer> q = new RegionCommitQueue<>(regions, slow, budget, pos -> { }, m);
        List<BlockChange<Integer>> changes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            blocks.put(pos, 1);
            changes.add(new BlockChange<>(pos, 1, 2, 0));
        }
        regions.await(0, () -> q.enqueue(changes));
        // without advancing the window, only 3 changes may be applied
        eventually(() -> m.applied.sum() == 3, "first window applies 3");
        Thread.sleep(30);
        assertThat(m.applied.sum()).isEqualTo(3);
        while (m.applied.sum() < 10) {
            long before = m.applied.sum();
            fakeNow.addAndGet(CommitBudget.WINDOW_NANOS); // next tick window
            eventually(() -> m.applied.sum() > before, "progress in the next window");
            assertThat(m.applied.sum() - before).isLessThanOrEqualTo(3);
        }
        assertThat(m.budgetDeferrals.sum()).isPositive();
        assertThat(budget.maxWindowNanos()).isLessThanOrEqualTo(1_500_000);
    }

    @Test
    void newerSnapshotWinsRegardlessOfArrivalOrder() throws Exception {
        PipelineMetrics m = new PipelineMetrics();
        RegionCommitQueue<Integer> q = new RegionCommitQueue<>(regions, world,
                new CommitBudget(1_500_000, System::nanoTime), pos -> { }, m);
        BlockPos pos = new BlockPos(1, 64, 1);
        blocks.put(pos, 1);
        regions.await(0, () -> {
            q.enqueue(List.of(new BlockChange<>(pos, 1, 5, 200))); // newer decision first
            q.enqueue(List.of(new BlockChange<>(pos, 1, 3, 100))); // older one arrives later: ignored while queued
        });
        eventually(() -> blocks.get(pos) == 5, "newer applied");
        regions.await(0, () -> q.enqueue(List.of(new BlockChange<>(pos, 5, 2, 150)))); // older than applied
        eventually(() -> m.superseded.sum() == 1, "older decision superseded");
        assertThat(blocks.get(pos)).isEqualTo(5);
        assertThat(m.superseded.sum()).isEqualTo(1);
        assertThat(m.queueCoalesced.sum()).isEqualTo(1);
    }

    @Test
    void staleChangeIsHandedBackNotApplied() throws Exception {
        List<BlockPos> staleSeen = new ArrayList<>();
        PipelineMetrics m = new PipelineMetrics();
        RegionCommitQueue<Integer> q = new RegionCommitQueue<>(regions, world,
                new CommitBudget(1_500_000, System::nanoTime), staleSeen::add, m);
        BlockPos pos = new BlockPos(2, 64, 2);
        blocks.put(pos, STONE);
        regions.await(0, () -> q.enqueue(List.of(new BlockChange<>(pos, 1, 4, 1))));
        eventually(() -> m.stale.sum() == 1, "stale detected");
        assertThat(blocks.get(pos)).isEqualTo(STONE);
        assertThat(staleSeen).containsExactly(pos);
        assertThat(m.stale.sum()).isEqualTo(1);
    }

    @Test
    void enqueueFromWrongThreadIsReroutedAndCounted() throws Exception {
        PipelineMetrics m = new PipelineMetrics();
        RegionCommitQueue<Integer> q = new RegionCommitQueue<>(regions, world,
                new CommitBudget(1_500_000, System::nanoTime), pos -> { }, m);
        BlockPos pos = new BlockPos(-3, 64, 0); // west region
        blocks.put(pos, 1);
        regions.await(0, () -> q.enqueue(List.of(new BlockChange<>(pos, 1, 2, 1)))); // called on east thread
        eventually(() -> blocks.get(pos) == 2, "re-routed change applied");
        assertThat(m.threadViolations.sum()).isEqualTo(1);
        assertThat(blocks.get(pos)).isEqualTo(2);
    }
}
