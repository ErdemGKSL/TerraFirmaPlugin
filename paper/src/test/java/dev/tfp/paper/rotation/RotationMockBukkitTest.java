/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.rotation.CreativeMotor;
import dev.tfp.core.rotation.RotationWorld;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-MECH-01 on MockBukkit with fakes of the CraftEngine adapter: the device kinds hand block states to the network
 * actor (inline executor), the motor's plus and minus faces set its step, the hammer toggles gearbox faces, the clutch follows
 * redstone, a sixth axle is sent to break, the chunk index re-adds a chunk's blocks and the delayed break drops the block.
 * Stand-in: a blaze rod is the hammer.
 */
class RotationMockBukkitTest extends MockServerTest {
    static final String AXLE = "tfp:wood/axle/oak";
    static final String ENCASED = "tfp:wood/encased_axle/oak";
    static final String CLUTCH = "tfp:wood/clutch/oak";
    static final String GEAR_BOX = "tfp:wood/gear_box/oak";

    /** In-memory device store: positions with a "block entity". */
    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    /** Records the world side; {@link #face} is what the player's look ray hits. */
    static final class Ops implements RotationDevices.Ops {
        boolean signal;
        Direction face = Direction.SOUTH;
        final List<BlockStateRef> written = new ArrayList<>();
        int placeSounds;
        int failSounds;

        @Override
        public boolean hasNeighborSignal(Block block) {
            return signal;
        }

        @Override
        public void setState(Block block, BlockStateRef expected, BlockStateRef next) {
            written.add(next);
            blocks.place(block, next, false);
        }

        @Override
        public void placeSound(Block block) {
            placeSounds++;
        }

        @Override
        public void failSound(Block block) {
            failSounds++;
        }

        @Override
        public Optional<Direction> hitFace(Player player, Block block) {
            return Optional.of(face);
        }

        FakeCustomBlocks blocks;
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(AXLE, ENCASED, CLUTCH, GEAR_BOX, RotationBlockStates.MOTOR);
    private final MemoryStore store = new MemoryStore();
    private final Ops ops = new Ops();
    private final List<BlockPos> breaks = new ArrayList<>();
    private RotationService networks;
    private RotationDevices kinds;
    private DeviceService devices;
    private RotationChunkIndex index;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("rotation");
        ops.blocks = blocks;
        networks = new RotationService(Runnable::run, (w, pos) -> breaks.add(pos), Logger.getLogger("test"));
        index = new RotationChunkIndex(new PdcChunkDataStore());
        kinds = new RotationDevices(networks, ops, index, s -> s.getType() == Material.BLAZE_ROD);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        kinds.kinds().forEach(devices::register);
        player = server.addPlayer();
    }

