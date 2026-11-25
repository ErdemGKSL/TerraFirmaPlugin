/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.triphammer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-MECH-07 on MockBukkit with fakes of the CraftEngine adapter: a hammer goes in by a right click, the bladed axle above (a
 * real rotation network: creative motor and bladed axle) drives strikes on the metal anvil in front through its remote work,
 * each hit the anvil takes damages the hammer, a rotation the wrong way breaks the trip hammer. Stand-ins: an iron pickaxe is
 * the metal hammer of the trip hammers tag; the anvil's remote work is a recording {@link AnvilRemote}.
 */
class TripHammerMockBukkitTest extends MockServerTest {
    static final String BLADED = "tfp:wood/bladed_axle/oak";
    static final String MOTOR = "tfp:creative_motor";
    static final String ANVIL = "tfp:metal/anvil/bronze";
    static final String ROCK_ANVIL = "tfp:rock/anvil/granite";

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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(TripHammerBlocks.ID, BLADED, MOTOR, ANVIL, ROCK_ANVIL);
    private final MemoryStore store = new MemoryStore();
    private final InlineScheduler scheduler = new InlineScheduler();
    private final List<Block> hits = new ArrayList<>();
    private final List<Block> breaks = new ArrayList<>();
    private final List<ForgeStep> remote = new ArrayList<>();
    private boolean anvilTakes = true;
    private RotationService networks;
    private TripHammerService service;
    private TripHammerChunkIndex index;
    private DeviceService devices;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("hammer");
        networks = new RotationService(Runnable::run, (w, pos) -> { }, Logger.getLogger("test"));
        service = new TripHammerService(scheduler, blocks, () -> Optional.of(networks), () -> (anvil, step, movement, force) -> {
            assertThat(movement).isEqualTo(12);
            assertThat(force).isTrue();
            assertThat(blocks.stateAt(anvil).orElseThrow().id()).isEqualTo(ANVIL);
            remote.add(step);
            return anvilTakes;
        }, new TripHammerService.Effects() {
            @Override
            public void hit(Block hammer) {
                hits.add(hammer);
            }

            @Override
            public void itemBroke(Block hammer) {
                breaks.add(hammer);
            }

            @Override
            public ItemStack damage(ItemStack stack) {
                return HammerDamage.damageOnce(stack, 0);
            }
        });
        index = new TripHammerChunkIndex(new PdcChunkDataStore());
        TripHammerDevice device = new TripHammerDevice(service, index, blocks, scheduler,
            s -> s.getType() == Material.IRON_PICKAXE, (b, s) -> blocks.place(b, s, false));
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        DeviceKind<TripHammerDevice.Nothing> kind = devices.register(device.kind());
        service.bind(devices, kind);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** Hammer at (0, 64, 0) facing east, a bronze anvil east of it, a bladed axle (axis z) above it, a motor north of the axle. */
    private Block layout(String axleHand, int step) {
        Block hammer = world.getBlockAt(0, 64, 0);
        blocks.place(hammer, BlockStateRef.of(TripHammerBlocks.ID).with("facing", "east"), true);
        store.data.put(FakeCustomBlocks.at(hammer), new DataMap());
        devices.placed(hammer, player, EquipmentSlot.HAND, ItemStack.empty());
        blocks.place(world.getBlockAt(1, 64, 0), BlockStateRef.of(ANVIL).with("facing", "north"), true);
        blocks.place(world.getBlockAt(0, 65, 0), BlockStateRef.of(BLADED).with("axis", "z"), true);
        // the motor (axis z) at z=-1 drives the bladed axle at z=0 south (positive hand) at its step's speed
        networks.put(world.getUID(), new BlockPos(0, 65, -1), MechanicalBlock.State.motor(RotationAxis.Z, step));
        networks.put(world.getUID(), new BlockPos(0, 65, 0), MechanicalBlock.State.axle(MechanicalBlock.BLADED_AXLE, RotationAxis.Z));
        assertThat(networks.rotation(world.getUID(), new BlockPos(0, 65, 0)).orElseThrow().direction())
            .isEqualTo(Direction.valueOf(axleHand));
        return hammer;
    }

    private ItemStack hammerItem(int damage) {
        ItemStack s = new ItemStack(Material.IRON_PICKAXE);
        Damageable d = (Damageable) s.getItemMeta();
        d.setDamage(damage);
        s.setItemMeta(d);
        return s;
    }

    private void insert(Block hammer, ItemStack item) {
        player.getInventory().setItem(EquipmentSlot.HAND, item);
        devices.use(hammer, player, EquipmentSlot.HAND, item);
    }

    private ItemStack slot(Block hammer) {
        return devices.inspect(hammer).flatMap(DeviceService.Inspection::record)
            .map(r -> StackCodec.PAPER.decode(r.slot(0))).orElseThrow();
    }

    private void runTicks(int ticks) {
        for (int t = 0; t < ticks; t += TripHammerService.PERIOD) {
            new ArrayList<>(scheduler.repeating).forEach(Runnable::run);
        }
    }

