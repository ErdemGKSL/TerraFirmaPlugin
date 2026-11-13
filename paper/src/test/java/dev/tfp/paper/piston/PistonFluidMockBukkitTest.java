/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piston;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-DATA-16 on MockBukkit: piston events watch the destinations of moved fluid-carrying blocks; once the move lands
 * (a 1-tick pulse places the block with its water, MC-130183), the block is dried through the region commit queue.
 * Region tasks run when the test calls {@link #tick()}; "this tick" and "next tick" tasks are kept apart so the polling
 * while {@code moving_piston} is visible.
 */
class PistonFluidMockBukkitTest extends MockServerTest {
    static final String WET_SLAB = "minecraft:oak_slab[type=bottom,waterlogged=true]";
    static final String DRY_SLAB = "minecraft:oak_slab[type=bottom,waterlogged=false]";

    final ArrayDeque<Runnable> now = new ArrayDeque<>();
    final List<Runnable> nextTick = new ArrayList<>();
    final FakeCustomBlocks custom = new FakeCustomBlocks().known("tfp:test/reed");
    PistonFluidService service;
    WorldMock world;
    Block piston;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("piston");
        RegionExecutor regions = new RegionExecutor() {
            @Override
            public void run(int chunkX, int chunkZ, Runnable task) {
                now.add(task);
            }

            @Override
            public void runNextTick(int chunkX, int chunkZ, Runnable task) {
                nextTick.add(task);
            }

            @Override
            public boolean isOwner(int chunkX, int chunkZ) {
                return true;
            }
        };
        service = new PistonFluidService(w -> regions, custom, Runnable::run,
                new CommitBudget(PistonFluidService.BUDGET_NANOS, System::nanoTime));
        server.getPluginManager().registerEvents(new PistonFluidListener(service), plugin);
        world.loadChunk(0, 0);
        piston = world.getBlockAt(0, 64, 0);
        piston.setType(Material.STICKY_PISTON);
    }

    /** Runs one tick of region tasks (and the tasks they queue for this tick). */
    void tick() {
        now.addAll(nextTick);
        nextTick.clear();
        while (!now.isEmpty()) {
            now.poll().run();
        }
    }

    void set(Block b, String data) {
        b.setBlockData(Bukkit.createBlockData(data), false);
    }

    String data(Block b) {
        return b.getBlockData().getAsString();
    }

    @Test
    void oneTickPulseLandsDry() {
        Block origin = world.getBlockAt(1, 64, 0);
        Block dest = world.getBlockAt(2, 64, 0);
        set(origin, WET_SLAB);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(origin), BlockFace.EAST));
        assertThat(service.watching(world)).isEqualTo(1);
        // the move starts: origin empties, destination holds the moving block
        origin.setType(Material.AIR);
        dest.setType(Material.MOVING_PISTON);
        tick();
        assertThat(nextTick).as("still moving: polled again next tick").hasSize(1);
        // the piston retracts at once: finalTick places the slab with its water (MC-130183)
        set(dest, WET_SLAB);
        tick();
        assertThat(data(dest)).isEqualTo(DRY_SLAB);
        assertThat(origin.getType()).as("no water source left at the origin").isEqualTo(Material.AIR);
        assertThat(service.watching(world)).isZero();
        assertThat(service.pipeline(world).isIdle()).isTrue();
        assertThat(service.pipeline(world).metrics().applied.sum()).isEqualTo(1);
    }

    @Test
    void normalPulseIsAlreadyDryAndCostsNoWrite() {
        Block origin = world.getBlockAt(1, 64, 0);
        Block dest = world.getBlockAt(2, 64, 0);
        set(origin, WET_SLAB);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(origin), BlockFace.EAST));
        origin.setType(Material.AIR);
        dest.setType(Material.MOVING_PISTON);
        tick();
        tick();
        set(dest, DRY_SLAB); // vanilla's tick() clears waterlogged on a normal landing
        tick();
        assertThat(data(dest)).isEqualTo(DRY_SLAB);
        assertThat(service.pipeline(world).metrics().applied.sum()).isZero();
        assertThat(service.watching(world)).isZero();
    }

    @Test
    void stickyRetractPullsDry() {
        Block origin = world.getBlockAt(2, 64, 0);
        Block dest = world.getBlockAt(1, 64, 0);
        set(origin, "minecraft:oak_fence[east=false,north=false,south=false,waterlogged=true,west=false]");
        server.getPluginManager().callEvent(new BlockPistonRetractEvent(piston, List.of(origin), BlockFace.WEST));
        origin.setType(Material.AIR);
        set(dest, "minecraft:oak_fence[east=false,north=false,south=false,waterlogged=true,west=false]");
        tick();
        assertThat(data(dest)).isEqualTo("minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
    }

    @Test
    void craftEngineFluidLoggedBlockLandsEmpty() {
        Block origin = world.getBlockAt(1, 65, 0);
        Block dest = world.getBlockAt(1, 66, 0);
        custom.place(origin, BlockStateRef.parse("tfp:test/reed[fluid=water,part=lower]"), false);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(origin), BlockFace.UP));
        custom.place(dest, BlockStateRef.parse("tfp:test/reed[fluid=water,part=lower]"), false);
        tick();
        assertThat(custom.placed().get(FakeCustomBlocks.at(dest)))
                .isEqualTo(BlockStateRef.parse("tfp:test/reed[fluid=empty,part=lower]"));
    }

    @Test
    void iceArrivesAsAir() {
        Block origin = world.getBlockAt(1, 64, 0);
        Block dest = world.getBlockAt(2, 64, 0);
        origin.setType(Material.ICE);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(origin), BlockFace.EAST));
        origin.setType(Material.AIR);
        dest.setType(Material.ICE);
        tick();
        assertThat(dest.getType()).isEqualTo(Material.AIR);
    }

    @Test
    void anotherBlockAtTheDestinationIsLeftAlone() {
        Block origin = world.getBlockAt(1, 64, 0);
        Block dest = world.getBlockAt(2, 64, 0);
        set(origin, WET_SLAB);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(origin), BlockFace.EAST));
        set(dest, "minecraft:stone_slab[type=bottom,waterlogged=true]");
        tick();
        assertThat(data(dest)).isEqualTo("minecraft:stone_slab[type=bottom,waterlogged=true]");
    }

    @Test
    void stuckMoveGivesUpAfterMaxPolls() {
        Block origin = world.getBlockAt(1, 64, 0);
        Block dest = world.getBlockAt(2, 64, 0);
        set(origin, WET_SLAB);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(origin), BlockFace.EAST));
        dest.setType(Material.MOVING_PISTON);
        for (int i = 0; i < PistonFluidService.MAX_POLLS + 2; i++) {
            tick();
        }
        assertThat(nextTick).isEmpty();
        assertThat(service.watching(world)).isZero();
        assertThat(dest.getType()).isEqualTo(Material.MOVING_PISTON);
    }

    @Test
    void dryBlocksAndCancelledEventsCostNothing() {
        Block stone = world.getBlockAt(1, 64, 0);
        stone.setType(Material.STONE);
        Block dry = world.getBlockAt(1, 65, 0);
        set(dry, DRY_SLAB);
        server.getPluginManager().callEvent(new BlockPistonExtendEvent(piston, List.of(stone, dry), BlockFace.EAST));
        Block wet = world.getBlockAt(1, 66, 0);
        set(wet, WET_SLAB);
        BlockPistonExtendEvent cancelled = new BlockPistonExtendEvent(piston, List.of(wet), BlockFace.EAST);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertThat(service.watching(world)).isZero();
        assertThat(nextTick).isEmpty();
    }

    @Test
    void wiredThroughTheModuleServiceFile() throws Exception {
        String services = new String(PistonFluidModule.class.getClassLoader()
                .getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(services.lines()).contains(PistonFluidModule.class.getName());
        assertThat(new PistonFluidModule().id()).isEqualTo("piston_fluid");
    }
}
