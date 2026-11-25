/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pump;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-MECH-09 on MockBukkit with fakes of the CraftEngine adapter: a pump with a turning crankshaft draws water up a pipe
 * run of ten and places it at its output, an unpowered pump takes its water back, pipes connect as TFC's
 * {@code updateConnectedSides}, and the chunk index lists the pumps.
 */
class PumpMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(PumpBlockStates.PIPE, PumpBlockStates.PUMP);
    private final PumpScheduler scheduler = new PumpScheduler();
    private final BlockWriter writer = (block, next) -> blocks.place(block, next, false);
    private Rotation shaft;
    private PumpService service;
    private PumpDevices devices;
    private PumpChunkIndex index;
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("pump");
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                world.getChunkAt(cx, cz); // loads it: the pump reads only loaded chunks
            }
        }
        index = new PumpChunkIndex(new PdcChunkDataStore());
        service = new PumpService(scheduler, blocks, writer, () -> Optional.of((pump, side) -> shaft));
        devices = new PumpDevices(scheduler, blocks, writer, service, index);
    }

    private static BlockStateRef pipe(String fluid, Direction... open) {
        return PumpBlockStates.withFaces(BlockStateRef.of(PumpBlockStates.PIPE).with(PumpBlockStates.FLUID, fluid), Set.of(open));
    }

    private Block pump(int x, int y, int z, String facing) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.of(PumpBlockStates.PUMP).with(PumpBlockStates.FACING, facing), false);
        return b;
    }

    /** The done criterion: a pump moves water ten pipes up and places a source at its output. */
    @Test
    void aPumpMovesWaterTenPipesUp() {
        world.getBlockAt(0, 69, 0).setType(Material.WATER);
        for (int y = 70; y <= 79; y++) {
            blocks.place(world.getBlockAt(0, y, 0), pipe(y == 70 ? "water" : "empty", Direction.UP, Direction.DOWN), false);
        }
        Block pump = pump(0, 80, 0, "north");
        shaft = Rotation.of(Direction.SOUTH, 0.1f);
        service.run(world, new BlockPos(0, 80, 0));
        assertThat(world.getBlockAt(0, 80, -1).getType()).isEqualTo(Material.WATER);

        // the shaft stops: the pump takes the water it placed back
        shaft = null;
        service.run(world, new BlockPos(0, 80, 0));
        assertThat(world.getBlockAt(0, 80, -1).getType()).isEqualTo(Material.AIR);
        assertThat(blocks.stateAt(pump)).isPresent();
    }

    /** TFC MAX_COST: a run of seventeen pipes is out of reach. */
    @Test
    void aSeventeenPipeRunIsOutOfReach() {
        world.getBlockAt(0, 62, 0).setType(Material.WATER);
        for (int y = 63; y <= 79; y++) {
            blocks.place(world.getBlockAt(0, y, 0), pipe(y == 63 ? "water" : "empty", Direction.UP, Direction.DOWN), false);
        }
        pump(0, 80, 0, "north");
        shaft = Rotation.of(Direction.SOUTH, 0.1f);
        service.run(world, new BlockPos(0, 80, 0));
        assertThat(world.getBlockAt(0, 80, -1).getType()).isNotEqualTo(Material.WATER);
    }

    @Test
    void aPumpWithNoCrankshaftServiceDoesNothing() {
        PumpService none = new PumpService(scheduler, blocks, writer, Optional::empty);
        world.getBlockAt(0, 69, 0).setType(Material.WATER);
        blocks.place(world.getBlockAt(0, 70, 0), pipe("water", Direction.UP, Direction.DOWN), false);
        pump(0, 71, 0, "north");
        none.run(world, new BlockPos(0, 71, 0));
        assertThat(world.getBlockAt(0, 71, -1).getType()).isNotEqualTo(Material.WATER);
    }

    /**
     * PAR-MECH-14: the pump finds the crankshaft's {@link ShaftSource} through {@code ModuleServices} (as {@code PumpModule}
     * does, even when the provider registers after the pump service exists) and asks the face opposite its facing.
     */
    @Test
    void aShaftSourceProvidedByTheCrankshaftModulePowersThePump() {
        ModuleServices services = new ModuleServices();
        PumpService wired = new PumpService(scheduler, blocks, writer, () -> services.get(ShaftSource.class));
        world.getBlockAt(0, 69, 0).setType(Material.WATER);
        for (int y = 70; y <= 79; y++) {
            blocks.place(world.getBlockAt(0, y, 0), pipe(y == 70 ? "water" : "empty", Direction.UP, Direction.DOWN), false);
        }
        pump(0, 80, 0, "north");
        wired.run(world, new BlockPos(0, 80, 0));
        assertThat(world.getBlockAt(0, 80, -1).getType()).isNotEqualTo(Material.WATER); // nobody provides yet

        Rotation turning = Rotation.of(Direction.SOUTH, 0.1f);
        services.provide(ShaftSource.class, (block, side) -> side == Direction.SOUTH ? turning : null);
        wired.run(world, new BlockPos(0, 80, 0));
        assertThat(world.getBlockAt(0, 80, -1).getType()).isEqualTo(Material.WATER);
    }

    @Test
    void aTaskThatFindsNoPumpStopsItself() {
        service.run(world, new BlockPos(5, 70, 5));
        assertThat(service.running()).isZero();
    }

    /** TFC updateConnectedSides: no neighbour gives the look axis; one gives a straight pipe; a pump above connects its top. */
    @Test
    void pipesConnectAsTfc() {
        Block lone = world.getBlockAt(10, 70, 10);
        blocks.place(lone, pipe("empty"), false);
        devices.placed(lone, false, Direction.EAST, Direction.NORTH);
        BlockStateRef s = blocks.stateAt(lone).orElseThrow();
        assertThat(PumpBlockStates.faces(s)).containsExactlyInAnyOrder(Direction.EAST, Direction.WEST);
        assertThat(PumpBlockStates.fluid(s)).isNull();

        Block wet = world.getBlockAt(10, 72, 10);
        blocks.place(wet, pipe("empty"), false);
        devices.placed(wet, true, Direction.UP, Direction.NORTH);
        assertThat(PumpBlockStates.fluid(blocks.stateAt(wet).orElseThrow())).isEqualTo("water");

        // a second pipe above the first: both straight up and down
        Block above = world.getBlockAt(10, 71, 10);
        blocks.place(above, pipe("empty"), false);
        devices.placed(above, false, Direction.NORTH, Direction.NORTH);
        assertThat(PumpBlockStates.faces(blocks.stateAt(above).orElseThrow()))
            .containsExactlyInAnyOrder(Direction.UP, Direction.DOWN);
        assertThat(PumpBlockStates.faces(blocks.stateAt(wet).orElseThrow())).isNotEmpty();

        // a pump above the pipe at y=70 does not exist here; the pump above `wet` connects its top
        Block top = pump(10, 73, 10, "south");
        devices.placed(top, false, Direction.NORTH, Direction.WEST);
        assertThat(PumpBlockStates.facing(blocks.stateAt(top).orElseThrow())).isEqualTo(Direction.WEST);
        assertThat(PumpBlockStates.faces(blocks.stateAt(wet).orElseThrow())).contains(Direction.UP);
    }

    @Test
    void facingHelpersFollowVanilla() {
        assertThat(PumpDevices.horizontal(0f)).isEqualTo(Direction.SOUTH);
        assertThat(PumpDevices.horizontal(90f)).isEqualTo(Direction.WEST);
        assertThat(PumpDevices.horizontal(-180f)).isEqualTo(Direction.NORTH);
        assertThat(PumpDevices.horizontal(-90f)).isEqualTo(Direction.EAST);
        assertThat(PumpDevices.nearestLooking(new org.bukkit.util.Vector(0.1, -0.9, 0.2))).isEqualTo(Direction.DOWN);
        assertThat(PumpDevices.nearestLooking(new org.bukkit.util.Vector(-0.8, 0.1, 0.2))).isEqualTo(Direction.WEST);
    }

    @Test
    void theChunkIndexListsPumps() {
        Block b = pump(3, 70, 4, "east");
        index.track(b);
        assertThat(index.positions(b.getChunk())).containsExactly(new BlockPos(3, 70, 4));
        index.untrack(b);
        assertThat(index.positions(b.getChunk())).isEmpty();
    }
}
