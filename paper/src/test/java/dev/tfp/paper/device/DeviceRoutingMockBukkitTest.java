/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.CatchUp;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
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
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-02: the device router on MockBukkit, with fakes of our adapter interfaces in place of CraftEngine (the
 * {@code tfp:device} behaviour calls exactly these router methods). Covers use routing, catch-up once with the elapsed
 * ticks, inventory drop on break, placement and attack from Bukkit events, and untouched unreadable data.
 */
class DeviceRoutingMockBukkitTest extends MockServerTest {
    /** In-memory {@link DeviceStore}: a map per position; only positions with a "block entity" answer. */
    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();
        int writes;

        void addBlockEntity(Block block) {
            data.put(FakeCustomBlocks.at(block), new DataMap());
        }

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
            writes++;
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    /** MockBukkit cannot serialise every meta: slots hold an index into this table. */
    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

        @Override
        public ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }

    /** A kind that records every catch-up and every removal. */
    record Counter(long ticks) {
    }

    static final VersionedCodec<Counter> COUNTER = VersionedCodec.<Counter>builder("counter", 1)
            .build((c, out) -> out.putLong("t", c.ticks()), in -> new Counter(in.requireLong("t")));

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(ProbeDevice.ID, "tfp:device/counter/oak");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicLong now = new AtomicLong(1_000);
    private final List<long[]> advances = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private DeviceService service;
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("devices");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, now::get, stacks, Logger.getLogger("test"));
        service.register(ProbeDevice.kind());
        service.register(DeviceKind.builder("tfp:device/counter", COUNTER, new Counter(0))
                .slots(1)
                .advance((rec, from, to) -> {
                    advances.add(new long[] {from, to});
                    return rec.withState(new Counter(rec.state().ticks() + to - from));
                })
                .handler(new DeviceHandler<>() {
                    @Override
                    public DeviceResult<Counter> use(DeviceContext<Counter> ctx) {
                        events.add("use@" + ctx.now());
                        return DeviceResult.consume();
                    }

                    @Override
                    public DeviceResult<Counter> attack(DeviceContext<Counter> ctx) {
                        events.add("attack:" + ctx.player().getName());
                        return DeviceResult.pass();
                    }

                    @Override
                    public DeviceResult<Counter> placed(DeviceContext<Counter> ctx) {
                        events.add("placed:" + ctx.player().getName() + ":" + ctx.hand() + ":" + ctx.item().getType());
                        return DeviceResult.pass();
                    }

                    @Override
                    public DeviceResult<Counter> neighbourChanged(DeviceContext<Counter> ctx) {
                        events.add("neighbour");
                        return DeviceResult.pass();
                    }

                    @Override
                    public void removed(DeviceContext<Counter> ctx) {
                        events.add("removed:" + ctx.record().state().ticks());
                    }
                })
                .build());
    }

    private Block device(String id, int x) {
        Block block = world.getBlockAt(x, 64, 0);
        blocks.place(block, BlockStateRef.of(id), false);
        store.addBlockEntity(block);
        return block;
    }

    private <S> DeviceRecord<S> stored(Block block, DeviceKind<S> kind) {
        return kind.codec().decode(store.read(block).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private DeviceKind<ProbeDevice.State> probe() {
        return (DeviceKind<ProbeDevice.State>) service.kinds().find(ProbeDevice.ID).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private DeviceKind<Counter> counter() {
        return (DeviceKind<Counter>) service.kinds().find("tfp:device/counter/oak").orElseThrow();
    }

    @Test
    void useIsRoutedAndStampsThenCatchesUpOnceWithTheElapsedTicks() {
        Block block = device("tfp:device/counter/oak", 0);
        PlayerMock player = server.addPlayer();

        assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(advances).isEmpty(); // first access: TFC's MIN_VALUE stamp, no update
        assertThat(stored(block, counter()).lastUpdateTick()).isEqualTo(1_000);

        now.set(1_750);
        assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(advances).hasSize(1);
        assertThat(advances.getFirst()).containsExactly(1_000, 1_750);
        DeviceRecord<Counter> rec = stored(block, counter());
        assertThat(rec.state()).isEqualTo(new Counter(750));
        assertThat(rec.lastUpdateTick()).isEqualTo(1_750);
        assertThat(events).containsExactly("use@1000", "use@1750");

        // same tick again: no advance, nothing new to write
        int writes = store.writes;
        assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(advances).hasSize(1);
        assertThat(store.writes).isEqualTo(writes);
    }

    @Test
    void useWithItemFillsTheInventoryAndBreakDropsIt() {
        Block block = device(ProbeDevice.ID, 2);
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.COBBLESTONE, 5));

        assertThat(service.use(block, player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand())).isTrue();
        assertThat(service.use(block, player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand())).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(3);
        DeviceInventory<ProbeDevice.State> inv = new DeviceInventory<>(probe(), stored(block, probe()), stacks);
        assertThat(inv.get(0).getType()).isEqualTo(Material.COBBLESTONE);
        assertThat(inv.get(0).getAmount()).isEqualTo(2);

        // empty hand: the plain use counts the click
        assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(stored(block, probe()).state().uses()).isEqualTo(1);

        // the block is broken: CraftEngine hands the last record to the router; TFC ejectInventory
        DataMap last = store.read(block).orElseThrow();
        blocks.remove(block);
        now.set(5_000);
        service.removed(world, block.getX(), block.getY(), block.getZ(), ProbeDevice.ID, last);
        List<Item> drops = List.copyOf(world.getEntitiesByClass(Item.class));
        assertThat(drops).hasSize(1);
        assertThat(drops.getFirst().getItemStack().getType()).isEqualTo(Material.COBBLESTONE);
        assertThat(drops.getFirst().getItemStack().getAmount()).isEqualTo(2);
        assertThat(drops.getFirst().getLocation().getX()).isEqualTo(2.5);
        assertThat(drops.getFirst().getPickupDelay()).isZero(); // TFC new ItemEntity: no pickup delay
    }

    /** A kind that records which use callbacks ran, with which hand and whether the stack was empty. */
    private Block handsDevice(List<String> calls, int x) {
        blocks.known("tfp:device/hands");
        service.register(DeviceKind.builder("tfp:device/hands", COUNTER, new Counter(0))
                .handler(new DeviceHandler<>() {
                    @Override
                    public DeviceResult<Counter> useWithItem(DeviceContext<Counter> ctx) {
                        calls.add("withItem:" + ctx.hand() + ":" + (ctx.item().isEmpty() ? "empty" : ctx.item().getType()));
                        return DeviceResult.pass();
                    }

                    @Override
                    public DeviceResult<Counter> use(DeviceContext<Counter> ctx) {
                        calls.add("use:" + ctx.hand());
                        return DeviceResult.pass();
                    }
                })
                .build());
        return device("tfp:device/hands", x);
    }

    @Test
    void useWithItemRunsForEmptyHandsAndUseOnlyForTheMainHand() {
        List<String> calls = new ArrayList<>();
        Block block = handsDevice(calls, 20);
        PlayerMock player = server.addPlayer();

        // vanilla: useItemOn with the (empty) stack, then useWithoutItem for the main hand only
        service.use(block, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(calls).containsExactly("withItem:HAND:empty", "use:HAND");
        calls.clear();
        // the off-hand packet of the same click: useItemOn only, use never runs a second time
        service.use(block, player, EquipmentSlot.OFF_HAND, ItemStack.empty());
        assertThat(calls).containsExactly("withItem:OFF_HAND:empty");
        calls.clear();
        service.use(block, player, EquipmentSlot.OFF_HAND, new ItemStack(Material.STICK));
        assertThat(calls).containsExactly("withItem:OFF_HAND:STICK");
        calls.clear();
        service.use(block, player, EquipmentSlot.HAND, new ItemStack(Material.STICK));
        assertThat(calls).containsExactly("withItem:HAND:STICK", "use:HAND");
    }

    @Test
    void offHandClickDoesNotRepeatTheProbesPlainUse() {
        Block block = device(ProbeDevice.ID, 22);
        PlayerMock player = server.addPlayer();
        assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(service.use(block, player, EquipmentSlot.OFF_HAND, ItemStack.empty())).isFalse();
        assertThat(stored(block, probe()).state().uses()).isEqualTo(1);
    }

    @Test
    void creationStampsAFreshRecordOfTheKind() {
        now.set(4_242);
        DataMap created = service.created("tfp:device/counter/oak").orElseThrow();
        DeviceRecord<Counter> rec = counter().codec().decode(created);
        assertThat(rec.lastUpdateTick()).isEqualTo(4_242);
        assertThat(rec.state()).isEqualTo(new Counter(0));
        assertThat(service.created("tfp:not_a_device")).isEmpty();
    }

    @Test
    void removalCatchesUpBeforeTheHandlerAndNoopKindsDropNothing() {
        Block block = device("tfp:device/counter/oak", 4);
        service.use(block, server.addPlayer(), EquipmentSlot.HAND, ItemStack.empty());
        DataMap last = store.read(block).orElseThrow();
        now.set(1_400);
        service.removed(world, block.getX(), block.getY(), block.getZ(), "tfp:device/counter/oak", last);
        assertThat(events).endsWith("removed:400");
        assertThat(world.getEntitiesByClass(Item.class)).isEmpty();
    }

    @Test
    void placementAndAttackComeFromBukkitEvents() {
        ServerScheduler scheduler = new ServerScheduler(plugin, 1);
        try {
            server.getPluginManager().registerEvents(new DeviceListener(service, scheduler), plugin);
            Block block = device("tfp:device/counter/oak", 6);
            PlayerMock player = server.addPlayer();
            server.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0),
                    new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND));
            assertThat(events).isEmpty(); // runs on the next tick, after CraftEngine stored the block
            server.getScheduler().performOneTick();
            // TFC setPlacedBy gets the placed stack: copied during the event
            assertThat(events).containsExactly("placed:" + player.getName() + ":HAND:STONE");
            assertThat(stored(block, counter()).lastUpdateTick()).isEqualTo(1_000);

            server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                    ItemStack.empty(), block, org.bukkit.block.BlockFace.UP));
            assertThat(events).endsWith("attack:" + player.getName());
            int seen = events.size();

            // vanilla calls Block.attack neither for a refused click nor in creative
            PlayerInteractEvent denied = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, ItemStack.empty(),
                    block, org.bukkit.block.BlockFace.UP);
            denied.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            server.getPluginManager().callEvent(denied);
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                    ItemStack.empty(), block, org.bukkit.block.BlockFace.UP));
            assertThat(events).hasSize(seen);

            service.neighbourChanged(block);
            assertThat(events).endsWith("neighbour");
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void nonDevicesPassAndUnreadableDataIsLeftUntouched() {
        PlayerMock player = server.addPlayer();
        Block vanilla = world.getBlockAt(10, 64, 0);
        assertThat(service.use(vanilla, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        assertThat(service.inspect(vanilla)).isEmpty();

        // a custom block of a device kind without the tfp:device block entity
        Block noEntity = world.getBlockAt(11, 64, 0);
        blocks.place(noEntity, BlockStateRef.of(ProbeDevice.ID), false);
        assertThat(service.use(noEntity, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        assertThat(service.inspect(noEntity).orElseThrow().problem()).contains("no device block entity");

        Block future = device(ProbeDevice.ID, 12);
        DataMap newer = new DataMap().putInt("v", 99).putString("from", "a newer plugin");
        store.write(future, newer);
        int writes = store.writes;
        assertThat(service.use(future, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        assertThat(service.catchUp(future)).isEmpty();
        assertThat(store.writes).isEqualTo(writes);
        assertThat(store.read(future)).contains(newer);
        assertThat(service.inspect(future).orElseThrow().problem()).startsWith("unreadable");
    }

    @Test
    void catchUpHookAndInspection() {
        Block block = device("tfp:device/counter/oak", 14);
        assertThat(service.inspect(block).orElseThrow().problem()).isEqualTo("never written");
        assertThat(service.catchUp(block)).isPresent();
        now.set(3_000);
        DeviceRecord<?> rec = service.catchUp(block).orElseThrow();
        assertThat(rec.state()).isEqualTo(new Counter(2_000));
        DeviceService.Inspection i = service.inspect(block).orElseThrow();
        assertThat(i.kindId()).isEqualTo("tfp:device/counter");
        assertThat(DeviceModule.lines(i, service)).anyMatch(l -> l.startsWith("lastUpdateTick: 3000"));
    }

    @Test
    void kindsMatchTheLongestIdAndRefuseDuplicates() {
        DeviceKinds kinds = new DeviceKinds();
        DeviceKind<Counter> family = kinds.register(DeviceKind.builder("tfp:crucible", COUNTER, new Counter(0)).build());
        DeviceKind<Counter> special = kinds.register(
                DeviceKind.builder("tfp:crucible/special", COUNTER, new Counter(0)).build());
        assertThat(kinds.find("tfp:crucible")).containsSame(family);
        assertThat(kinds.find("tfp:crucible/plain")).containsSame(family);
        assertThat(kinds.find("tfp:crucible/special/a")).containsSame(special);
        assertThat(kinds.find("tfp:crucibles")).isEmpty();
        assertThat(kinds.find("other:crucible")).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> kinds.register(
                DeviceKind.builder("tfp:crucible", COUNTER, new Counter(0)).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inventoryFollowsTheSlotFilterAndLimits() {
        DeviceKind<Counter> kind = DeviceKind.builder("tfp:device/filtered", COUNTER, new Counter(0))
                .slots(2)
                .slotLimit(16)
                .filter((slot, stack) -> slot == 1 || stack.getType() != Material.DIRT)
                .build();
        DeviceInventory<Counter> inv = new DeviceInventory<>(kind, kind.fresh(), stacks);
        assertThat(inv.canInsert(0, new ItemStack(Material.DIRT))).isFalse();
        DeviceInventory.Insert<Counter> dirt = inv.insert(new ItemStack(Material.DIRT, 40));
        assertThat(dirt.inventory().get(0).isEmpty()).isTrue();
        assertThat(dirt.inventory().get(1).getAmount()).isEqualTo(16); // slot limit
        assertThat(dirt.remainder().getAmount()).isEqualTo(24);
        DeviceInventory.Insert<Counter> stone = dirt.inventory().insert(new ItemStack(Material.STONE, 3));
        assertThat(stone.remainder().isEmpty()).isTrue();
        DeviceInventory.Extract<Counter> out = stone.inventory().extract(1, 10);
        assertThat(out.extracted().getAmount()).isEqualTo(10);
        assertThat(out.inventory().get(1).getAmount()).isEqualTo(6);
        assertThat(out.inventory().contents()).hasSize(2);
        assertThat(out.inventory().cleared().contents()).isEmpty();
        assertThat(CatchUp.elapsed(DeviceRecord.NEVER, 5)).isZero();
    }

    @Test
    void slotLimitsArePerSlotLikeTfcsAnvil() {
        // TFC AnvilBlockEntity.getSlotStackLimit: slot == SLOT_CATALYST (2) ? 64 : 1
        DeviceKind<Counter> anvil = DeviceKind.builder("tfp:device/anvil_like", COUNTER, new Counter(0))
                .slots(3)
                .slotLimit(slot -> slot == 2 ? 64 : 1)
                .build();
        assertThat(anvil.slotLimit(0)).isEqualTo(1);
        assertThat(anvil.slotLimit(2)).isEqualTo(64);
        DeviceInventory<Counter> inv = new DeviceInventory<>(anvil, anvil.fresh(), stacks);
        DeviceInventory.Insert<Counter> r = inv.insert(new ItemStack(Material.IRON_INGOT, 10));
        assertThat(r.inventory().get(0).getAmount()).isEqualTo(1);
        assertThat(r.inventory().get(1).getAmount()).isEqualTo(1);
        assertThat(r.inventory().get(2).getAmount()).isEqualTo(8);
        assertThat(r.remainder().isEmpty()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DeviceKind.builder("tfp:device/bad", COUNTER,
                new Counter(0)).slots(2).slotLimit(slot -> slot == 1 ? 0 : 64).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overfilledSlotsNeitherCreateItemsOnInsertNorGiveMoreThanAStack() {
        DeviceKind<Counter> kind = DeviceKind.builder("tfp:device/over", COUNTER, new Counter(0))
                .slots(1)
                .slotLimit(16)
                .build();
        // set() has no limit, as TFC setStackInSlot
        DeviceInventory<Counter> inv = new DeviceInventory<>(kind, kind.fresh(), stacks)
                .set(0, new ItemStack(Material.STONE, 40));
        DeviceInventory.Insert<Counter> in = inv.insert(0, new ItemStack(Material.STONE, 5));
        assertThat(in.remainder().getAmount()).isEqualTo(5); // unchanged: the remaining limit is <= 0
        assertThat(in.inventory().get(0).getAmount()).isEqualTo(40);

        DeviceInventory<Counter> huge = inv.set(0, new ItemStack(Material.STONE, 100));
        DeviceInventory.Extract<Counter> out = huge.extract(0, 100);
        assertThat(out.extracted().getAmount()).isEqualTo(64); // capped at getMaxStackSize
        assertThat(out.inventory().get(0).getAmount()).isEqualTo(36);
    }
}
