/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.neighbour.ShapeRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.CommitBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-10: the runner on two fake regions (split at chunkX = 0) and a worker pool. The rule is a TFC support
 * beam's horizontal connections ({@code VerticalSupportBlock.updateShape}: one boolean per horizontal side, true when
 * that neighbour is a beam); placement writes the provisional "unconnected" state.
 */
class NeighbourRunnerTest {
    static final String BEAM = "tfp:test/beam";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");
    static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    final Map<BlockPos, BlockStateRef> blocks = new ConcurrentHashMap<>();
    final AtomicInteger writes = new AtomicInteger();
    final Set<String> decideThreads = ConcurrentHashMap.newKeySet();
    final List<String> wrongThreadWrites = java.util.Collections.synchronizedList(new ArrayList<>());
    final FakeRegions regions = new FakeRegions(2);
    final ExecutorService workers = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "tfp-worker-test");
        t.setDaemon(true);
        return t;
    });
    /** Fake time cost of one write (nanos added to the fake clock). */
    volatile long writeCost;
    final AtomicLong fakeNow = new AtomicLong();

    final BlockAccess<BlockStateRef> world = new BlockAccess<>() {
        @Override
        public BlockStateRef read(BlockPos pos) {
            return blocks.getOrDefault(pos, AIR);
        }

        @Override
        public void write(BlockPos pos, BlockStateRef state) {
            if (!regions.isOwner(pos.chunkX(), pos.chunkZ())) {
                wrongThreadWrites.add(pos + " on " + Thread.currentThread().getName());
            }
            fakeNow.addAndGet(writeCost);
            writes.incrementAndGet();
            blocks.put(pos, state);
        }
    };

    final ShapeRule<BlockStateRef> beam = new ShapeRule<>() {
        @Override
        public String id() {
            return BEAM;
        }

        @Override
        public Set<String> blockIds() {
            return Set.of(BEAM);
        }

        @Override
        public boolean appliesTo(BlockStateRef state) {
            return state.id().equals(BEAM);
        }

        @Override
        public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
            decideThreads.add(Thread.currentThread().getName());
            BlockStateRef s = n.center();
            for (Direction d : HORIZONTAL) {
                BlockStateRef o = n.at(d);
                if (o == null) {
                    return null;
                }
                s = s.with(d.name().toLowerCase(java.util.Locale.ROOT), Boolean.toString(appliesTo(o)));
            }
            return s;
        }

        @Override
        public BlockStateRef provisional(BlockStateRef placed) {
            BlockStateRef s = placed;
            for (Direction d : HORIZONTAL) {
                s = s.with(d.name().toLowerCase(java.util.Locale.ROOT), "false");
            }
            return s;
        }
    };

    @AfterEach
    void stop() {
        regions.close();
        workers.shutdownNow();
    }

    NeighbourRunner<BlockStateRef> runner(CommitBudget budget) {
        ShapeRules<BlockStateRef> rules = ShapeRules.<BlockStateRef>empty().with(beam);
        return new NeighbourRunner<>("test", regions, world, workers, budget, () -> rules);
    }

    /**
     * Waits until the runner is idle; while waiting, the fake clock moves one tick window every 5 ms. It moves on the
     * east region's thread, between its tasks, so a drain there never sees the clock jump in the middle of a change.
     */
    void settle(NeighbourRunner<?> r) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        int stable = 0;
        while (stable < 10 && System.currentTimeMillis() < deadline) {
            stable = r.isIdle() ? stable + 1 : 0;
            regions.await(0, () -> fakeNow.addAndGet(50_000_000L));
            Thread.sleep(5);
        }
        assertThat(r.isIdle()).as("runner settles: " + r).isTrue();
    }

    boolean connected(BlockPos p, Direction d) {
        return Boolean.parseBoolean(blocks.get(p).properties().get(d.name().toLowerCase(java.util.Locale.ROOT)));
    }

    void assertRowConnected(List<BlockPos> row) {
        for (int i = 0; i < row.size(); i++) {
            BlockPos p = row.get(i);
            assertThat(connected(p, Direction.WEST)).as("west of " + p).isEqualTo(i > 0);
            assertThat(connected(p, Direction.EAST)).as("east of " + p).isEqualTo(i < row.size() - 1);
            assertThat(connected(p, Direction.NORTH)).isFalse();
            assertThat(connected(p, Direction.SOUTH)).isFalse();
        }
    }

    static List<BlockPos> row(int x0, int n) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new BlockPos(x0 + i, 64, 5));
        }
        return out;
    }

    @Test
    void chainOfTenPlacementsConvergesWithOneCommitPerChangedBlock() throws Exception {
        writeCost = 300_000; // fake clock: each write costs 0.3 ms of the 1 ms budget
        CommitBudget budget = new CommitBudget(1_000_000, fakeNow::get);
        NeighbourRunner<BlockStateRef> r = runner(budget);
        List<BlockPos> row = row(2, 10); // all in chunk 0, one region
        regions.await(0, () -> row.forEach(p -> r.placeProvisional(p, BlockStateRef.of(BEAM))));
        assertThat(writes.get()).as("provisional writes at once").isEqualTo(10);
        assertThat(blocks.get(row.get(3))).isEqualTo(beam.provisional(BlockStateRef.of(BEAM)));
        settle(r);

        assertRowConnected(row);
        assertThat(r.metrics().applied.sum()).as("one commit per changed block").isEqualTo(10);
        assertThat(writes.get()).isEqualTo(20);
        assertThat(r.provisionals()).isEqualTo(10);
        // 10 x (centre + 6 faces) = 70 requests for 52 distinct positions in the placing tick
        assertThat(r.tickCoalesced()).as("duplicate requests in one tick coalesce").isGreaterThanOrEqualTo(18);
        assertThat(r.metrics().budgetDeferrals.sum()).as("the 1 ms budget spread the commits").isPositive();
        assertThat(budget.maxWindowNanos()).as("at most one change over the budget").isLessThanOrEqualTo(1_300_000);
        assertThat(r.metrics().threadViolations.sum()).isZero();
        assertThat(wrongThreadWrites).isEmpty();
    }

    @Test
    void decisionsRunOffThreadAndCommitsOnTheOwnerRegion() throws Exception {
        NeighbourRunner<BlockStateRef> r = runner(new CommitBudget(1_000_000, System::nanoTime));
        List<BlockPos> row = row(-5, 10); // x -5..4 straddles the region border at x = 0
        List<BlockPos> west = row.stream().filter(p -> p.chunkX() < 0).toList();
        List<BlockPos> east = row.stream().filter(p -> p.chunkX() >= 0).toList();
        regions.await(-1, () -> west.forEach(p -> r.placeProvisional(p, BlockStateRef.of(BEAM))));
        regions.await(0, () -> east.forEach(p -> r.placeProvisional(p, BlockStateRef.of(BEAM))));
        settle(r);

        assertRowConnected(row);
        assertThat(decideThreads).as("decide ran only on the worker pool").containsOnly("tfp-worker-test");
        assertThat(wrongThreadWrites).as("every write on the owner region of its chunk").isEmpty();
        assertThat(r.metrics().threadViolations.sum()).isZero();
        assertThat(r.metrics().remoteCaptures.sum()).as("border snapshots read the other region").isPositive();
    }

    @Test
    void requestsForOnePositionInOneTickAreOneDecision() throws Exception {
        NeighbourRunner<BlockStateRef> r = runner(new CommitBudget(1_000_000, System::nanoTime));
        BlockPos p = new BlockPos(3, 64, 3);
        blocks.put(p, beam.provisional(BlockStateRef.of(BEAM)));
        regions.await(0, () -> {
            for (int i = 0; i < 50; i++) {
                r.request(p);
            }
        });
        settle(r);
        assertThat(r.tickCoalesced()).isEqualTo(49);
        assertThat(r.metrics().decisions.sum()).isEqualTo(1);
        assertThat(r.metrics().applied.sum()).as("already in its decided shape").isZero();
    }

    @Test
    void removingANeighbourCascadesThroughPhysicsRequests() throws Exception {
        NeighbourRunner<BlockStateRef> r = runner(new CommitBudget(1_000_000, System::nanoTime));
        List<BlockPos> row = row(2, 3);
        regions.await(0, () -> row.forEach(p -> r.placeProvisional(p, BlockStateRef.of(BEAM))));
        settle(r);
        long applied = r.metrics().applied.sum();
        regions.await(0, () -> {
            blocks.put(row.get(1), AIR); // broken by someone else; the physics event notifies the neighbours
            r.requestAround(row.get(1));
        });
        settle(r);
        assertThat(connected(row.get(0), Direction.EAST)).isFalse();
        assertThat(connected(row.get(2), Direction.WEST)).isFalse();
        assertThat(r.metrics().applied.sum() - applied).isEqualTo(2);
    }

    @Test
    void unknownNeighbourKeepsTheProvisionalState() throws Exception {
        NeighbourRunner<BlockStateRef> r = new NeighbourRunner<>("test", regions, new BlockAccess<>() {
            @Override
            public BlockStateRef read(BlockPos pos) {
                return pos.chunkX() < 0 ? null : world.read(pos); // the west chunk "is not loaded"
            }

            @Override
            public void write(BlockPos pos, BlockStateRef state) {
                world.write(pos, state);
            }
        }, workers, new CommitBudget(1_000_000, System::nanoTime), () -> ShapeRules.<BlockStateRef>empty().with(beam));
        BlockPos edge = new BlockPos(0, 64, 5);
        regions.await(0, () -> r.placeProvisional(edge, BlockStateRef.of(BEAM)));
        settle(r);
        assertThat(blocks.get(edge)).isEqualTo(beam.provisional(BlockStateRef.of(BEAM)));
        assertThat(r.metrics().applied.sum()).isZero();
    }
}
