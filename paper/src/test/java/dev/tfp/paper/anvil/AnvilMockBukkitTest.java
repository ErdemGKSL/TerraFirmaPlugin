/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-03 on MockBukkit, with fakes of the CraftEngine adapter and the world effects: the sneaking clicks of TFC's
 * {@code interactWithAnvil} (insert order, slot limits, extraction), the slot filters, the display view (tier, facing),
 * the hammer conversion of raw igneous rock, and the sneaking click routing of {@link AnvilListener}.
 *
 * <p>Stand-ins: iron pickaxe = hammer ({@code c:tools/hammer}), stone pickaxe = chisel, gunpowder = flux, gold ingot and
 * iron ingot = anything else.
 */
class AnvilMockBukkitTest extends MockServerTest {
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

    static final class Effects implements AnvilEffects {
        View shown;
        boolean hidden;
        int shows;
        final List<BlockStateRef> states = new ArrayList<>();

        @Override
        public void show(Block block, View view) {
            shown = view;
            shows++;
        }

        @Override
        public void hide(Block block) {
            hidden = true;
        }

        @Override
        public void state(Block block, BlockStateRef wanted) {
            states.add(wanted);
        }
    }

    private static final String COPPER = "tfp:metal/anvil/copper";
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(COPPER, "tfp:rock/anvil/granite",
        "tfp:rock/raw/granite", "tfp:rock/raw/limestone");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AnvilClicks clicks = new AnvilClicks();
    private DeviceService service;
    private World world;
    private Block anvil;
    private PlayerMock player;

    static boolean hammer(ItemStack s) {
        return s.getType() == Material.IRON_PICKAXE;
    }

    static boolean chisel(ItemStack s) {
        return s.getType() == Material.STONE_PICKAXE;
    }

