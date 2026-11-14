/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.core.gui.SlotLayout;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-HEAT-07: the firepit screen: layout, TFC's slot predicates and limits, cascade, thermometer, reloads, viewers. */
class FirepitScreensMockBukkitTest extends MockServerTest {
    /** Logs are fuel, beef is heatable, stone is neither. */
    static final FirepitSlotRules RULES = new FirepitSlotRules(s -> s.getType() == Material.OAK_LOG,
        s -> s.getType() == Material.BEEF);
    private static final int FUEL_TOP = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_INPUT);
    private static final int FUEL_BOTTOM = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_CONSUME);
    private static final int INPUT = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_ITEM_INPUT);
    private static final int OUT1 = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_OUTPUT_1);
    private static final int OUT2 = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_OUTPUT_2);

    private final AtomicLong now = new AtomicLong(100_000);
    private TickScheduler scheduler;
    private FirepitScreens screens;
    private PlayerMock player;

    /** A firepit in memory that records commits and cascades like TFC; {@code gone} makes it answer empty. */
    static final class FakeLink implements FirepitLink {
        FirepitSnapshot firepit;
        boolean gone;
        final List<List<ItemStack>> commits = new ArrayList<>();
        int closed;

        FakeLink(float temperature) {
            this.firepit = new FirepitSnapshot(temperature, FirepitSnapshot.emptySlots());
        }

        @Override
        public Optional<FirepitSnapshot> snapshot() {
            return gone ? Optional.empty() : Optional.of(firepit);
        }

        @Override
        public Optional<FirepitSnapshot> commit(List<ItemStack> slots) {
            commits.add(slots);
            if (gone) {
                return Optional.empty();
            }
            firepit = firepit.withSlots(FirepitScreenLayout.cascade(slots));
            return snapshot();
        }

        @Override
        public boolean isItemValid(int tfcSlot, ItemStack stack) {
            return RULES.isItemValid(tfcSlot, stack);
        }

        @Override
        public void closed(org.bukkit.entity.Player viewer) {
            closed++;
        }
    }

    @BeforeEach
    void setUp() {
        scheduler = new TickScheduler();
        GuiService gui = new GuiService(server, scheduler, now::get, true);
        screens = new FirepitScreens(gui, scheduler, Messages.fromJar(), HeatDisplayStyle.COLOR);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new FirepitGuiListener(screens), plugin);
        player = server.addPlayer("Bot");
    }

    private GuiSession open(FirepitLink link) {
        FirepitScreens.Opened o = screens.open(player, "firepit@0,64,0", link, p -> "Firepit");
        assertThat(o.ok()).isTrue();
        return o.session();
    }

    private InventoryClickEvent hotbarSwap(int raw, int hotbar) {
        InventoryClickEvent e = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, raw,
            ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, hotbar);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        InventoryType.SlotType st = raw < view.getTopInventory().getSize() ? InventoryType.SlotType.CONTAINER
            : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent e = new InventoryClickEvent(view, st, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    void theLayoutPutsTfcsSlotsOnA4RowChest() {
        assertThat(FirepitScreenLayout.CHEST.height()).as("TFC's 166 + 20").isEqualTo(FirepitScreenLayout.TFC_HEIGHT);
        assertThat(FirepitScreenLayout.DY).isEqualTo(2);
        // the fuel column lands exactly on chest column 0
        SlotLayout fuel = SlotLayout.of(FirepitScreenLayout.CHEST, FirepitScreenLayout.DY,
            FirepitScreenLayout.TFC_SLOTS.subList(0, 4));
        assertThat(fuel.chestSlots()).isEqualTo(FirepitScreenLayout.CHEST_SLOTS.subList(0, 4));
        ChestGeometry chest = FirepitScreenLayout.CHEST;
        // the input keeps its column (x 80); the outputs sit one column each side of it, centred under the input
        assertThat(chest.slotX(INPUT)).isEqualTo(80);
        assertThat(chest.slotX(OUT1) + chest.slotX(OUT2)).isEqualTo(2 * chest.slotX(INPUT));
        assertThat(chest.slotY(OUT1)).isGreaterThan(chest.slotY(INPUT) + ChestGeometry.SLOT);
        for (int i = 0; i < FirepitSim.SLOTS; i++) {
            SlotLayout.Point p = FirepitScreenLayout.TFC_SLOTS.get(i);
            int slot = FirepitScreenLayout.chestSlot(i);
            assertThat(Math.abs(chest.slotX(slot) - p.x())).as("slot " + i + " x").isLessThanOrEqualTo(9);
            assertThat(Math.abs(chest.slotY(slot) - p.y() - FirepitScreenLayout.DY)).as("slot " + i + " y").isLessThanOrEqualTo(13);
        }
        assertThat(FirepitScreenLayout.THERMOMETER.x()).isEqualTo(30);
        assertThat(FirepitScreenLayout.THERMOMETER.y()).as("76 - 51 + dy").isEqualTo(27);
        assertThat(FirepitScreenLayout.THERMOMETER.frames()).isGreaterThanOrEqualTo(ProgressFrames.THERMOMETER_FRAMES);
        for (int slot : FirepitScreenLayout.THERMOMETER_SLOTS) {
            assertThat(chest.slotX(slot)).as("thermometer column under x 30..44").isBetween(30 - 16, 44);
        }
    }

    @Test
    void slotPredicatesFuelOnlyInTheTopFuelSlotAndHeatablesOnlyInTheInput() {
        for (int tfc = 0; tfc < FirepitSim.SLOTS; tfc++) {
            assertThat(RULES.isItemValid(tfc, new ItemStack(Material.OAK_LOG))).as("log in " + tfc)
                .isEqualTo(tfc == FirepitSim.SLOT_FUEL_INPUT || tfc >= FirepitSim.SLOT_OUTPUT_1);
            assertThat(RULES.isItemValid(tfc, new ItemStack(Material.BEEF))).as("beef in " + tfc)
                .isEqualTo(tfc >= FirepitSim.SLOT_ITEM_INPUT);
        }
        open(new FakeLink(0));
        player.setItemOnCursor(new ItemStack(Material.OAK_LOG));
        assertThat(click(INPUT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("fuel in the input").isTrue();
        assertThat(click(FUEL_BOTTOM, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("fuel slot 0").isTrue();
        assertThat(click(OUT1, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("output").isTrue();
        assertThat(click(FirepitScreenLayout.THERMOMETER_SLOTS.getFirst(), ClickType.LEFT, InventoryAction.PLACE_ALL)
            .isCancelled()).as("thermometer").isTrue();
        assertThat(click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isFalse();
        player.setItemOnCursor(new ItemStack(Material.BEEF));
        assertThat(click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("beef as fuel").isTrue();
        assertThat(click(INPUT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isFalse();
        player.setItemOnCursor(new ItemStack(Material.STONE));
        assertThat(click(INPUT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("not heatable").isTrue();
    }

    @Test
    void clicksIntoTheChestSlotsTfcDoesNotHaveAreCancelled() {
        open(new FakeLink(0));
        Set<Integer> used = new HashSet<>(FirepitScreenLayout.THERMOMETER_SLOTS);
        for (int tfc = 0; tfc < FirepitSim.SLOTS; tfc++) {
            used.add(FirepitScreenLayout.chestSlot(tfc));
        }
        assertThat(used).doesNotContain(2, 13);
        for (int slot = 0; slot < 36; slot++) {
            if (used.contains(slot)) {
                continue;
            }
            player.setItemOnCursor(new ItemStack(Material.OAK_LOG));
            assertThat(click(slot, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("unused slot " + slot).isTrue();
            assertThat(click(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled())
                .as("shift-click on unused slot " + slot).isTrue();
            player.setItemOnCursor(null);
            player.getInventory().setItem(0, new ItemStack(Material.OAK_LOG));
            assertThat(hotbarSwap(slot, 0).isCancelled()).as("number-key swap into unused slot " + slot).isTrue();
            player.setItemOnCursor(new ItemStack(Material.OAK_LOG));
            assertThat(click(slot, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled())
                .as("double-click on unused slot " + slot).isTrue();
            InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(), null,
                new ItemStack(Material.OAK_LOG, 2), false,
                Map.of(FUEL_TOP, new ItemStack(Material.OAK_LOG), slot, new ItemStack(Material.OAK_LOG)));
            server.getPluginManager().callEvent(drag);
            assertThat(drag.isCancelled()).as("drag across unused slot " + slot).isTrue();
        }
    }

    @Test
    void placingAStackPutsOneItemInAndTheCommitCascadesTheFuel() {
        FakeLink link = new FakeLink(0);
        GuiSession session = open(link);
        player.setItemOnCursor(new ItemStack(Material.OAK_LOG, 5));
        click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL);
        assertThat(session.getInventory().getItem(FUEL_TOP).getAmount()).isEqualTo(1);
        assertThat(player.getItemOnCursor().getAmount()).as("the rest stays on the cursor").isEqualTo(4);
        assertThat(click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("slot full").isTrue();
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(4);
        scheduler.runDelayed();
        assertThat(link.commits).hasSize(1);
        assertThat(link.firepit.slot(FirepitSim.SLOT_FUEL_CONSUME).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(session.getInventory().getItem(FUEL_BOTTOM).getType()).as("cascaded to fuel slot 0").isEqualTo(Material.OAK_LOG);
        assertThat(session.getInventory().getItem(FUEL_TOP)).isNull();
    }

    @Test
    void shiftClickFillsTheTopFuelSlotAndTheInputWithOneItemAndGivesTheRestBack() {
        FakeLink link = new FakeLink(0);
        GuiSession session = open(link);
        int top = session.getInventory().getSize();
        int raw = top; // the first player inventory slot in a chest view
        player.getOpenInventory().setItem(raw, new ItemStack(Material.OAK_LOG, 8));
        click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(session.getInventory().getItem(FUEL_TOP).getAmount()).isEqualTo(1);
        assertThat(session.getInventory().getItem(INPUT)).as("logs are not heatable").isNull();
        assertThat(count(Material.OAK_LOG)).as("seven come back").isEqualTo(7);
        player.getOpenInventory().setItem(top + 1, new ItemStack(Material.BEEF, 3));
        click(top + 1, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(session.getInventory().getItem(INPUT).getAmount()).isEqualTo(1);
        assertThat(count(Material.BEEF)).isEqualTo(2);
        player.getOpenInventory().setItem(top + 2, new ItemStack(Material.STONE, 3));
        click(top + 2, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(count(Material.STONE)).as("stone goes nowhere").isEqualTo(3);
    }

    @Test
    void theThermometerFollowsTheTemperatureAndShowsItAsTooltip() {
        FakeLink link = new FakeLink(0);
        GuiSession session = open(link);
        assertThat(session.glyphs().tags()).isEqualTo(FirepitScreenLayout.LAYERS.compose(FirepitScreenLayout.frames(0)).tags());
        ItemStack cold = session.getInventory().getItem(FirepitScreenLayout.THERMOMETER_SLOTS.getFirst());
        assertThat(GuiButtons.isButton(cold)).isTrue();
        assertThat(cold.getItemMeta().isHideTooltip()).as("TFC shows nothing when cold").isTrue();
        link.firepit = link.firepit.withTemperature(600);
        scheduler.tick();
        assertThat(FirepitScreens.shown(session).orElseThrow().temperature()).isEqualTo(600f);
        assertThat(session.glyphs().tags()).isEqualTo(FirepitScreenLayout.LAYERS.compose(FirepitScreenLayout.frames(600)).tags());
        assertThat(FirepitScreenLayout.frames(600).get(FirepitScreenLayout.THERMOMETER_LAYER))
            .isEqualTo(ProgressFrames.thermometer(600)).isPositive();
        ItemStack warm = session.getInventory().getItem(FirepitScreenLayout.THERMOMETER_SLOTS.getLast());
        assertThat(warm.getItemMeta().isHideTooltip()).isFalse();
        assertThat(warm.getItemMeta().customName()).isNotNull();
    }

    @Test
    void reloadShowsCookedOutputsAndTheOutputsOnlyGiveBack() {
        FakeLink link = new FakeLink(300);
        GuiSession session = open(link);
        List<ItemStack> slots = FirepitSnapshot.emptySlots();
        slots.set(FirepitSim.SLOT_OUTPUT_1, new ItemStack(Material.COOKED_BEEF, 3));
        link.firepit = link.firepit.withSlots(slots);
        scheduler.tick();
        assertThat(session.getInventory().getItem(OUT1).getAmount()).isEqualTo(3);
        player.setItemOnCursor(null);
        assertThat(click(OUT1, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isFalse();
    }

    @Test
    void oneViewerPerFirepitAndClosingCommitsAndFrees() {
        FakeLink link = new FakeLink(0);
        GuiSession session = open(link);
        PlayerMock other = server.addPlayer("Other");
        assertThat(screens.open(other, "firepit@0,64,0", link, p -> "Firepit").refusal()).isEqualTo(FirepitScreens.Refusal.BUSY);
        session.getInventory().setItem(INPUT, new ItemStack(Material.BEEF));
        player.closeInventory();
        assertThat(link.commits).hasSize(1);
        assertThat(link.firepit.slot(FirepitSim.SLOT_ITEM_INPUT).getType()).isEqualTo(Material.BEEF);
        assertThat(session.getInventory().getItem(INPUT)).isNull();
        assertThat(link.closed).isEqualTo(1);
        assertThat(scheduler.repeating.getFirst().isCancelled()).isTrue();
        assertThat(screens.openCount()).isZero();
        assertThat(screens.open(other, "firepit@0,64,0", link, p -> "Firepit").ok()).isTrue();
    }

    @Test
    void aGoneFirepitClosesTheScreenAndReturnsOnlyWhatTheViewerAdded() {
        FakeLink link = new FakeLink(0);
        List<ItemStack> slots = FirepitSnapshot.emptySlots();
        slots.set(FirepitSim.SLOT_OUTPUT_1, new ItemStack(Material.COOKED_BEEF));
        link.firepit = link.firepit.withSlots(slots);
        GuiSession session = open(link);
        session.getInventory().setItem(INPUT, new ItemStack(Material.BEEF));
        link.gone = true;
        scheduler.tick();
        assertThat(session.closed()).isTrue();
        assertThat(player.getInventory().contains(Material.BEEF)).isTrue();
        assertThat(player.getInventory().contains(Material.COOKED_BEEF)).as("dropped with the block").isFalse();
        FakeLink broken = new FakeLink(0);
        broken.gone = true;
        assertThat(screens.open(player, "other", broken, p -> "x").refusal()).isEqualTo(FirepitScreens.Refusal.GONE);
    }

    @Test
    void theDemoLinkCascadesAndGivesEverythingBackOnClose() {
        DemoFirepitLink demo = new DemoFirepitLink(500, RULES);
        FirepitScreens.Opened o = screens.open(player, new Object(), demo, p -> "Firepit");
        o.session().getInventory().setItem(FUEL_TOP, new ItemStack(Material.OAK_LOG));
        o.session().getInventory().setItem(INPUT, new ItemStack(Material.BEEF));
        screens.reload(o.session());
        assertThat(demo.snapshot().orElseThrow().slot(FirepitSim.SLOT_FUEL_CONSUME).getType()).isEqualTo(Material.OAK_LOG);
        player.closeInventory();
        assertThat(count(Material.OAK_LOG)).isEqualTo(1);
        assertThat(count(Material.BEEF)).isEqualTo(1);
    }

    @Test
    void cascadeMovesFuelDownInOrder() {
        List<ItemStack> slots = FirepitSnapshot.emptySlots();
        slots.set(1, new ItemStack(Material.OAK_LOG));
        slots.set(3, new ItemStack(Material.BIRCH_LOG));
        slots.set(4, new ItemStack(Material.BEEF));
        List<ItemStack> out = FirepitScreenLayout.cascade(slots);
        assertThat(out.get(0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(out.get(1).getType()).isEqualTo(Material.BIRCH_LOG);
        assertThat(out.get(2).isEmpty()).isTrue();
        assertThat(out.get(3).isEmpty()).isTrue();
        assertThat(out.get(4).getType()).as("the input stays").isEqualTo(Material.BEEF);
    }

    private int count(Material m) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == m) {
                n += s.getAmount();
            }
        }
        return n;
    }
}
