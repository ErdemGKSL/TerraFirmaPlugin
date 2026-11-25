/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.landslide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PAR-PHYS-04: support beams hold landslide blocks up (TFC {@code Support.isSupported} in {@code getLandslidePos}). A floating
 * dirt block would fall; a horizontal beam within 4 blocks sideways, 2 below or 2 above keeps it. The snapshot reads the
 * beam reach for kinds only, and only when the support table has supports.
 */
class LandslideBeamSupportTest extends MockServerTest {
    private static final String DIRT = "tfp:dirt/loam";
    private static final String BEAM = "tfp:wood/horizontal_support/oak";
    private static final String POLE = "tfp:wood/vertical_support/oak";
    private static final SupportTable BEAMS = new SupportTable(List.of(new SupportTable.Support(Set.of(BEAM), 2, 2, 4)));

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(DIRT, BEAM, POLE);
    private final List<Block> falls = new ArrayList<>();
    private final AtomicReference<SupportTable> table = new AtomicReference<>(BEAMS);
    private int fullReads;
    private int kindReads;
    private LandslideService service;
    private World world;

    /** Dirt is a full custom block that slides as itself; beams are not sturdy; everything vanilla here is air. */
    private final LandslideWorld fake = new LandslideWorld() {
        @Override
        public boolean canLandslide(Block block) {
            return custom.stateAt(block).map(s -> s.id().equals(DIRT)).orElse(false);
        }

        @Override
        public SlideState.Cell cell(Block block) {
            fullReads++;
            var state = custom.stateAt(block);
            if (state.isPresent()) {
                boolean dirt = state.get().id().equals(DIRT);
                return new SlideState.Cell(state.get().id(), dirt ? EnumSet.allOf(Direction.class) : Set.of(), 0, false, false,
                        dirt ? DIRT : null);
            }
            return SlideState.Cell.AIR;
        }

        @Override
        public String kind(Block block) {
            kindReads++;
            return custom.stateAt(block).map(BlockStateRef::id).orElse(SlideState.Cell.AIR.kind());
        }

        @Override
        public boolean canFall(String state) {
            return true;
        }

        @Override
        public boolean spawnFalling(Block at, String state) {
            falls.add(at);
            return true;
        }

        @Override
        public void remove(Block block) {
            custom.remove(block);
        }

        @Override
        public void destroy(Block block) {
            remove(block);
        }

        @Override
        public void slideSound(Block block) {}
    };

    private final RegionExecutor regions = new RegionExecutor() {
        @Override
        public void run(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void runNextTick(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public boolean isOwner(int chunkX, int chunkZ) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        service = new LandslideService(w -> regions, fake, new Ticks(), Runnable::run,
                new CommitBudget(1_000_000_000L, System::nanoTime), table::get, bound -> 0);
        world = server.addSimpleWorld("slide");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
    }

    private Block dirt() {
        Block b = world.getBlockAt(0, 100, 0);
        custom.place(b, BlockStateRef.of(DIRT), false);
        return b;
    }

    private void check(Block b) {
        service.requestPlaced(b);
        for (int i = 0; i < 1000 && !tasks.isEmpty(); i++) {
            tasks.poll().run();
        }
        assertThat(service.isIdle()).isTrue();
    }

    @ParameterizedTest(name = "beam at {0},{1},{2} holds: {3}")
    @CsvSource({
        "4, -2, 0, true", "-4, 2, 4, true", "0, 2, 0, true", "3, 0, -3, true",
        "5, 0, 0, false", "0, 3, 0, false", "0, -3, 0, false", "4, -2, 5, false"})
    void aHorizontalBeamInReachHoldsTheBlock(int dx, int dy, int dz, boolean holds) {
        Block d = dirt();
        custom.place(d.getRelative(dx, dy, dz), BlockStateRef.of(BEAM), false);
        check(d);
        assertThat(falls.isEmpty()).isEqualTo(holds);
        assertThat(custom.stateAt(d).isPresent()).isEqualTo(holds);
    }

    @Test
    void aVerticalBeamHoldsNothing() {
        Block d = dirt();
        custom.place(d.getRelative(2, 0, 0), BlockStateRef.of(POLE), false);
        check(d);
        assertThat(falls).containsExactly(d);
    }

    @Test
    void onlyTheReachIsReadAndOnlyForKinds() {
        Block d = dirt();
        check(d);
        assertThat(fullReads).as("the 3x3x3 cube and the commit's re-read of the block").isEqualTo(28);
        assertThat(kindReads).as("9 x 5 x 9 reach without the cube").isEqualTo(9 * 5 * 9 - 27);
        assertThat(falls).containsExactly(d);
    }

    @Test
    void anEmptyTableReadsTheCubeOnly() {
        table.set(new SupportTable(List.of()));
        Block d = dirt();
        custom.place(d.getRelative(4, -2, 0), BlockStateRef.of(BEAM), false);
        check(d);
        assertThat(fullReads).isEqualTo(28);
        assertThat(kindReads).isZero();
        assertThat(falls).containsExactly(d);
    }

    /** Region and delayed tasks join the queue; CPU work runs inline. */
    private final class Ticks implements Scheduler {
        @Override
        public void region(Location location, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void region(World w, int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public TaskHandle regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            tasks.add(task);
            return null;
        }

        @Override
        public TaskHandle regionRepeating(World w, int cx, int cz, long i, long p, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity e, long i, long p, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalRepeating(long i, long p, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Executor ioExecutor() {
            return Runnable::run;
        }

        @Override
        public boolean isOwner(World w, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {}
    }
}
