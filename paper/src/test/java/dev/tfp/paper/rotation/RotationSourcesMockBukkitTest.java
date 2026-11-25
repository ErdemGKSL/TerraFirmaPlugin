/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.waterwheel.WaterWheelNetwork;
import dev.tfp.paper.windmill.WindmillNetwork;
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
 * PAR-MECH-17 on MockBukkit: the water wheel and windmill services of the rotation module move the network (inline actor), a
 * speed change keeps the network id, a windmill turned back into its axle re-adds the axle, and a rejected source breaks.
 */
class RotationSourcesMockBukkitTest extends MockServerTest {
    static final String AXLE = "tfp:wood/axle/oak";
    static final String WHEEL = "tfp:wood/water_wheel/oak";
    static final String WINDMILL = "tfp:wood/windmill/oak";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(AXLE, WHEEL, WINDMILL, RotationBlockStates.MOTOR);
    private final RotationMockBukkitTest.MemoryStore store = new RotationMockBukkitTest.MemoryStore();
    private final RotationMockBukkitTest.Ops ops = new RotationMockBukkitTest.Ops();
    private final List<BlockPos> breaks = new ArrayList<>();
    private final InlineScheduler scheduler = new InlineScheduler();
    private RotationService networks;
    private RotationDevices kinds;
    private DeviceService devices;
    private RotationChunkIndex index;
    private PaperRotationWorld paper;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("sources");
        ops.blocks = blocks;
        networks = new RotationService(Runnable::run, (w, pos) -> breaks.add(pos), Logger.getLogger("test"));
        index = new RotationChunkIndex(new PdcChunkDataStore());
        kinds = new RotationDevices(networks, ops, index, s -> s.getType() == Material.BLAZE_ROD);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        kinds.kinds().forEach(devices::register);
        paper = new PaperRotationWorld(scheduler, blocks, () -> null,
            new FakeCustomItems().define(WHEEL, n -> new ItemStack(Material.OAK_PLANKS, n)), uid -> world);
        paper.bind(networks, devices);
        player = server.addPlayer();
    }

    private Block axle(int x, int y, int z, String axis) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.of(AXLE).with("axis", axis), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty());
        return b;
    }

    private RotationService.Report report(Block b) {
        return networks.report(world.getUID(), RotationDevices.pos(b)).join().orElseThrow();
    }

    /** The done criterion: a water wheel's reported speed turns its axles; the debug readout names it. */
    @Test
    void waterWheelTurnsItsAxles() {
        WaterWheelNetwork wheels = RotationSources.waterWheel(networks);
        Block wheel = world.getBlockAt(0, 64, 0);
        blocks.place(wheel, BlockStateRef.of(WHEEL).with("axis", "x"), true);
        Block a1 = axle(1, 64, 0, "x");
        Block a2 = axle(2, 64, 0, "x");
        assertThat(report(a2).target().network()).isEmpty();

        wheels.source(world.getUID(), RotationDevices.pos(wheel), ShaftAxis.X, -WaterWheel.MAX_SPEED);
        RotationService.Report r = report(a2);
        assertThat(r.network()).hasSize(3);
        assertThat(r.target().rpm()).isCloseTo(-15f, Offset.offset(1e-4f));
        assertThat(r.target().incoming().orElseThrow().direction()).isEqualTo(Direction.EAST);
        assertThat(RotationModule.reply(r).details()).anySatisfy(l -> assertThat(l).contains("water_wheel"));
        long id = r.target().network().getAsLong();

        // a speed change keeps the network id
        wheels.source(world.getUID(), RotationDevices.pos(wheel), ShaftAxis.X, WaterWheel.MAX_SPEED / 2);
        RotationService.Report after = report(a1);
        assertThat(after.target().network()).hasValue(id);
        assertThat(after.target().rpm()).isCloseTo(7.5f, Offset.offset(1e-4f));

        wheels.remove(world.getUID(), RotationDevices.pos(wheel));
        assertThat(report(a1).target().network()).isEmpty();
        assertThat(breaks).isEmpty();
    }

    /** A windmill drives its line; back to its axle (CustomBlocks.place, no device placement) the axle is re-added. */
    @Test
    void windmillBackToAxleIsReAdded() {
        WindmillNetwork mills = RotationSources.windmill(networks, (w, p) -> paper.reAddAfterSource(w, p, index, kinds));
        Block mill = world.getBlockAt(0, 64, 0);
        blocks.place(mill, BlockStateRef.of(WINDMILL).with("axis", "z").with("count", "1"), true);
        Block a1 = axle(0, 64, 1, "z");
        float speed = (float) (2 * Math.PI) / 80f;
        mills.source(world.getUID(), RotationDevices.pos(mill), ShaftAxis.Z, speed);
        assertThat(report(a1).target().incoming().orElseThrow().speed()).isEqualTo(speed);

        // the last blade comes out: WindmillDevices puts the axle back, the windmill's removal leaves the network
        blocks.place(mill, BlockStateRef.of(AXLE).with("axis", "z"), true);
        mills.remove(world.getUID(), RotationDevices.pos(mill));
        assertThat(scheduler.delays).contains(1L);
        RotationService.Report r = report(mill);
        assertThat(r.target().block()).isEqualTo(dev.tfp.core.rotation.MechanicalBlock.AXLE);
        assertThat(r.network()).hasSize(1); // the plain line: no source, disconnected
        assertThat(report(a1).target().network()).isEmpty();
        assertThat(index.positions(mill.getChunk())).contains(RotationDevices.pos(mill));

        // a windmill that is broken (air) re-adds nothing
        Block other = world.getBlockAt(5, 64, 5);
        mills.remove(world.getUID(), RotationDevices.pos(other));
        assertThat(networks.report(world.getUID(), RotationDevices.pos(other)).join()).isEmpty();
    }

    /** TFC destroyIfInvalid on a water wheel whose source was rejected: it drops itself and is removed. */
    @Test
    void rejectedWaterWheelBreaks() {
        Block motor = world.getBlockAt(0, 64, 0);
        blocks.place(motor, BlockStateRef.of(RotationBlockStates.MOTOR).with("axis", "x"), true);
        store.data.put(FakeCustomBlocks.at(motor), new DataMap());
        devices.placed(motor, player, EquipmentSlot.HAND, ItemStack.empty());
        axle(1, 64, 0, "x");
        Block wheel = world.getBlockAt(2, 64, 0);
        blocks.place(wheel, BlockStateRef.of(WHEEL).with("axis", "x"), true);
        RotationSources.waterWheel(networks).source(world.getUID(), RotationDevices.pos(wheel), ShaftAxis.X, 0.01f);
        assertThat(breaks).containsExactly(RotationDevices.pos(wheel));
        paper.breakAt(world.getUID(), RotationDevices.pos(wheel));
        assertThat(blocks.stateAt(wheel)).isEmpty();
        assertThat(world.getEntities()).anySatisfy(e -> assertThat(e).isInstanceOf(org.bukkit.entity.Item.class));
    }

    /** The drops of TFC's loot tables: the wheel itself, the windmill's axle, nothing for the motor. */
    @Test
    void dropsOfInvalidBlocks() {
        assertThat(PaperRotationWorld.dropOf(BlockStateRef.of(WHEEL))).contains(java.util.Optional.of(WHEEL));
        assertThat(PaperRotationWorld.dropOf(BlockStateRef.of(WINDMILL).with("axis", "x").with("count", "2")).orElseThrow()
            .orElseThrow()).startsWith("tfp:wood/axle/oak");
        assertThat(PaperRotationWorld.dropOf(BlockStateRef.of(RotationBlockStates.MOTOR))).contains(java.util.Optional.empty());
        assertThat(PaperRotationWorld.dropOf(BlockStateRef.of("tfp:rock/raw/granite"))).isEmpty();
    }
}
