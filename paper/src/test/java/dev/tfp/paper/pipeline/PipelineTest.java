/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The M0-ASYNC-01 toy rebuilt on the production classes: a "connected" block's state is the number of
 * connected neighbours (1..7 = toy with n-1 neighbours, 0 = air, 100 = stone).
 */
class PipelineTest extends PipelineTestBase {
    /** Pure decision: every toy block in {center + faces} gets colour = 1 + toy neighbours. */
    List<BlockChange<Integer>> decide(NeighbourSnapshot<Integer> s) {
        if (decideSleepMillis > 0) {
            try {
                Thread.sleep(decideSleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<BlockChange<Integer>> out = new ArrayList<>();
        List<BlockPos> eval = new ArrayList<>(List.of(s.center()));
        for (Direction d : Direction.values()) {
            eval.add(s.center().offset(d));
        }
        for (BlockPos p : eval) {
            Integer cur = s.at(p);
            if (!toy(cur)) {
                continue;
            }
            int n = 0;
            boolean unknown = false;
            for (Direction d : Direction.values()) {
                Integer o = s.at(p.offset(d));
                if (o == null) {
                    unknown = true;
                } else if (toy(o)) {
                    n++;
                }
            }
            if (!unknown && cur != n + 1) {
                out.add(new BlockChange<>(p, cur, n + 1, s.capturedNanos()));
            }
        }
        return out;
    }

    AsyncDecisionPipeline<Integer, NeighbourSnapshot<Integer>> pipeline(long budgetNanos) {
        return AsyncDecisionPipeline.neighbourhood("toy", regions, world, workers,
                new CommitBudget(budgetNanos, System::nanoTime), 2, NeighbourSnapshot.Shape.MANHATTAN, this::decide);
    }

    void awaitIdle(AsyncDecisionPipeline<?, ?> p) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        int stable = 0;
        while (stable < 5 && System.currentTimeMillis() < deadline) {
            stable = p.isIdle() ? stable + 1 : 0;
            Thread.sleep(10);
        }
        assertThat(p.isIdle()).as("pipeline settles").isTrue();
    }

    int wrongColours() {
        int wrong = 0;
        for (var e : blocks.entrySet()) {
            if (!toy(e.getValue())) {
                continue;
            }
            int n = 0;
            for (Direction d : Direction.values()) {
                if (toy(blocks.get(e.getKey().offset(d)))) {
                    n++;
                }
            }
            if (e.getValue() != n + 1) {
                wrong++;
            }
        }
        return wrong;
    }

    /** Places toy blocks on their owner threads, as a place event would, and submits each. */
    void place(AsyncDecisionPipeline<Integer, ?> p, List<BlockPos> positions) throws Exception {
        for (BlockPos pos : positions) {
            regions.await(pos.chunkX(), () -> {
                blocks.put(pos, 1);
                p.submit(pos);
            });
        }
    }

    @Test
    void connectedBlocksConvergeAcrossRegionBorder() throws Exception {
        var p = pipeline(1_500_000);
        Random r = new Random(1);
        List<BlockPos> ps = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            ps.add(new BlockPos(-12 + r.nextInt(24), 64 + r.nextInt(4), -12 + r.nextInt(24))); // straddles x=0
        }
        place(p, ps);
        awaitIdle(p);
        assertThat(wrongColours()).isZero();
        assertThat(p.metrics().threadViolations.sum()).isZero();
        assertThat(p.metrics().remoteCaptures.sum()).as("border captures read the other region").isPositive();
        assertThat(p.metrics().applied.sum()).isPositive();
    }

    @Test
    void worldChangingBetweenDecideAndApplyIsCorrected() throws Exception {
        var p = pipeline(1_500_000);
        decideSleepMillis = 20;
        Random r = new Random(2);
        List<BlockPos> ps = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            ps.add(new BlockPos(-5 + r.nextInt(10), 64 + r.nextInt(2), -5 + r.nextInt(10)));
        }
        place(p, ps);
        // behind the pipeline's back: flip every third block while decisions sleep, then trigger like an event
        for (BlockPos pos : ps) {
            if (Math.floorMod(pos.x() + pos.z(), 3) == 0) {
                regions.await(pos.chunkX(), () -> {
                    blocks.put(pos, toy(blocks.get(pos)) ? STONE : 1);
                    p.submit(pos);
                    for (Direction d : Direction.values()) {
                        BlockPos n = pos.offset(d);
                        if (regions.isOwner(n.chunkX(), n.chunkZ())) {
                            p.submit(n);
                        } else {
                            regions.run(n.chunkX(), n.chunkZ(), () -> p.submit(n));
                        }
                    }
                });
            }
        }
        awaitIdle(p);
        assertThat(wrongColours()).isZero();
        assertThat(p.metrics().stale.sum() + p.metrics().reruns.sum()).as("the race was exercised").isPositive();
    }

    @Test
    void submitsWhileInFlightCoalesceButRerun() throws Exception {
        var p = pipeline(1_500_000);
        decideSleepMillis = 30;
        BlockPos pos = new BlockPos(3, 64, 3);
        regions.await(0, () -> {
            blocks.put(pos, 1);
            for (int i = 0; i < 100; i++) {
                p.submit(pos);
            }
        });
        awaitIdle(p);
        assertThat(p.metrics().coalesced.sum()).isEqualTo(99);
        assertThat(p.metrics().decisions.sum()).isEqualTo(2); // the first job + exactly one re-run
        assertThat(blocks.get(pos)).isEqualTo(1);
    }

}
