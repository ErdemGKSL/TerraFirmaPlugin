/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.collapse.CollapseRandom;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-PHYS-02 on MockBukkit with a fake clock and scripted draws: mining an unsupported raw rock ceiling over a room starts a
 * real collapse (the start columns fall as cobble, the next layer follows on the propagation after the commits), a support
 * in reach prevents it, a fake collapse only shows its sound and particles, blocks outside {@code #can_trigger_collapse}
 * cost nothing, a block changed between decision and commit is left alone, the commit budget spreads the falls over ticks,
 * and the break event feeds the service in any game mode.
 */
class CollapseMockBukkitTest extends MockServerTest {
    private static final String RAW = "tfp:rock/raw/granite";
    private static final String COBBLE = "tfp:rock/cobble/granite";
    private static final String BEAM = "tfp:wood/horizontal_support/oak";
    private static final int CEILING = 100;

    /** Region tasks run on the next "tick"; delayed ones after their delay. The test thread owns every region. */
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(RAW, COBBLE, BEAM);
    private final FakeWorld fake = new FakeWorld();
    private final long[] clock = {0L};
    private SupportTable supports = new SupportTable(List.of());
    private Supplier<CollapseRandom> randoms = () -> scripted(new float[] {0f}, 0);
    private CollapseService service;
    private WorldMock world;

    private static final class Delayed {
        long ticks;
        final Runnable task;

        Delayed(long ticks, Runnable task) {
            this.ticks = ticks;
            this.task = task;
        }
    }

    private record Spawn(Block at, String state) {}

    /** Floats in order (the last repeats); every int draw returns {@code ints}. */
    private static CollapseRandom scripted(float[] floats, int ints) {
        return new CollapseRandom() {
            private int i;

            @Override
            public float nextFloat() {
                return floats[Math.min(i++, floats.length - 1)];
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(ints, bound - 1);
            }
        };
    }

    /** Raw granite (custom) collapses into cobble; stone is a sturdy full block; an oak fence stands in for a beam. */
    private final class FakeWorld implements CollapseWorld {
        final List<Spawn> spawns = new ArrayList<>();
        final List<Block> destroyed = new ArrayList<>();
        final List<CollapseSound> sounds = new ArrayList<>();
        final List<Block> fakeStarts = new ArrayList<>();
        int cellReads;

        @Override
        public boolean canTrigger(Block block) {
            return custom.stateAt(block).map(s -> s.id().equals(RAW)).orElse(false);
        }

        @Override
        public CollapseState.Cell cell(Block block) {
            cellReads++;
            var state = custom.stateAt(block);
            if (state.isPresent() && state.get().id().equals(RAW)) {
                return new CollapseState.Cell(RAW, RAW, true, true, 2, false, true, true, false, COBBLE);
            }
            if (state.isPresent() && state.get().id().equals(BEAM)) {
                return new CollapseState.Cell(BEAM, BEAM, false, false, 0, false, false, false, false, null);
            }
            return switch (block.getType()) {
                case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA -> CollapseState.Cell.AIR;
                case BEDROCK -> new CollapseState.Cell("minecraft:bedrock", "minecraft:bedrock", true, true, 4, true,
                        false, false, false, null);
                default -> new CollapseState.Cell(block.getType().getKey().toString(),
                        block.getType().getKey().toString(), true, true, 0, false, false, false, false, null);
            };
        }

        @Override
        public boolean canFall(String state) {
            return custom.exists(BlockStateRef.parse(state).id());
        }

        @Override
        public boolean spawnFalling(Block at, String state) {
            spawns.add(new Spawn(at, state));
            return true;
        }

        @Override
        public void remove(Block block) {
            custom.remove(block);
            block.setType(Material.AIR);
        }

        @Override
        public void destroy(Block block) {
            destroyed.add(block);
            remove(block);
        }

        @Override
        public void sound(Block block, CollapseSound sound) {
            sounds.add(sound);
        }

        @Override
        public void fakeStart(Block block) {
            fakeStarts.add(block);
        }
    }

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
        world = server.addSimpleWorld("collapse");
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        build(1_000_000_000L);
    }

    private void build(long budgetNanos) {
        service = new CollapseService(w -> regions, fake, new TestScheduler(), Runnable::run,
                new CommitBudget(budgetNanos, () -> clock[0] += 400_000L), () -> supports, () -> randoms.get());
    }

    /** One server tick: the clock moves a tick, delayed tasks count down, then everything queued before the tick runs. */
    private void tick() {
        clock[0] += 50_000_000L;
        Iterator<Delayed> it = delayed.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Delayed d = it.next();
            if (--d.ticks <= 0) {
                it.remove();
                due.add(d.task);
            }
        }
        int n = tasks.size();
        for (int i = 0; i < n; i++) {
            tasks.poll().run();
        }
        due.forEach(Runnable::run);
    }

    private int runAll() {
        int ticks = 0;
        for (; ticks < 500 && (!tasks.isEmpty() || !delayed.isEmpty()); ticks++) {
            tick();
        }
        assertThat(tasks).as("settled").isEmpty();
        assertThat(delayed).as("settled").isEmpty();
        assertThat(service.isIdle()).isTrue();
        return ticks;
    }

    private Block raw(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE);
        assertThat(custom.place(block, BlockStateRef.of(RAW), false)).isTrue();
        return block;
    }

    /** A raw granite ceiling {@code layers} thick at y 100.., 13 x 13, over a room of air down to a stone floor at y 95. */
    private void room(int layers) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                world.getBlockAt(x, 95, z).setType(Material.STONE);
                for (int y = 96; y < CEILING; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
                for (int l = 0; l < layers; l++) {
                    raw(x, CEILING + l, z);
                }
                for (int y = CEILING + layers; y <= CEILING + 6; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    @Test
    void miningAnUnsupportedCeilingCollapsesItAsFallingCobble() {
        room(2);
        service.trigger(world.getBlockAt(0, CEILING, 0));
        runAll();

        // start at (-2, 100, -2) (the first unsupported start in x, y, z order), radius 3: the 25 columns closer than 3 fall;
        // then the layer above, closer than 3 again, follows: 21 more
        assertThat(service.realCollapses()).isEqualTo(1);
        assertThat(fake.spawns).hasSize(46);
        assertThat(fake.spawns).allMatch(s -> s.state().equals(COBBLE));
        assertThat(fake.spawns.stream().filter(s -> s.at().getY() == CEILING)).hasSize(25);
        assertThat(fake.spawns.stream().filter(s -> s.at().getY() == CEILING + 1)).hasSize(21);
        assertThat(fake.spawns).anyMatch(s -> s.at().getX() == -2 && s.at().getZ() == -2 && s.at().getY() == CEILING);
        assertThat(world.getBlockAt(-2, CEILING, -2).getType()).isEqualTo(Material.AIR);
        assertThat(custom.stateAt(world.getBlockAt(6, CEILING, 6))).as("outside the radius").isPresent();
        assertThat(fake.destroyed).as("air below: nothing to break").isEmpty();
        assertThat(fake.sounds).containsExactly(CollapseWorld.CollapseSound.LONG, CollapseWorld.CollapseSound.SHORT);
        assertThat(service.falls(world)).isEqualTo(46);
        assertThat(service.propagations()).isEqualTo(2);
    }

    @Test
    void aSupportInReachPreventsTheCollapse() {
        room(1);
        Block beam = world.getBlockAt(0, 99, 0);
        beam.setType(Material.OAK_FENCE);
        assertThat(custom.place(beam, BlockStateRef.of(BEAM), false)).isTrue();
        supports = new SupportTable(List.of(new SupportTable.Support(Set.of(BEAM), 2, 2, 4)));
        service.trigger(world.getBlockAt(0, CEILING, 0));
        runAll();

        assertThat(service.triggers()).isEqualTo(1);
        assertThat(service.realCollapses()).isZero();
        assertThat(fake.spawns).isEmpty();
        assertThat(fake.sounds).isEmpty();
    }

    @Test
    void aFakeCollapseOnlyShowsItself() {
        room(1);
        randoms = () -> scripted(new float[] {0.5f, 0f}, 0);
        service.trigger(world.getBlockAt(0, CEILING, 0));
        runAll();

        assertThat(service.fakeCollapses()).isEqualTo(1);
        assertThat(fake.spawns).isEmpty();
        assertThat(fake.sounds).containsExactly(CollapseWorld.CollapseSound.LONG_FAKE);
        assertThat(fake.fakeStarts).as("3 + nextInt(n - 3) with a zero draw").hasSize(3);
        assertThat(fake.fakeStarts).allMatch(b -> b.getY() == CEILING);
    }

    @Test
    void noCollapseWhenBothChecksFail() {
        room(1);
        randoms = () -> scripted(new float[] {0.5f, 0.5f}, 0);
        service.trigger(world.getBlockAt(0, CEILING, 0));
        runAll();
        assertThat(fake.spawns).isEmpty();
        assertThat(fake.sounds).isEmpty();
        assertThat(fake.fakeStarts).isEmpty();
    }

    @Test
    void blocksThatCannotTriggerCostNothing() {
        room(1);
        Block stone = world.getBlockAt(0, 95, 0);
        service.trigger(stone);
        service.trigger(world.getBlockAt(0, 97, 0));
        assertThat(fake.cellReads).isZero();
        assertThat(service.triggers()).isZero();
        assertThat(tasks).isEmpty();
    }

    @Test
    void aBlockChangedBeforeTheCommitIsLeftAlone() {
        room(1);
        service.trigger(world.getBlockAt(0, CEILING, 0));
        // decided and enqueued; the drain is a region task that has not run yet
        assertThat(fake.spawns).isEmpty();
        custom.remove(world.getBlockAt(-2, CEILING, -2));
        world.getBlockAt(-2, CEILING, -2).setType(Material.DIRT);
        runAll();

        assertThat(fake.spawns).hasSize(24);
        assertThat(world.getBlockAt(-2, CEILING, -2).getType()).isEqualTo(Material.DIRT);
        assertThat(service.metrics(world).stale.sum()).isEqualTo(1);
    }

    @Test
    void theCommitBudgetSpreadsTheFallsOverTicks() {
        build(1_000_000L);
        room(1);
        service.trigger(world.getBlockAt(0, CEILING, 0));
        int ticks = runAll();

        assertThat(fake.spawns).hasSize(25);
        assertThat(service.metrics(world).budgetDeferrals.sum()).isPositive();
        assertThat(ticks).isGreaterThan(5);
    }

    @Test
    void anUnloadedAreaTriggersNothing() {
        room(1);
        world.unloadChunk(2, 0);
        service.trigger(world.getBlockAt(0, CEILING, 0));
        runAll();
        assertThat(service.triggers()).isZero();
        assertThat(fake.spawns).isEmpty();
    }

    @Test
    void theBreakEventFeedsTheServiceInAnyGameMode() {
        room(1);
        server.getPluginManager().registerEvents(new CollapseListener(service), plugin);
        PlayerMock player = server.addPlayer();
        for (GameMode mode : List.of(GameMode.SURVIVAL, GameMode.CREATIVE)) {
            player.setGameMode(mode);
            player.simulateBlockBreak(raw(6, CEILING + 3, 6));
        }
        assertThat(service.triggers()).isEqualTo(2);
    }

    @Test
    void theSnapshotBoxesCoverWhatTheRulesRead() {
        BlockPos mined = new BlockPos(0, 64, 0);
        assertThat(CollapseService.triggerBox(mined, new SupportTable(List.of()))).hasSize(9 * 9 * 6)
                .contains(new BlockPos(4, 61, 4), new BlockPos(-4, 66, -4));
        assertThat(CollapseService.triggerBox(mined,
                new SupportTable(List.of(new SupportTable.Support(Set.of(BEAM), 2, 2, 4))))).hasSize(17 * 17 * 9);
        assertThat(CollapseService.startBox(mined, 3)).hasSize(25 * 9)
                .doesNotContain(new BlockPos(3, 64, 0));
        assertThat(CollapseService.startBox(mined, 18)).hasSizeLessThan(37 * 37 * 9);
    }

    private final class TestScheduler implements Scheduler {
        @Override
        public void region(Location location, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void region(World w, int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public Scheduler.TaskHandle regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            Delayed d = new Delayed(Math.max(1, delayTicks), task);
            delayed.add(d);
            return new Scheduler.TaskHandle() {
                @Override
                public void cancel() {
                    delayed.remove(d);
                }

                @Override
                public boolean isCancelled() {
                    return !delayed.contains(d);
                }
            };
        }

        @Override
        public Scheduler.TaskHandle regionRepeating(World w, int chunkX, int chunkZ, long initialDelayTicks,
                long periodTicks, Runnable task) {
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
        public Scheduler.TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks,
                Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scheduler.TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
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
        public void shutdown() {
        }
    }
}