    @Test
    void aHammerGoesInByHandOnce() {
        Block hammer = layout("SOUTH", 8);
        // not a trip hammer item: passes
        insert(hammer, new ItemStack(Material.STICK));
        assertThat(StackCodec.isEmpty(slot(hammer))).isTrue();
        ItemStack two = new ItemStack(Material.IRON_PICKAXE);
        insert(hammer, two);
        assertThat(slot(hammer).getType()).isEqualTo(Material.IRON_PICKAXE);
        assertThat(slot(hammer).getAmount()).isEqualTo(1);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getType()).isEqualTo(Material.AIR);
        // the slot is full: a second hammer stays in the hand
        ItemStack another = new ItemStack(Material.IRON_PICKAXE);
        insert(hammer, another);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getType()).isEqualTo(Material.IRON_PICKAXE);
        assertThat(service.running()).isEqualTo(1);
        assertThat(index.positions(hammer.getChunk())).containsExactly(new BlockPos(0, 64, 0));
    }

    /**
     * The done criterion off-server: a facing-east hammer under a bladed axle turning south (the facing's clockwise side) strikes
     * the bronze anvil in front with TFC's light hit about once per turn; each hit the anvil takes costs the hammer one point.
     */
    @Test
    void theBladedAxleDrivesStrikesOnTheAnvil() {
        Block hammer = layout("SOUTH", 8);
        insert(hammer, hammerItem(0));
        runTicks(400);
        // 30 rpm = one turn per 40 ticks, the 0.8-turn cooldown never skips one, the start cooldown takes the first 10 ticks
        assertThat(remote).hasSizeBetween(9, 10).containsOnly(ForgeStep.HIT_LIGHT);
        assertThat(hits).hasSize(remote.size());
        assertThat(service.strikes(world.getUID(), new BlockPos(0, 64, 0))).isEqualTo(remote.size());
        assertThat(((Damageable) slot(hammer).getItemMeta()).getDamage()).isEqualTo(remote.size());
        // a hit the anvil refuses (TFC workRemotely false: nothing to work) leaves the hammer as it is
        anvilTakes = false;
        int before = remote.size();
        runTicks(200);
        assertThat(remote.size()).isGreaterThan(before);
        assertThat(((Damageable) slot(hammer).getItemMeta()).getDamage()).isEqualTo(before);
    }

    @Test
    void theHammerWearsOutAndStops() {
        Block hammer = layout("SOUTH", 8);
        insert(hammer, hammerItem(Material.IRON_PICKAXE.getMaxDurability() - 2));
        runTicks(400);
        assertThat(remote).hasSize(2);
        assertThat(breaks).containsExactly(hammer);
        assertThat(StackCodec.isEmpty(slot(hammer))).isTrue();
    }

    @Test
    void noAxleNoMetalAnvilNoItemNoStrike() {
        Block hammer = layout("SOUTH", 8);
        runTicks(200); // no item
        assertThat(remote).isEmpty();
        insert(hammer, hammerItem(0));
        blocks.place(world.getBlockAt(1, 64, 0), BlockStateRef.of(ROCK_ANVIL), true);
        runTicks(200); // a rock anvil is no AnvilBlock
        assertThat(remote).isEmpty();
        assertThat(hits).isEmpty();
        blocks.place(world.getBlockAt(1, 64, 0), BlockStateRef.of(ANVIL), true);
        blocks.remove(world.getBlockAt(0, 65, 0));
        runTicks(200); // no bladed axle above
        assertThat(remote).isEmpty();
    }

    /** TFC: rotation.positiveDirection() != FACING.getClockWise() destroys the hammer. */
    @Test
    void theWrongHandBreaksTheTripHammer() {
        Block hammer = world.getBlockAt(0, 64, 0);
        layout("SOUTH", 8);
        blocks.place(hammer, BlockStateRef.of(TripHammerBlocks.ID).with("facing", "west"), true); // clockwise: north
        insert(hammer, hammerItem(0));
        runTicks(200);
        assertThat(blocks.stateAt(hammer)).isEmpty();
        assertThat(remote).isEmpty();
        assertThat(service.running()).isZero();
    }

    @Test
    void removalStopsAndChunkLoadRestarts() {
        Block hammer = layout("SOUTH", 8);
        service.stop(world.getUID(), new BlockPos(0, 64, 0));
        assertThat(service.running()).isZero();
        TripHammerModule.chunkLoaded(hammer.getChunk(), blocks, index, service);
        assertThat(service.running()).isEqualTo(1);
        DataMap last = store.data.get(FakeCustomBlocks.at(hammer));
        blocks.remove(hammer);
        devices.removed(world, 0, 64, 0, TripHammerBlocks.ID, last);
        assertThat(service.running()).isZero();
        assertThat(index.positions(hammer.getChunk())).isEmpty();
    }

    /** TFC getStateForPlacement: the hammer faces the player (the opposite of the look). */
    @Test
    void placementFacesThePlayer() {
        Block hammer = world.getBlockAt(5, 64, 5);
        blocks.place(hammer, BlockStateRef.of(TripHammerBlocks.ID), true);
        TripHammerDevice device = new TripHammerDevice(service, index, blocks, scheduler, s -> true,
            (b, s) -> blocks.place(b, s, false));
        device.placed(hammer, Direction.NORTH);
        assertThat(blocks.stateAt(hammer).orElseThrow().properties()).containsEntry("facing", "south");
        assertThat(TripHammerBlocks.horizontal(90f)).isEqualTo(Direction.WEST);
        assertThat(TripHammerBlocks.isMetalAnvil(BlockStateRef.of(ANVIL))).isTrue();
        assertThat(TripHammerBlocks.isMetalAnvil(BlockStateRef.of(ROCK_ANVIL))).isFalse();
        assertThat(TripHammerBlocks.isBladedAxle(BlockStateRef.of(BLADED))).isTrue();
        assertThat(TripHammerBlocks.isBladedAxle(BlockStateRef.of("tfp:wood/axle/oak"))).isFalse();
    }
}
