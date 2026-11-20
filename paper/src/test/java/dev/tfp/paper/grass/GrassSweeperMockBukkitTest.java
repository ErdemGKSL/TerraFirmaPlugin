/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grass;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-11: the sweep dies grass under a placed block after a catch-up, and only stamps a fresh chunk. */
class GrassSweeperMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:grass/mollisol", "tfp:dirt/mollisol");
    private World world;
    private GrassSweeper sweeper;
    private long time = 1000;

    /** Everything runs at once on the calling thread. */
    private static Scheduler inline() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "cpu", "global" -> ((Runnable) args[0]).run();
                        case "region" -> ((Runnable) args[args.length - 1]).run();
                        case "cpuExecutor" -> {
                            return (Executor) Runnable::run;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                    return null;
                });
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("grass");
        world.loadChunk(0, 0);
        sweeper = new GrassSweeper(blocks, inline(), w -> 3, w -> time);
    }

    private Block grassWithStoneAbove() {
        Block grass = world.getBlockAt(3, 64, 3);
        grass.setType(Material.GRASS_BLOCK);
        blocks.place(grass, BlockStateRef.of("tfp:grass/mollisol"), false);
        world.getBlockAt(3, 65, 3).setType(Material.STONE);
        return grass;
    }

    @Test
    void aFreshChunkIsOnlyStamped() {
        Block grass = grassWithStoneAbove();
        assertThat(sweeper.sweepChunk(world, 0, 0)).isFalse();
        assertThat(blocks.stateAt(grass)).hasValueSatisfying(s -> assertThat(s.id()).isEqualTo("tfp:grass/mollisol"));
    }

    @Test
    void grassUnderAPlacedBlockTurnsToDirtAfterACatchUp() {
        Block grass = grassWithStoneAbove();
        assertThat(sweeper.sweepChunk(world, 0, 0)).isFalse();
        time += 24000;
        assertThat(world.isChunkLoaded(0, 0)).as("loaded").isTrue();
        assertThat(sweeper.snapshot(world, world.getChunkAt(0, 0, false))).as("cells").isNotEmpty();
        assertThat(sweeper.sweepChunk(world, 0, 0)).isTrue();
        assertThat(blocks.stateAt(grass)).hasValueSatisfying(s -> assertThat(s.id()).isEqualTo("tfp:dirt/mollisol"));
    }

    @Test
    void tooLittleTimeDoesNothing() {
        Block grass = grassWithStoneAbove();
        sweeper.sweepChunk(world, 0, 0);
        time += 100;
        assertThat(sweeper.sweepChunk(world, 0, 0)).isFalse();
        assertThat(blocks.stateAt(grass)).hasValueSatisfying(s -> assertThat(s.id()).isEqualTo("tfp:grass/mollisol"));
    }
}
