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
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
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
 * PAR-MECH-21 on MockBukkit: a crankshaft base joins a rotation network as a sink on its facing's counter-clockwise face, the
 * crank rotation service reports the rotation it receives and empties when the network goes, and the
 * base takes TFC's placement facing.
 */
class CrankSinksMockBukkitTest extends MockServerTest {
    static final String AXLE = "tfp:wood/axle/oak";
    static final String CRANK = RotationBlockStates.CRANKSHAFT;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(AXLE, RotationBlockStates.MOTOR, CRANK);
    private final RotationMockBukkitTest.MemoryStore store = new RotationMockBukkitTest.MemoryStore();
    private final RotationMockBukkitTest.Ops ops = new RotationMockBukkitTest.Ops();
    private RotationService networks;
    private DeviceService devices;
    private CrankSinks cranks;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("crank");
        ops.blocks = blocks;
        networks = new RotationService(Runnable::run, (w, pos) -> { }, Logger.getLogger("test"));
        RotationDevices kinds = new RotationDevices(networks, ops, new RotationChunkIndex(new PdcChunkDataStore()),
            s -> s.getType() == Material.BLAZE_ROD);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        kinds.kinds().forEach(devices::register);
        cranks = new CrankSinks(networks, blocks, (b, s) -> blocks.place(b, s, false), new InlineScheduler(),
            uid -> server.getWorld(uid));
        player = server.addPlayer();
    }

    private Block place(int x, int y, int z, String id, String axis) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.of(id).with("axis", axis), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty());
        return b;
    }

    private Block crank(int x, int y, int z, String facing, String part) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, BlockStateRef.of(CRANK).with("facing", facing).with("part", part), true);
        return b;
    }

    /** The in-game layout: motor (axis z, step 8) -> two axles -> a base facing east (sink face north). */
    private Block line() {
        Block motor = place(0, 64, 0, RotationBlockStates.MOTOR, "z");
        place(0, 64, 1, AXLE, "z");
        place(0, 64, 2, AXLE, "z");
        ops.face = Direction.EAST; // a z motor's interactable axis is x: east steps up
        for (int i = 0; i < 8; i++) {
            devices.use(motor, player, EquipmentSlot.HAND, ItemStack.empty());
        }
        return crank(0, 64, 3, "east", "base");
    }

    @Test
    void baseJoinsAsASinkAndReportsItsRotation() {
        Block base = line();
        BlockPos pos = RotationDevices.pos(base);
        // the first read from the base's region hands it to the network; the answer comes with the next read
        assertThat(cranks.rotation(world.getUID(), pos)).isEmpty();
        assertThat(cranks.known(world.getUID(), pos)).isTrue();
        Rotation r = cranks.rotation(world.getUID(), pos).orElseThrow();
        assertThat(r.direction()).isEqualTo(Direction.SOUTH);
        RotationService.Report report = networks.report(world.getUID(), pos).join().orElseThrow();
        assertThat(report.target().block()).isEqualTo(MechanicalBlock.CRANKSHAFT);
        assertThat(report.target().parent()).contains(Direction.NORTH);
        assertThat(report.target().rpm()).isCloseTo(30f, Offset.offset(1e-4f));
        assertThat(RotationModule.reply(report).details()).anySatisfy(l -> assertThat(l).contains("crankshaft"));
    }

    @Test
    void wrongFacingAndShaftAreNoMembers() {
        line();
        BlockPos pos = new BlockPos(0, 64, 3);
        crank(0, 64, 3, "west", "base"); // sink face south, away from the line
        cranks.refresh(world.getBlockAt(0, 64, 3));
        assertThat(cranks.rotation(world.getUID(), pos)).isEmpty();
        assertThat(networks.report(world.getUID(), pos).join().orElseThrow().target().network()).isEmpty();
        // a shaft piece is no node (TFC: only the base is added)
        Block shaft = crank(1, 64, 3, "east", "shaft");
        cranks.refresh(shaft);
        assertThat(cranks.known(world.getUID(), RotationDevices.pos(shaft))).isFalse();
        assertThat(networks.report(world.getUID(), RotationDevices.pos(shaft)).join()).isEmpty();
    }

    @Test
    void rotationEmptiesWhenTheNetworkGoesAndWhenTheBaseIsRemoved() {
        Block base = line();
        BlockPos pos = RotationDevices.pos(base);
        cranks.refresh(base);
        assertThat(cranks.rotation(world.getUID(), pos)).isPresent();
        // the axle next to it breaks: no network
        Block axle = world.getBlockAt(0, 64, 2);
        blocks.remove(axle);
        devices.removed(world, 0, 64, 2, AXLE, new DataMap());
        assertThat(cranks.rotation(world.getUID(), pos)).isEmpty();
        place(0, 64, 2, AXLE, "z");
        assertThat(cranks.rotation(world.getUID(), pos)).isPresent();
        // the base removed: the access hook's refresh takes it out
        blocks.remove(base);
        cranks.hook().after(base);
        assertThat(cranks.known(world.getUID(), pos)).isFalse();
        assertThat(networks.report(world.getUID(), pos).join()).isEmpty();
    }

    @Test
    void chunkUnloadRemovesTheBases() {
        Block base = line();
        cranks.refresh(base);
        BlockPos pos = RotationDevices.pos(base);
        cranks.chunkUnloaded(base.getChunk());
        assertThat(cranks.known(world.getUID(), pos)).isFalse();
        assertThat(networks.report(world.getUID(), pos).join()).isEmpty();
        // the chunk back: the powered bellows' next read (region thread of the base) re-adds it
        assertThat(cranks.rotation(world.getUID(), pos)).isEmpty();
        assertThat(cranks.rotation(world.getUID(), pos)).isPresent();
    }

    /** TFC getStateForPlacement: facing = the placer's horizontal look, clockwise; then it joins. */
    @Test
    void placementTakesTheClockwiseFacing() {
        line();
        Block base = crank(0, 64, 3, "north", "base"); // CraftEngine's default state
        cranks.placed(base, Direction.NORTH); // looking north: the base faces east
        assertThat(blocks.stateAt(base).orElseThrow().properties().get("facing")).isEqualTo("east");
        assertThat(cranks.known(world.getUID(), RotationDevices.pos(base))).isTrue();
        assertThat(cranks.rotation(world.getUID(), RotationDevices.pos(base))).isPresent();
        assertThat(CrankSinks.horizontal(180f)).isEqualTo(Direction.NORTH);
        assertThat(CrankSinks.horizontal(0f)).isEqualTo(Direction.SOUTH);
        assertThat(CrankSinks.horizontal(-90f)).isEqualTo(Direction.EAST);
        // another block placed: ignored
        Block axle = place(5, 64, 5, AXLE, "x");
        cranks.placed(axle, Direction.NORTH);
        assertThat(cranks.known(world.getUID(), RotationDevices.pos(axle))).isFalse();
    }

    @Test
    void parsedOnlyForTheBase() {
        assertThat(RotationBlockStates.block(CRANK)).contains(MechanicalBlock.CRANKSHAFT);
        assertThat(RotationBlockStates.KINDS).doesNotContain(CRANK);
        assertThat(RotationBlockStates.parse(BlockStateRef.of(CRANK).with("facing", "south").with("part", "base"), 0))
            .contains(MechanicalBlock.State.crankshaft(Direction.SOUTH));
        assertThat(RotationBlockStates.parse(BlockStateRef.of(CRANK).with("facing", "south").with("part", "shaft"), 0)).isEmpty();
        assertThat(RotationBlockStates.parse(BlockStateRef.of(CRANK).with("facing", "up").with("part", "base"), 0)).isEmpty();
        assertThat(PaperRotationWorld.dropOf(BlockStateRef.of(CRANK))).contains(Optional.of(CRANK));
    }
}