    private Block place(int x, int y, int z, BlockStateRef state) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, state, true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap()); // CraftEngine creates the block entity
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty());
        return b;
    }

    private static BlockStateRef axis(String id, String axis) {
        return BlockStateRef.of(id).with("axis", axis);
    }

    private static BlockStateRef gearBox(Direction... on) {
        BlockStateRef s = BlockStateRef.of(GEAR_BOX);
        return RotationBlockStates.withFaces(s, java.util.Set.of(on));
    }

    private RotationService.Report report(Block b) {
        return networks.report(world.getUID(), RotationDevices.pos(b)).join().orElseThrow();
    }

    private void click(Block b, ItemStack held) {
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        devices.use(b, player, EquipmentSlot.HAND, held);
    }

    /** The done criterion: motor, five axles, a gearbox and the axle behind it, 30 RPM at step 8 everywhere. */
    @Test
    void creativeMotorDrivesAxleLineAndGearbox() {
        Block motor = place(0, 64, 0, axis(RotationBlockStates.MOTOR, "x"));
        for (int x = 1; x <= 5; x++) {
            place(x, 64, 0, axis(AXLE, "x"));
        }
        place(6, 64, 0, gearBox(Direction.WEST, Direction.SOUTH));
        Block out = place(6, 64, 1, axis(ENCASED, "z"));
        assertThat(report(out).target().rpm()).isZero();
        assertThat(report(out).network()).hasSize(8);

        ops.face = Direction.SOUTH; // x motor: the interactable axis is z; south is its plus face
        for (int i = 0; i < 10; i++) {
            click(motor, ItemStack.empty());
        }
        assertThat(store.read(motor).orElseThrow().getMap("state").getInt("step", -99)).isEqualTo(CreativeMotor.MAX_STEPS);
        RotationService.Report r = report(out);
        assertThat(r.target().rpm()).isCloseTo(30f, org.assertj.core.data.Offset.offset(1e-4f));
        assertThat(r.network()).allSatisfy(x -> assertThat(x.rpm()).isCloseTo(30f, org.assertj.core.data.Offset.offset(1e-4f)));
        assertThat(RotationModule.reply(r).details()).hasSize(8);

        ops.face = Direction.NORTH;
        click(motor, ItemStack.empty());
        assertThat(report(out).target().rpm()).isCloseTo(30f * 7 / 8, org.assertj.core.data.Offset.offset(1e-4f));
        assertThat(breaks).isEmpty();
    }

    /** GearBoxBlock.useItemOn: the hammer enables the hit face; a third axis is refused with the break sound. */
    @Test
    void hammerTogglesGearBoxFaces() {
        Block box = place(0, 64, 0, gearBox(Direction.NORTH, Direction.EAST));
        ItemStack hammer = new ItemStack(Material.BLAZE_ROD);
        ops.face = Direction.UP;
        click(box, hammer);
        assertThat(ops.failSounds).isEqualTo(1);
        assertThat(ops.written).isEmpty();
        ops.face = Direction.SOUTH;
        click(box, hammer);
        assertThat(ops.placeSounds).isEqualTo(1);
        assertThat(RotationBlockStates.faces(ops.written.getLast()))
            .containsExactlyInAnyOrder(Direction.NORTH, Direction.SOUTH, Direction.EAST);
        click(box, new ItemStack(Material.STICK));
        assertThat(ops.written).hasSize(1);
    }

    /** ClutchBlock: placed powered, it connects nothing; losing the signal joins the line again. */
    @Test
    void clutchFollowsRedstone() {
        place(0, 64, 0, axis(RotationBlockStates.MOTOR, "z"));
        ops.signal = true;
        Block clutch = place(0, 64, 1, axis(CLUTCH, "z").with("powered", "false"));
        assertThat(ops.written.getLast().properties()).containsEntry("powered", "true");
        Block axle = place(0, 64, 2, axis(AXLE, "z"));
        assertThat(report(axle).target().network()).isEmpty();
        ops.signal = false;
        devices.neighbourChanged(clutch);
        assertThat(ops.written.getLast().properties()).containsEntry("powered", "false");
        assertThat(report(axle).target().network()).isPresent();
    }

    /** AxleNode.MAX_AXLE_LENGTH: the sixth axle is sent to break; breaking it (device removal) clears it. */
    @Test
    void sixthAxleBreaks() {
        place(0, 64, 0, axis(RotationBlockStates.MOTOR, "x"));
        for (int x = 1; x <= 5; x++) {
            place(x, 64, 0, axis(AXLE, "x"));
        }
        Block sixth = place(6, 64, 0, axis(AXLE, "x"));
        assertThat(breaks).containsExactly(RotationDevices.pos(sixth));
        RotationWorld.Readout r = report(sixth).target();
        assertThat(r.invalid()).isTrue();
        assertThat(RotationModule.line(r)).contains("invalid");
    }

    /** The chunk index lists placed blocks; a chunk load re-adds them, an unload removes them. */
    @Test
    void chunkUnloadAndLoad() {
        PaperRotationWorld paper = new PaperRotationWorld(new InlineScheduler(), blocks, () -> null, new FakeCustomItems(),
            uid -> world);
        paper.bind(networks, devices);
        place(0, 64, 0, axis(RotationBlockStates.MOTOR, "y"));
        Block axle = place(0, 65, 0, axis(AXLE, "y"));
        assertThat(index.positions(axle.getChunk())).containsExactlyInAnyOrder(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0));
        paper.chunkUnloaded(axle.getChunk(), index);
        assertThat(networks.report(world.getUID(), RotationDevices.pos(axle)).join()).isEmpty();
        paper.chunkLoaded(axle.getChunk(), index, kinds);
        assertThat(report(axle).target().incoming().orElseThrow().direction()).isEqualTo(Direction.UP);
    }

    /** PAR-MECH-11: blocks in chunks that were loaded before the module enabled join their network without an interaction. */
    @Test
    void blocksLoadedBeforeEnableAreReAdded() {
        PaperRotationWorld paper = new PaperRotationWorld(new InlineScheduler(), blocks, () -> null, new FakeCustomItems(),
            uid -> world);
        paper.bind(networks, devices);
        place(0, 64, 0, axis(RotationBlockStates.MOTOR, "y"));
        Block axle = place(0, 65, 0, axis(AXLE, "y"));
        // the plugin restarted: a fresh network knows nothing, the chunk (and its index) is already loaded
        networks = new RotationService(Runnable::run, (w, pos) -> breaks.add(pos), Logger.getLogger("test"));
        RotationService fresh = networks;
        RotationDevices freshKinds = new RotationDevices(fresh, ops, index, s -> s.getType() == Material.BLAZE_ROD);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        assertThat(fresh.report(world.getUID(), RotationDevices.pos(axle)).join()).isEmpty();
        world.getChunkAt(axle.getChunk().getX(), axle.getChunk().getZ()); // loaded
        paper.bind(fresh, devices);
        paper.reAddLoaded(world, index, freshKinds);
        RotationService.Report r = fresh.report(world.getUID(), RotationDevices.pos(axle)).join().orElseThrow();
        assertThat(r.network()).hasSize(2);
        assertThat(r.target().incoming().orElseThrow().direction()).isEqualTo(Direction.UP);
    }

    /** RotatingBlockEntity.destroyIfInvalid: the block drops its item and is removed; the motor drops nothing. */
    @Test
    void delayedBreakDropsTheBlock() {
        InlineScheduler scheduler = new InlineScheduler();
        FakeCustomItems items = new FakeCustomItems().define(AXLE, n -> new ItemStack(Material.STICK, n));
        PaperRotationWorld paper = new PaperRotationWorld(scheduler, blocks, () -> null, items, uid -> world);
        paper.bind(networks, devices);
        Block axle = place(3, 64, 3, axis(AXLE, "x"));
        paper.breakAt(world.getUID(), RotationDevices.pos(axle));
        assertThat(scheduler.delays).containsExactly((long) PaperRotationWorld.DELAY_FOR_INVALID_IN_NETWORK);
        assertThat(blocks.stateAt(axle)).isEmpty();
        assertThat(world.getEntities()).anySatisfy(e -> assertThat(e).isInstanceOf(org.bukkit.entity.Item.class));
        // a block that is no longer a rotation block is left alone
        paper.breakAt(world.getUID(), new BlockPos(9, 64, 9));
        assertThat(scheduler.delays).hasSize(2);
    }
}