    static boolean flux(ItemStack s) {
        return s.getType() == Material.GUNPOWDER;
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("anvil");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        AnvilDevice.Services services = new AnvilDevice.Services(AnvilMockBukkitTest::hammer, AnvilMockBukkitTest::flux, effects,
            clicks);
        service.register(AnvilDevice.kind(AnvilDevice.METAL_ID, services));
        service.register(AnvilDevice.kind(AnvilDevice.ROCK_ID, services));
        anvil = world.getBlockAt(0, 64, 0);
        blocks.place(anvil, BlockStateRef.of(COPPER).with("facing", "east"), true);
        store.data.put(FakeCustomBlocks.at(anvil), new DataMap());
        player = server.addPlayer();
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<AnvilState> record() {
        DeviceKind<AnvilState> kind = (DeviceKind<AnvilState>) service.kinds().find(COPPER).orElseThrow();
        return kind.codec().decode(store.read(anvil).orElseThrow());
    }

    private ItemStack slot(int slot) {
        return stacks.decode(record().slot(slot));
    }

    private void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, amount == 0 ? null : new ItemStack(type, amount));
    }

    private ItemStack held() {
        return player.getInventory().getItem(EquipmentSlot.HAND);
    }

    private boolean click() {
        return service.use(anvil, player, EquipmentSlot.HAND, held());
    }

    @Test
    void aClickWithoutSneakingOpensTheScreenLaterAndPassesNow() {
        hold(Material.IRON_INGOT, 1);
        assertThat(click()).isFalse();
        assertThat(effects.shows).isZero();
    }

    @Test
    void sneakingInsertsIntoFluxThenMainThenSecondOneItemAtATime() {
        player.setSneaking(true);
        hold(Material.IRON_INGOT, 3);
        assertThat(click()).isTrue();
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(slot(AnvilSlots.INPUT_MAIN).getAmount()).as("the slot holds one").isEqualTo(1);
        assertThat(held().getAmount()).isEqualTo(2);

        assertThat(click()).isTrue();
        assertThat(slot(AnvilSlots.INPUT_SECOND).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(held().getAmount()).isEqualTo(1);

        assertThat(click()).as("both inputs are full").isFalse();
        assertThat(held().getAmount()).isEqualTo(1);
        assertThat(effects.shown.main().getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(effects.shown.second().getType()).isEqualTo(Material.IRON_INGOT);
    }

    @Test
    void fluxGoesToItsOwnSlotAndStacksToSixtyFour() {
        player.setSneaking(true);
        hold(Material.GUNPOWDER, 40);
        assertThat(click()).isTrue();
        assertThat(slot(AnvilSlots.CATALYST).getAmount()).isEqualTo(40);
        assertThat(held().getType()).isEqualTo(Material.AIR);
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.AIR);
        hold(Material.GUNPOWDER, 40);
        assertThat(click()).isTrue();
        assertThat(slot(AnvilSlots.CATALYST).getAmount()).isEqualTo(64);
        assertThat(held().getAmount()).as("the rest stays in the hand").isEqualTo(16);
        assertThat(effects.shown.catalyst().getAmount()).isEqualTo(64);
    }

    @Test
    void aHammerInHandPassesAndIsNeverInserted() {
        player.setSneaking(true);
        hold(Material.IRON_PICKAXE, 1);
        assertThat(click()).as("welding is PAR-METAL-06").isFalse();
        assertThat(held().getType()).isEqualTo(Material.IRON_PICKAXE);
        for (int s = 0; s < AnvilSlots.COUNT; s++) {
            assertThat(slot(s).getType()).isEqualTo(Material.AIR);
        }
    }

    @Test
    void anEmptyHandTakesTheMainInputThenTheSecond() {
        player.setSneaking(true);
        hold(Material.IRON_INGOT, 2);
        click();
        click();
        hold(Material.AIR, 0);
        assertThat(click()).isTrue();
        assertThat(held().getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.AIR);
        assertThat(slot(AnvilSlots.INPUT_SECOND).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(effects.shown.main().getType()).isEqualTo(Material.AIR);

        hold(Material.AIR, 0);
        assertThat(click()).isTrue();
        assertThat(slot(AnvilSlots.INPUT_SECOND).getType()).isEqualTo(Material.AIR);

        hold(Material.AIR, 0);
        assertThat(click()).as("nothing left").isFalse();
    }

    @Test
    void anEmptyHandDoesNotTakeTheHammerOrTheFlux() {
        player.setSneaking(true);
        hold(Material.GUNPOWDER, 5);
        click();
        hold(Material.AIR, 0);
        assertThat(click()).isFalse();
        assertThat(slot(AnvilSlots.CATALYST).getAmount()).isEqualTo(5);
    }

    @Test
    void slotFiltersAreTfcs() {
        @SuppressWarnings("unchecked")
        DeviceKind<AnvilState> kind = (DeviceKind<AnvilState>) service.kinds().find(COPPER).orElseThrow();
        assertThat(kind.slots()).isEqualTo(4);
        assertThat(kind.filter().canInsert(AnvilSlots.HAMMER, new ItemStack(Material.IRON_PICKAXE))).isTrue();
        assertThat(kind.filter().canInsert(AnvilSlots.HAMMER, new ItemStack(Material.IRON_INGOT))).isFalse();
        assertThat(kind.filter().canInsert(AnvilSlots.CATALYST, new ItemStack(Material.GUNPOWDER))).isTrue();
        assertThat(kind.filter().canInsert(AnvilSlots.CATALYST, new ItemStack(Material.IRON_INGOT))).isFalse();
        assertThat(kind.filter().canInsert(AnvilSlots.INPUT_MAIN, new ItemStack(Material.DIRT))).isTrue();
        assertThat(kind.filter().canInsert(AnvilSlots.INPUT_SECOND, new ItemStack(Material.DIRT))).isTrue();
        assertThat(kind.slotLimit(AnvilSlots.CATALYST)).isEqualTo(64);
        assertThat(kind.slotLimit(AnvilSlots.HAMMER)).isEqualTo(1);
        assertThat(kind.removal()).isEqualTo(DeviceKind.RemoveBehaviour.DROP);
    }

    @Test
    void theViewCarriesTheTierAndTheFacingOfTheBlock() {
        player.setSneaking(true);
        hold(Material.IRON_INGOT, 1);
        click();
        assertThat(effects.shown.tier()).isEqualTo(1);
        assertThat(effects.shown.rotation()).as("facing east is rotation 3").isEqualTo(3);

        Block rock = world.getBlockAt(5, 64, 0);
        blocks.place(rock, BlockStateRef.of("tfp:rock/anvil/granite"), true);
        store.data.put(FakeCustomBlocks.at(rock), new DataMap());
        hold(Material.GOLD_INGOT, 1);
        assertThat(service.use(rock, player, EquipmentSlot.HAND, held())).isTrue();
        assertThat(effects.shown.tier()).as("a rock anvil is tier 0").isZero();
        assertThat(effects.shown.main().getType()).isEqualTo(Material.GOLD_INGOT);
    }

    @Test
    void theDisplaysGoWhenTheAnvilIsRemoved() {
        player.setSneaking(true);
        hold(Material.IRON_INGOT, 1);
        click();
        DataMap data = store.read(anvil).orElseThrow();
        blocks.remove(anvil);
        service.removed(world, 0, 64, 0, COPPER, data);
        assertThat(effects.hidden).isTrue();
        assertThat(world.getNearbyEntities(anvil.getLocation(), 2, 2, 2).stream()
            .filter(e -> e instanceof org.bukkit.entity.Item).count()).as("DROP spawns the slots").isEqualTo(1);
    }

    // ---- the listener ---------------------------------------------------------------------------------------

    private AnvilListener listener() {
        dev.tfp.paper.scheduler.Scheduler scheduler = (dev.tfp.paper.scheduler.Scheduler) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {dev.tfp.paper.scheduler.Scheduler.class}, (proxy, method, args) -> {
                if (method.getName().equals("isOwner")) {
                    return true;
                }
                for (Object a : args) {
                    if (a instanceof Runnable r) {
                        r.run();
                        return null;
                    }
                }
                return null;
            });
        return new AnvilListener(blocks, service, AnvilMockBukkitTest::hammer, AnvilMockBukkitTest::chisel, scheduler, effects,
            clicks);
    }

    private PlayerInteractEvent rightClick(Block block, BlockFace face, EquipmentSlot hand) {
        ItemStack item = player.getInventory().getItem(hand);
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block, face, hand);
    }

    @Test
    void aSneakingClickWithAnItemReachesTheDevice() {
        AnvilListener listener = listener();
        player.setSneaking(true);
        hold(Material.IRON_INGOT, 2);
        PlayerInteractEvent e = rightClick(anvil, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.IRON_INGOT);

        PlayerInteractEvent off = rightClick(anvil, BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.onInteract(off);
        assertThat(off.isCancelled()).as("the other hand of the same click").isTrue();
        assertThat(slot(AnvilSlots.INPUT_SECOND).getType()).as("is not used a second time").isEqualTo(Material.AIR);
    }

    @Test
    void aClickTheDeviceConsumedCancelsTheOtherHandsEventEvenWithoutTheListenersHelp() {
        AnvilListener listener = listener();
        player.setSneaking(true);
        hold(Material.IRON_INGOT, 1);
        click(); // main input (the device routed it, as CraftEngine does with both hands empty)
        hold(Material.AIR, 0);
        assertThat(click()).isTrue(); // the empty hand takes it back: consumed, marked
        PlayerInteractEvent off = rightClick(anvil, BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.onInteract(off);
        assertThat(off.isCancelled()).isTrue();
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aClickWithoutSneakingIsLeftToTheDeviceBehaviour() {
        AnvilListener listener = listener();
        hold(Material.IRON_INGOT, 2);
        PlayerInteractEvent e = rightClick(anvil, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).isFalse();
        assertThat(effects.shows).isZero();
    }

    private int rawAt = 10;

    private Block raw(String rock) {
        Block b = world.getBlockAt(rawAt, 64, 10);
        rawAt += 3;
        blocks.place(b, BlockStateRef.of("tfp:rock/raw/" + rock), true);
        b.getRelative(BlockFace.UP).setType(Material.AIR);
        return b;
    }

    @Test
    void aHammerOnTheTopOfRawIgneousRockMakesItsAnvil() {
        AnvilListener listener = listener();
        Block rock = raw("granite");
        hold(Material.IRON_PICKAXE, 1);
        PlayerInteractEvent e = rightClick(rock, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(blocks.stateAt(rock)).contains(BlockStateRef.of("tfp:rock/anvil/granite"));
    }

    @Test
    void theConversionNeedsAHammerNoChiselTopFaceAirAboveAndAnIgneousRock() {
        AnvilListener listener = listener();
        Block rock = raw("granite");

        hold(Material.IRON_INGOT, 1);
        listener.onInteract(rightClick(rock, BlockFace.UP, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rock)).contains(BlockStateRef.of("tfp:rock/raw/granite"));

        hold(Material.IRON_PICKAXE, 1);
        listener.onInteract(rightClick(rock, BlockFace.NORTH, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rock)).as("a side face").contains(BlockStateRef.of("tfp:rock/raw/granite"));

        player.getInventory().setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.STONE_PICKAXE));
        listener.onInteract(rightClick(rock, BlockFace.UP, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rock)).as("a chisel in the other hand").contains(BlockStateRef.of("tfp:rock/raw/granite"));
        player.getInventory().setItem(EquipmentSlot.OFF_HAND, null);

        rock.getRelative(BlockFace.UP).setType(Material.STONE);
        listener.onInteract(rightClick(rock, BlockFace.UP, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rock)).as("a block above").contains(BlockStateRef.of("tfp:rock/raw/granite"));
        rock.getRelative(BlockFace.UP).setType(Material.AIR);

        player.setSneaking(true);
        listener.onInteract(rightClick(rock, BlockFace.UP, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rock)).as("sneaking skips a block's use").contains(BlockStateRef.of("tfp:rock/raw/granite"));
        player.setSneaking(false);

        Block limestone = raw("limestone");
        listener.onInteract(rightClick(limestone, BlockFace.UP, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(limestone)).as("not igneous").contains(BlockStateRef.of("tfp:rock/raw/limestone"));

        listener.onInteract(rightClick(rock, BlockFace.UP, EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rock)).contains(BlockStateRef.of("tfp:rock/anvil/granite"));
    }

    @Test
    void aDeniedClickDoesNothing() {
        AnvilListener listener = listener();
        Block rock = raw("granite");
        hold(Material.IRON_PICKAXE, 1);
        PlayerInteractEvent e = rightClick(rock, BlockFace.UP, EquipmentSlot.HAND);
        e.setUseInteractedBlock(Event.Result.DENY);
        listener.onInteract(e);
        assertThat(blocks.stateAt(rock)).contains(BlockStateRef.of("tfp:rock/raw/granite"));
    }
}
