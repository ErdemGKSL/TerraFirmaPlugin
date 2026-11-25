/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse.explosion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.collapse.CollapseRandom;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.collapse.CollapseService;
import dev.tfp.paper.collapse.CollapseState;
import dev.tfp.paper.collapse.CollapseWorld;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-PHYS-05 on MockBukkit with a fake clock and scripted draws: an explosion that blew a raw rock ceiling lets the layer
 * above fall as cobble after the delay (within TFC's reach, one draw per blown block), nothing falls when no draw passes
 * 0.3, and nothing falls immediately.
 */
class ExplosionCollapseServiceMockBukkitTest extends MockServerTest {
    private static final String RAW = "tfp:rock/raw/granite";
    private static final String COBBLE = "tfp:rock/cobble/granite";

    private record Delayed(long[] ticks, Runnable task) {}

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(RAW, COBBLE);
    private final List<String> spawns = new ArrayList<>();
    private final List<BlockPos> spawnAt = new ArrayList<>();
    private final long[] clock = {0L};
    private Supplier<CollapseRandom> randoms = () -> scripted(0f);
    private CollapseService service;
    private WorldMock world;

    private static CollapseRandom scripted(float value) {
        return new CollapseRandom() {
            @Override
            public float nextFloat() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private final CollapseWorld fake = new CollapseWorld() {
        @Override
        public boolean canTrigger(Block block) {
            return false;
        }

        @Override
        public CollapseState.Cell cell(Block block) {
            var state = custom.stateAt(block);
            if (state.isPresent() && state.get().id().equals(RAW)) {
                return new CollapseState.Cell(RAW, RAW, true, true, 2, false, true, true, false, COBBLE);
            }
            return switch (block.getType()) {
                case AIR, CAVE_AIR, VOID_AIR -> CollapseState.Cell.AIR;
                default -> new CollapseState.Cell("minecraft:stone", "minecraft:stone", true, true, 0, false, false, false,
                        false, null);
            };
        }

        @Override
        public boolean canFall(String state) {
            return custom.exists(BlockStateRef.parse(state).id());
        }

        @Override
        public boolean spawnFalling(Block at, String state) {
            spawns.add(state);
            spawnAt.add(new BlockPos(at.getX(), at.getY(), at.getZ()));
            return true;
        }

        @Override
        public void remove(Block block) {
            custom.remove(block);
            block.setType(Material.AIR);
        }

        @Override
        public void destroy(Block block) {
            remove(block);
        }

        @Override
        public void sound(Block block, CollapseSound sound) {}

        @Override
        public void fakeStart(Block block) {}
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

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "regionDelayed" -> {
                        Delayed d = new Delayed(new long[] {Math.max(1, (Long) args[3])}, (Runnable) args[4]);
                        delayed.add(d);
                        yield new Scheduler.TaskHandle() {
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
                    case "cpuExecutor" -> (java.util.concurrent.Executor) Runnable::run;
                    case "isOwner" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("explosion");
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        service = new CollapseService(w -> regions, fake, scheduler(), Runnable::run,
                new CommitBudget(1_000_000_000L, () -> clock[0] += 400_000L), () -> new SupportTable(List.of()),
                () -> randoms.get());
    }

    private void tick() {
        clock[0] += 50_000_000L;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Delayed> it = delayed.iterator(); it.hasNext();) {
            Delayed d = it.next();
            if (--d.ticks()[0] <= 0) {
                it.remove();
                due.add(d.task());
            }
        }
        int n = tasks.size();
        for (int i = 0; i < n; i++) {
            tasks.poll().run();
        }
        due.forEach(Runnable::run);
    }

    private void runAll() {
        for (int i = 0; i < 500 && (!tasks.isEmpty() || !delayed.isEmpty()); i++) {
            tick();
        }
        assertThat(service.isIdle()).isTrue();
    }

    /** Raw ceiling y 100..101 over an air room above a stone floor; the explosion then blows y 100, x and z in -2..2. */
    private List<BlockPos> ceilingAndBlast() {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                world.getBlockAt(x, 95, z).setType(Material.STONE);
                for (int y = 96; y < 100; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
                for (int y = 100; y <= 101; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    b.setType(Material.STONE);
                    assertThat(custom.place(b, BlockStateRef.of(RAW), false)).isTrue();
                }
                world.getBlockAt(x, 102, z).setType(Material.AIR);
            }
        }
        List<BlockPos> blown = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block b = world.getBlockAt(x, 100, z);
                custom.remove(b);
                b.setType(Material.AIR);
                blown.add(new BlockPos(x, 100, z));
            }
        }
        return blown;
    }

    @Test
    void theLayerAboveABlownCeilingFallsWithinTheBlastReach() {
        List<BlockPos> blown = ceilingAndBlast();
        service.explosion(world, new BlockPos(0, 99, 0), blown);
        assertThat(spawns).as("nothing falls at once").isEmpty();
        runAll();

        // reach squared = 2^2 + 2^2 + 1 = 9 from the centre; the block above at (x, 101, z) needs x^2 + z^2 + 4 < 9
        long expected = blown.stream().filter(p -> p.x() * p.x() + p.z() * p.z() + 4 < 9).count();
        assertThat(expected).isEqualTo(13);
        assertThat(spawns).hasSize(13).allMatch(s -> s.equals(COBBLE));
        assertThat(spawnAt).allMatch(p -> p.y() == 101);
        assertThat(service.explosions()).isEqualTo(1);
        assertThat(service.falls(world)).isEqualTo(13);
    }

    @Test
    void noDrawBelowTheChanceStartsNothing() {
        List<BlockPos> blown = ceilingAndBlast();
        randoms = () -> scripted(0.3f); // TFC: nextFloat() < 0.3
        service.explosion(world, new BlockPos(0, 99, 0), blown);
        runAll();
        assertThat(spawns).isEmpty();
        assertThat(service.explosions()).isZero();
    }

    @Test
    void anEmptyBlastStartsNothing() {
        service.explosion(world, new BlockPos(0, 99, 0), List.of());
        runAll();
        assertThat(service.explosions()).isZero();
    }
}
