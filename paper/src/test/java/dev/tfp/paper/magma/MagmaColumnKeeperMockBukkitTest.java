/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.magma;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.Queue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-WORLD-54: bubble columns over a fake CE magma block: formed under a swimming player, kept against physics. */
class MagmaColumnKeeperMockBukkitTest extends MockServerTest {
    static final String MAGMA = "tfp:rock/magma/basalt";
    final FakeCustomBlocks custom = new FakeCustomBlocks().known(MAGMA);
    final Queue<Runnable> tasks = new ArrayDeque<>();
    MagmaColumnKeeper keeper;
    WorldMock world;
    Block magma;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("caldera");
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        RegionExecutor regions = new RegionExecutor() {
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
        keeper = new MagmaColumnKeeper(w -> regions, custom, Runnable::run,
                new CommitBudget(MagmaColumnKeeper.BUDGET_NANOS, System::nanoTime));
        server.getPluginManager().registerEvents(keeper, plugin);
        // a flooded caldera floor: magma at y 40, water sources up to y 45, air above
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(x, 39, z).setType(Material.STONE);
                world.getBlockAt(x, 40, z).setType(Material.STONE); // the CE block's server state is not vanilla magma
                custom.place(world.getBlockAt(x, 40, z), BlockStateRef.of(MAGMA), false);
                for (int y = 41; y <= 45; y++) {
                    set(world.getBlockAt(x, y, z), "minecraft:water[level=0]");
                }
                world.getBlockAt(x, 46, z).setType(Material.AIR);
            }
        }
        magma = world.getBlockAt(0, 40, 0);
    }

    void set(Block b, String data) {
        b.setBlockData(Bukkit.createBlockData(data), false);
    }

    void drain() {
        while (!tasks.isEmpty()) {
            tasks.poll().run();
        }
    }

    @Test
    void aSwimmingPlayerOverMagmaGetsADragColumn() {
        PlayerMock player = server.addPlayer();
        player.teleport(world.getBlockAt(0, 43, 0).getLocation().add(0.5, 0, 0.5));
        keeper.nearPlayer(player);
        drain();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 41; y <= 45; y++) {
                    assertThat(world.getBlockAt(x, y, z).getBlockData().getAsString())
                        .as("%d %d %d", x, y, z).isEqualTo("minecraft:bubble_column[drag=true]");
                }
                assertThat(world.getBlockAt(x, 46, z).getType()).isEqualTo(Material.AIR);
            }
        }
        // complete columns are not submitted again
        long submits = keeper.pipeline(world).metrics().submits.sum();
        keeper.nearPlayer(player);
        assertThat(keeper.pipeline(world).metrics().submits.sum()).isEqualTo(submits);
        assertThat(keeper.floor(world.getBlockAt(0, 43, 0))).isEmpty();
    }

    @Test
    void waterOverOtherBlocksAndDryPlayersAreIgnored() {
        custom.remove(magma);
        magma.setType(Material.STONE);
        assertThat(keeper.floor(world.getBlockAt(0, 44, 0))).isEmpty();
        assertThat(keeper.floor(world.getBlockAt(1, 44, 0))).contains(new BlockPos(1, 40, 0));
        PlayerMock dry = server.addPlayer();
        dry.teleport(world.getBlockAt(5, 80, 5).getLocation());
        keeper.nearPlayer(dry);
        assertThat(tasks).isEmpty();
    }

    @Test
    void physicsCannotRemoveAColumnStandingOnMagma() {
        for (int y = 41; y <= 45; y++) {
            set(world.getBlockAt(0, y, 0), "minecraft:bubble_column[drag=true]");
        }
        Block top = world.getBlockAt(0, 45, 0);
        BlockPhysicsEvent onMagma = new BlockPhysicsEvent(top, top.getBlockData());
        server.getPluginManager().callEvent(onMagma);
        assertThat(onMagma.isCancelled()).isTrue();

        // the magma is gone: vanilla may turn the column back into water
        custom.remove(magma);
        magma.setType(Material.STONE);
        BlockPhysicsEvent onStone = new BlockPhysicsEvent(top, top.getBlockData());
        server.getPluginManager().callEvent(onStone);
        assertThat(onStone.isCancelled()).isFalse();

        // other blocks' physics is not touched
        Block water = world.getBlockAt(1, 44, 0);
        BlockPhysicsEvent onWater = new BlockPhysicsEvent(water, water.getBlockData());
        server.getPluginManager().callEvent(onWater);
        assertThat(onWater.isCancelled()).isFalse();
    }
}
