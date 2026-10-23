/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-INFRA-10: the listener path on MockBukkit. A physics event reaches the runner only for blocks a registered rule
 * may apply to (vanilla id or CraftEngine id through the adapter); nothing at all happens while no rule exists. Region
 * tasks are only collected here (the runner's own behaviour is covered by {@link NeighbourRunnerTest}).
 */
class NeighbourListenerMockBukkitTest extends MockServerTest {
    final List<Runnable> regionTasks = new ArrayList<>();
    final FakeCustomBlocks custom = new FakeCustomBlocks().known("tfp:test/beam");
    NeighbourService service;
    WorldMock world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("neighbour");
        RegionExecutor collect = new RegionExecutor() {
            @Override
            public void run(int chunkX, int chunkZ, Runnable task) {
                regionTasks.add(task);
            }

            @Override
            public void runNextTick(int chunkX, int chunkZ, Runnable task) {
                regionTasks.add(task);
            }

            @Override
            public boolean isOwner(int chunkX, int chunkZ) {
                return true;
            }
        };
        service = new NeighbourService(w -> collect, custom, Runnable::run, new CommitBudget(1_000_000, System::nanoTime));
        server.getPluginManager().registerEvents(new NeighbourListener(service), plugin);
    }

    void physics(Block b) {
        server.getPluginManager().callEvent(new BlockPhysicsEvent(b, b.getBlockData()));
    }

    @Test
    void noRuleMeansNoWork() {
        Block b = world.getBlockAt(1, 64, 1);
        b.setType(Material.BONE_BLOCK);
        physics(b);
        assertThat(service.runner(world).requests()).isZero();
        assertThat(regionTasks).isEmpty();
    }

    @Test
    void physicsRequestsOnlyBlocksARuleMayApplyTo() {
        service.register(NeighbourSelfCheck.boneAxisRule());
        Block bone = world.getBlockAt(1, 64, 1);
        bone.setType(Material.BONE_BLOCK);
        Block stone = world.getBlockAt(2, 64, 1);
        stone.setType(Material.STONE);
        physics(stone);
        assertThat(service.runner(world).requests()).isZero();
        physics(bone);
        physics(bone);
        assertThat(service.runner(world).requests()).isEqualTo(2);
        assertThat(service.runner(world).tickCoalesced()).isEqualTo(1);
        assertThat(regionTasks).as("one flush for the chunk").hasSize(1);
    }

    @Test
    void craftEngineBlocksAreMatchedByTheirCustomId() {
        NeighbourService.Registration reg = service.register(new BeamRule());
        Block b = world.getBlockAt(5, 64, 5);
        custom.place(b, BlockStateRef.of("tfp:test/beam"), false);
        physics(b);
        assertThat(service.runner(world).requests()).isEqualTo(1);
        reg.unregister();
        assertThat(service.rules().isEmpty()).isTrue();
        physics(b);
        assertThat(service.runner(world).requests()).isEqualTo(1);
    }

    @Test
    void wiredThroughTheModuleServiceFile() throws Exception {
        String services = new String(NeighbourModule.class.getClassLoader()
                .getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(services.lines()).contains(NeighbourModule.class.getName());
        assertThat(new NeighbourModule().id()).isEqualTo("neighbour");
    }

    /** A rule on a CraftEngine id that never changes anything. */
    static final class BeamRule implements dev.tfp.core.neighbour.ShapeRule<BlockStateRef> {
        @Override
        public String id() {
            return "beam";
        }

        @Override
        public java.util.Set<String> blockIds() {
            return java.util.Set.of("tfp:test/beam");
        }

        @Override
        public boolean appliesTo(BlockStateRef state) {
            return state.id().equals("tfp:test/beam");
        }

        @Override
        public BlockStateRef decide(dev.tfp.core.neighbour.Neighbourhood<BlockStateRef> n) {
            return null;
        }
    }
}
