/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.coral;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.coral.CoralRules;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-25 on MockBukkit with a fake CraftEngine adapter: a living coral out of water dies after TFC's 60..99 tick
 * delay (the facing of a wall fan kept), one in water or next to water lives, a coral that got its water back before the
 * tick lives; the support rule drops a wall fan without its wall and a sea pickle without a floor.
 */
class CoralMockBukkitTest extends MockServerTest {
    /** cpu and region tasks run at once; delayed ones wait for {@link #elapse}. */
    static final class Ticks implements Scheduler {
        record Later(long delay, Runnable task) {}

        final List<Later> later = new ArrayList<>();

        void elapse() {
            final List<Later> now = new ArrayList<>(later);
            later.clear();
            now.forEach(l -> l.task().run());
        }

        @Override
        public void region(Location location, Runnable task) {
            task.run();
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            later.add(new Later(delayTicks, task));
            return null;
        }

        @Override
        public TaskHandle regionRepeating(World world, int cx, int cz, long initialDelayTicks, long periodTicks, Runnable task) {
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
            task.run();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
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
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {}
    }

    World world;
    FakeCustomBlocks custom;
    Ticks ticks;
    CoralDeath death;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("coral");
        custom = new FakeCustomBlocks();
        CoralRules.all().forEach(c -> custom.known(c.id()));
        custom.known(CoralRules.SEA_PICKLE);
        ticks = new Ticks();
        death = new CoralDeath(ticks, custom, () -> RandomGenerator.of("L64X128MixRandom"));
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
                for (int y = 64; y <= 66; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    BlockStateRef state(String id, String... kv) {
        final Map<String, String> p = new java.util.TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            p.put(kv[i], kv[i + 1]);
        }
        return BlockStateRef.of(id, p);
    }

    @Test
    void livingCoralOnDryLandDiesAfterTheDelay() {
        final Block b = world.getBlockAt(0, 64, 0);
        custom.place(b, state("tfp:coral/tube_coral", "waterlogged", "false"), false);
        death.check(b);
        assertThat(ticks.later).hasSize(1);
        assertThat(ticks.later.getFirst().delay()).isBetween(60L, 99L);
        assertThat(custom.stateAt(b)).map(BlockStateRef::id).contains("tfp:coral/tube_coral");
        // a second update while the tick waits schedules nothing more (scheduleTick of a scheduled tick)
        death.check(b);
        assertThat(ticks.later).hasSize(1);
        ticks.elapse();
        assertThat(custom.stateAt(b)).contains(state("tfp:coral/tube_dead_coral", "waterlogged", "false"));
        assertThat(death.pending()).isZero();
    }

    @Test
    void wallFanKeepsItsFacingWhenItDies() {
        final Block b = world.getBlockAt(0, 64, 0);
        custom.place(b, state("tfp:coral/fire_coral_wall_fan", "facing", "east", "waterlogged", "false"), false);
        death.check(b);
        ticks.elapse();
        assertThat(custom.stateAt(b)).contains(
            state("tfp:coral/fire_dead_coral_wall_fan", "facing", "east", "waterlogged", "false"));
    }

    @Test
    void wetCoralsLive() {
        final Block own = world.getBlockAt(0, 64, 0);
        custom.place(own, state("tfp:coral/brain_coral_fan", "waterlogged", "true"), false);
        death.check(own);
        final Block nextToWater = world.getBlockAt(2, 64, 2);
        custom.place(nextToWater, state("tfp:coral/brain_coral", "waterlogged", "false"), false);
        world.getBlockAt(2, 65, 2).setType(Material.WATER);
        death.check(nextToWater);
        assertThat(ticks.later).isEmpty();
    }

    @Test
    void waterBackBeforeTheTickSavesIt() {
        final Block b = world.getBlockAt(0, 64, 0);
        custom.place(b, state("tfp:coral/horn_coral", "waterlogged", "false"), false);
        death.check(b);
        world.getBlockAt(1, 64, 0).setType(Material.WATER);
        ticks.elapse();
        assertThat(custom.stateAt(b)).map(BlockStateRef::id).contains("tfp:coral/horn_coral");
    }

