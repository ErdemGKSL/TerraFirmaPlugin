/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.rotation.spin.ShaftReport;
import dev.tfp.paper.rotation.spin.SpinVisuals;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.assertj.core.data.Offset;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-MECH-20 on MockBukkit: the bladed axle is a device kind that joins a network like an axle, the shaft hook reports it with
 * its blade model, and the rotation readout the trip hammer uses (the bladed axle above it) follows the line.
 */
class BladedAxleMockBukkitTest extends MockServerTest {
    static final String AXLE = "tfp:wood/axle/oak";
    static final String BLADED = "tfp:wood/bladed_axle/oak";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(AXLE, BLADED, RotationBlockStates.MOTOR);
    private final RotationMockBukkitTest.MemoryStore store = new RotationMockBukkitTest.MemoryStore();
    private final RotationMockBukkitTest.Ops ops = new RotationMockBukkitTest.Ops();
    private final List<ShaftReport> shafts = new ArrayList<>();
    private RotationService networks;
    private DeviceService devices;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("bladed");
        ops.blocks = blocks;
        networks = new RotationService(Runnable::run, (w, pos) -> { }, Logger.getLogger("test"));
        networks.shaftHook((w, changed, removed) -> shafts.addAll(changed));
        RotationDevices kinds = new RotationDevices(networks, ops, new RotationChunkIndex(new PdcChunkDataStore()),
            s -> s.getType() == Material.BLAZE_ROD);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        kinds.kinds().forEach(devices::register);
        player = server.addPlayer();
    }

    private Block place(int x, int y, int z, String id, String axis) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.of(id).with("axis", axis), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty());
        return b;
    }

    /** The in-game step on MockBukkit: a bladed axle in a motor line at step 8 turns at 30 rpm and is listed. */
    @Test
    void bladedAxleTurnsWithTheLine() {
        Block motor = place(0, 64, 0, RotationBlockStates.MOTOR, "x");
        place(1, 64, 0, AXLE, "x");
        Block bladed = place(2, 64, 0, BLADED, "x");
        ops.face = Direction.SOUTH;
        for (int i = 0; i < 8; i++) {
            devices.use(motor, player, EquipmentSlot.HAND, ItemStack.empty());
        }
        RotationService.Report r = networks.report(world.getUID(), RotationDevices.pos(bladed)).join().orElseThrow();
        assertThat(r.target().block()).isEqualTo(MechanicalBlock.BLADED_AXLE);
        assertThat(r.target().rpm()).isCloseTo(30f, Offset.offset(1e-4f));
        assertThat(r.network()).hasSize(3);
        assertThat(RotationModule.reply(r).details()).anySatisfy(l -> assertThat(l).contains("bladed_axle"));

        // the shaft hook reports it (the spinning display draws TFC's shaft-and-blade model)
        assertThat(shafts).anySatisfy(s -> {
            assertThat(s.pos()).isEqualTo(RotationDevices.pos(bladed));
            assertThat(s.speed()).isNotZero();
        });
        assertThat(SpinVisuals.renderItem(BLADED)).contains("tfp:render/rotation/wood/bladed_axle/oak");
        assertThat(SpinVisuals.renderItem(AXLE)).contains("tfp:render/rotation/wood/axle/oak");
    }

    /** The trip hammer's read (PAR-MECH-07): the rotation of the bladed axle at a position, empty when it leaves the line. */
    @Test
    void rotationReadoutOfTheBladedAxle() {
        place(0, 64, 0, RotationBlockStates.MOTOR, "z");
        Block bladed = place(0, 64, 1, BLADED, "z");
        BlockPos pos = RotationDevices.pos(bladed);
        Rotation at = networks.rotation(world.getUID(), pos).orElseThrow();
        assertThat(at.direction()).isEqualTo(Direction.SOUTH);
        assertThat(at.speed()).isZero(); // the motor at step 0
        devices.removed(world, 0, 64, 0, RotationBlockStates.MOTOR, store.data.get(FakeCustomBlocks.at(world.getBlockAt(0, 64, 0))));
        assertThat(networks.rotation(world.getUID(), pos)).isEmpty();
        assertThat(networks.rotation(world.getUID(), new BlockPos(9, 9, 9))).isEmpty();
    }

    /** RotationBlockStates: the bladed axle id is its own kind, parsed as an axle node. */
    @Test
    void parsedAsAnAxleNode() {
        assertThat(RotationBlockStates.block(BLADED)).contains(MechanicalBlock.BLADED_AXLE);
        assertThat(RotationBlockStates.KINDS).contains(RotationBlockStates.BLADED_AXLE);
        assertThat(RotationBlockStates.parse(BlockStateRef.of(BLADED).with("axis", "y"), 0).orElseThrow())
            .isEqualTo(MechanicalBlock.State.axle(MechanicalBlock.BLADED_AXLE, dev.tfp.core.rotation.RotationAxis.Y));
        assertThat(PaperRotationWorld.dropOf(BlockStateRef.of(BLADED))).contains(java.util.Optional.of(BLADED));
    }
}
