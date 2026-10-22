/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceRoutingMockBukkitTest.MemoryStore;
import dev.tfp.paper.device.DeviceRoutingMockBukkitTest.TableCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-18: {@link DeviceKind.RemoveBehaviour#SAVE} (TFC {@code InventoryRemoveBehavior.SAVE}). Breaking the device
 * ejects nothing; its own dropped item gets the record as item PDC {@code tfp:device}; placing that item restores state
 * and slots with {@code lastUpdateTick} stamped at placement. Other items and late drops are left alone.
 */
class DeviceSaveMockBukkitTest extends MockServerTest {
    static final String SAVER = "tfp:device/saver";

    /** The saver's block item is a cauldron here; every other stack is vanilla. */
    private static final CustomItems ITEMS = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return SAVER.equals(id);
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            return SAVER.equals(id) ? Optional.of(new ItemStack(Material.CAULDRON, amount)) : Optional.empty();
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return stack != null && stack.getType() == Material.CAULDRON ? Optional.of(SAVER) : Optional.empty();
        }
    };

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(SAVER, ProbeDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicLong now = new AtomicLong(1_000);
    private final AtomicLong nanos = new AtomicLong(0);
    private DeviceService service;
    private DeviceListener listener;
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("saves");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, now::get, stacks, ITEMS,
                Logger.getLogger("test"));
        service.nanos = nanos::get;
        service.register(ProbeDevice.kind(SAVER, DeviceKind.RemoveBehaviour.SAVE));
        service.register(ProbeDevice.kind());
        listener = new DeviceListener(service, new ServerScheduler(plugin, 1));
    }

    private Block device(String id, int x) {
        Block block = world.getBlockAt(x, 64, 0);
        blocks.place(block, BlockStateRef.of(id), false);
        store.addBlockEntity(block);
        return block;
    }

    @SuppressWarnings("unchecked")
    private DeviceKind<ProbeDevice.State> saver() {
        return (DeviceKind<ProbeDevice.State>) service.kinds().find(SAVER).orElseThrow();
    }

    private Item spawn(Block at, Material type) {
        Item item = world.dropItem(new Location(world, at.getX() + 0.6, at.getY() + 0.3, at.getZ() + 0.4),
                new ItemStack(type));
        listener.onItemSpawn(new ItemSpawnEvent(item));
        return item;
    }

    /** Fills the saver with 3 cobblestone and one plain click, then breaks it at tick 2000. */
    private Block filledAndBroken(int x) {
        Block block = device(SAVER, x);
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 3));
        for (int i = 0; i < 3; i++) {
            assertThat(service.use(block, player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand())).isTrue();
        }
        assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        DataMap last = store.read(block).orElseThrow();
        blocks.remove(block);
        now.set(2_000);
        service.removed(world, block.getX(), block.getY(), block.getZ(), SAVER, last);
        return block;
    }

    @Test
    void breakingEjectsNothingAndTheOwnDropCarriesTheCaughtUpRecord() {
        Block block = filledAndBroken(0);
        assertThat(world.getEntitiesByClass(Item.class)).isEmpty(); // TFC SAVE: no ejectInventory
        assertThat(service.pendingSaves()).isEqualTo(1);

        Item other = spawn(block, Material.STONE); // another drop at the same spot is not the device's item
        assertThat(DeviceService.savedRecord(other.getItemStack())).isEmpty();
        assertThat(service.pendingSaves()).isEqualTo(1);

        Item drop = spawn(block, Material.CAULDRON);
        assertThat(service.pendingSaves()).isZero();
        byte[] bytes = drop.getItemStack().getItemMeta().getPersistentDataContainer()
                .get(DeviceService.ITEM_KEY, PersistentDataType.BYTE_ARRAY);
        assertThat(bytes).isNotNull();
        DeviceRecord<ProbeDevice.State> saved = saver().codec().fromBytes(bytes);
        assertThat(saved.state().uses()).isEqualTo(1);
        assertThat(saved.state().simulatedTicks()).isEqualTo(1_000); // caught up to the removal (1000 -> 2000)
        assertThat(saved.lastUpdateTick()).isEqualTo(2_000);
        DeviceInventory<ProbeDevice.State> inv = new DeviceInventory<>(saver(), saved, stacks);
        assertThat(List.of(inv.get(0).getType(), inv.get(0).getAmount())).containsExactly(Material.COBBLESTONE, 3);

        // a second cauldron at the same spot gets nothing: one record, one item
        Item second = spawn(block, Material.CAULDRON);
        assertThat(DeviceService.savedRecord(second.getItemStack())).isEmpty();
    }

    @Test
    void placingTheSavedItemRestoresStateAndSlotsStampedAtPlacement() {
        Block broken = filledAndBroken(4);
        ItemStack item = spawn(broken, Material.CAULDRON).getItemStack();

        now.set(9_000); // 7000 ticks in item form: not simulated (TFC keeps no lastUpdateTick on the item)
        Block placed = device(SAVER, 8);
        DataMap restored = service.fromItem(SAVER, item).orElseThrow();
        assertThat(store.write(placed, restored)).isTrue();
        DeviceRecord<ProbeDevice.State> rec = saver().codec().decode(store.read(placed).orElseThrow());
        assertThat(rec.lastUpdateTick()).isEqualTo(9_000);
        assertThat(rec.state()).isEqualTo(new ProbeDevice.State(1_000, 1));
        assertThat(new DeviceInventory<>(saver(), rec, stacks).get(0).getAmount()).isEqualTo(3);

        // the next access catches up from the placement only
        now.set(9_500);
        PlayerMock player = server.addPlayer();
        assertThat(service.use(placed, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        rec = saver().codec().decode(store.read(placed).orElseThrow());
        assertThat(rec.state()).isEqualTo(new ProbeDevice.State(1_500, 2));
    }

    @Test
    void plainItemsUnknownBlocksAndUnreadableRecordsRestoreNothing() {
        assertThat(service.fromItem(SAVER, new ItemStack(Material.CAULDRON))).isEmpty();
        assertThat(service.fromItem(SAVER, ItemStack.empty())).isEmpty();
        Block broken = filledAndBroken(12);
        ItemStack item = spawn(broken, Material.CAULDRON).getItemStack();
        assertThat(service.fromItem("tfp:not_a_device", item)).isEmpty();

        ItemStack corrupt = new ItemStack(Material.CAULDRON);
        corrupt.editMeta(m -> m.getPersistentDataContainer().set(DeviceService.ITEM_KEY, PersistentDataType.BYTE_ARRAY,
                new byte[] {1, 2, 3}));
        assertThat(service.fromItem(SAVER, corrupt)).isEmpty();
    }

    @Test
    void aDropAfterTheWindowIsNotStampedAndDropKindsKeepEjecting() {
        Block block = filledAndBroken(16);
        nanos.addAndGet(DeviceService.SAVE_WINDOW_NANOS + 1);
        Item late = spawn(block, Material.CAULDRON);
        assertThat(DeviceService.savedRecord(late.getItemStack())).isEmpty();
        assertThat(service.pendingSaves()).isZero();

        // the DROP probe still ejects and leaves no pending save
        Block probe = device(ProbeDevice.ID, 20);
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));
        service.use(probe, player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand());
        DataMap last = store.read(probe).orElseThrow();
        blocks.remove(probe);
        service.removed(world, probe.getX(), probe.getY(), probe.getZ(), ProbeDevice.ID, last);
        assertThat(world.getEntitiesByClass(Item.class)).extracting(i -> i.getItemStack().getType())
                .contains(Material.DIAMOND);
        assertThat(service.pendingSaves()).isZero();
    }
}