    @Test
    void deadCoralsAndOtherBlocksAreIgnored() {
        final Block b = world.getBlockAt(0, 64, 0);
        custom.place(b, state("tfp:coral/tube_dead_coral", "waterlogged", "false"), false);
        death.check(b);
        death.check(world.getBlockAt(1, 64, 1));
        assertThat(ticks.later).isEmpty();
    }

    /** Wall fan without its wall and sea pickle without a floor become air; supported ones stay. */
    @Test
    void supportRule() {
        final CoralSupport rule = new CoralSupport(new CoralSupport.Faces() {
            @Override
            public Optional<Boolean> sturdy(BlockStateRef s, Direction face) {
                return s.namespace().equals("minecraft") ? Optional.of(s.id().equals("minecraft:stone")) : Optional.empty();
            }

            @Override
            public Optional<Boolean> topNonEmpty(BlockStateRef s) {
                return s.namespace().equals("minecraft")
                    ? Optional.of(s.id().equals("minecraft:stone") || s.id().equals("minecraft:oak_fence"))
                    : Optional.empty();
            }
        });
        final BlockStateRef stone = BlockStateRef.of("minecraft:stone");
        final BlockStateRef water = BlockStateRef.parse("minecraft:water[level=0]");
        final BlockStateRef fan = state("tfp:coral/tube_coral_wall_fan", "facing", "north", "waterlogged", "true");
        // a north-facing fan hangs on the block to its south
        assertThat(rule.decide(around(fan, Direction.SOUTH, stone, water))).isNull();
        assertThat(rule.decide(around(fan, Direction.SOUTH, water, water))).isEqualTo(CoralSupport.AIR);
        assertThat(rule.decide(around(fan, Direction.NORTH, stone, water))).isEqualTo(CoralSupport.AIR);
        final BlockStateRef coral = state("tfp:coral/tube_coral", "waterlogged", "true");
        assertThat(rule.decide(around(coral, Direction.DOWN, stone, water))).isNull();
        assertThat(rule.decide(around(coral, Direction.DOWN, water, water))).isEqualTo(CoralSupport.AIR);
        // an unknown (CraftEngine) support keeps the block
        assertThat(rule.decide(around(coral, Direction.DOWN, BlockStateRef.of("tfp:rock/raw/granite"), water))).isNull();
        final BlockStateRef pickle = state(CoralRules.SEA_PICKLE, "pickles", "2", "waterlogged", "true");
        assertThat(rule.decide(around(pickle, Direction.DOWN, stone, water))).isNull();
        assertThat(rule.decide(around(pickle, Direction.DOWN, BlockStateRef.of("minecraft:oak_fence"), water))).isNull();
        assertThat(rule.decide(around(pickle, Direction.DOWN, water, water))).isEqualTo(CoralSupport.AIR);
        assertThat(rule.blockIds()).hasSize(31);
        assertThat(rule.appliesTo(stone)).isFalse();
    }

    /** A neighbourhood of {@code center} with {@code side} at one face and {@code rest} everywhere else. */
    static Neighbourhood<BlockStateRef> around(BlockStateRef center, Direction at, BlockStateRef side, BlockStateRef rest) {
        return Neighbourhood.of((dx, dy, dz) -> {
            if (dx == 0 && dy == 0 && dz == 0) {
                return center;
            }
            return dx == at.dx() && dy == at.dy() && dz == at.dz() ? side : rest;
        });
    }

    @Test
    void waterOfStates() {
        assertThat(CoralStates.water(BlockStateRef.parse("minecraft:water[level=7]"))).isTrue();
        assertThat(CoralStates.water(BlockStateRef.parse("minecraft:oak_slab[type=bottom,waterlogged=true]"))).isTrue();
        assertThat(CoralStates.water(BlockStateRef.parse("minecraft:oak_slab[type=bottom,waterlogged=false]"))).isFalse();
        assertThat(CoralStates.water(BlockStateRef.of("minecraft:kelp_plant"))).isTrue();
        assertThat(CoralStates.water(BlockStateRef.of("minecraft:lava"))).isFalse();
        assertThat(CoralStates.water(state("tfp:plant/x", "fluid", "empty"))).isFalse();
        assertThat(CoralStates.water(state("tfp:plant/x", "fluid", "salt_water"))).isTrue();
        assertThat(CoralStates.water(null)).isNull();
    }
}
