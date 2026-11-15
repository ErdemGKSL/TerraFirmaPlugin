/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.SlotLayout;
import dev.tfp.paper.grill.TickScheduler;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-HEAT-08: the grill screen: layout, TFC's slot rules and limits, the cascade, reloads and one viewer per grill. */
class GrillScreensMockBukkitTest extends MockServerTest {
    private static final int FUEL_TOP = GrillScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_INPUT);
    private static final int FUEL_BOTTOM = GrillScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_CONSUME);

    private final AtomicLong now = new AtomicLong(100_000);
    private TickScheduler scheduler;
    private GrillScreens screens;
    private PlayerMock player;

    /** A grill in memory: logs are fuel, beef is heatable; commits cascade like TFC; {@code gone} answers empty. */
    static final class FakeLink implements GrillLink {
        GrillSnapshot grill;
        boolean gone;
        final List<List<ItemStack>> commits = new ArrayList<>();

        FakeLink(float temperature) {
            List<ItemStack> empty = new ArrayList<>();
            for (int i = 0; i < GrillSim.SLOTS; i++) {
                empty.add(ItemStack.empty());
            }
            grill = new GrillSnapshot(temperature, empty);
        }

        @Override
        public Optional<GrillSnapshot> snapshot() {
            return gone ? Optional.empty() : Optional.of(grill);
        }

        @Override
        public Optional<GrillSnapshot> commit(List<ItemStack> slots) {
            commits.add(slots);
            if (gone) {
                return Optional.empty();
            }
            grill = new GrillSnapshot(grill.temperature(), GrillScreenLayout.cascade(slots));
            return snapshot();
        }

        @Override
        public boolean isItemValid(int tfcSlot, ItemStack stack) {
            if (GrillSim.isGrillSlot(tfcSlot)) {
                return stack.getType() == Material.BEEF || stack.getType() == Material.STICK;
            }
            return tfcSlot == FirepitSim.SLOT_FUEL_INPUT && isFirepitFuel(stack);
        }

        /** Logs and sticks are fuel; a stick is also heatable (a heatable fuel, as some TFC items are). */
        @Override
        public boolean isFirepitFuel(ItemStack stack) {
            return stack.getType() == Material.OAK_LOG || stack.getType() == Material.STICK;
        }
    }

    @BeforeEach
    void setUp() {
        scheduler = new TickScheduler();
        GuiService gui = new GuiService(server, scheduler, now::get, true);
        screens = new GrillScreens(gui, scheduler, Messages.fromJar(), HeatDisplayStyle.COLOR);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new GrillGuiListener(screens), plugin);
        player = server.addPlayer("Bot");
    }

    private GuiSession open(GrillLink link) {
        GrillScreens.Opened o = screens.open(player, "grill@0,64,0", link, p -> "Grill");
        assertThat(o.ok()).isTrue();
        return o.session();
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
    void theLayoutPutsTfcsNineSlotsOnA4RowChest() {
        assertThat(GrillScreenLayout.CHEST.height()).isEqualTo(186);
        assertThat(GrillScreenLayout.DY).isEqualTo(2);
        SlotLayout fuel = SlotLayout.of(GrillScreenLayout.CHEST, GrillScreenLayout.DY, GrillScreenLayout.TFC_SLOTS.subList(0, 4));
        assertThat(fuel.chestSlots()).isEqualTo(GrillScreenLayout.CHEST_SLOTS.subList(0, 4));
        ChestGeometry chest = GrillScreenLayout.CHEST;
        for (int i = 0; i < GrillSim.SLOTS; i++) {
            SlotLayout.Point p = GrillScreenLayout.TFC_SLOTS.get(i);
            int slot = GrillScreenLayout.chestSlot(i);
            assertThat(chest.slotX(slot)).as("slot " + i + " x").isEqualTo(p.x());
            // the grill row moves up 4 px onto chest row 0
            assertThat(Math.abs(chest.slotY(slot) - p.y() - GrillScreenLayout.DY)).as("slot " + i + " y").isLessThanOrEqualTo(4);
        }
        Set<Integer> used = new HashSet<>(GrillScreenLayout.CHEST_SLOTS);
        used.addAll(GrillScreenLayout.THERMOMETER_SLOTS);
        assertThat(used).hasSize(GrillSim.SLOTS + 4);
        assertThat(GrillScreenLayout.BACKGROUND.id()).isEqualTo("tfp:gui/grill");
        assertThat(GrillScreenLayout.THERMOMETER.x()).isEqualTo(30);
    }

    @Test
    void slotRulesFuelOnTopHeatablesOnTheGrillOneItemEach() {
        GuiSession session = open(new FakeLink(0));
        player.setItemOnCursor(new ItemStack(Material.OAK_LOG, 5));
        assertThat(click(FUEL_BOTTOM, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue();
        assertThat(click(GrillScreenLayout.chestSlot(4), ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue();
        assertThat(click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue(); // one placed by hand
        assertThat(session.getInventory().getItem(FUEL_TOP).getAmount()).isEqualTo(1);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(4);
        player.setItemOnCursor(new ItemStack(Material.BEEF, 3));
        for (int s = GrillSim.SLOT_EXTRA_INPUT_START; s <= GrillSim.SLOT_EXTRA_INPUT_END; s++) {
            click(GrillScreenLayout.chestSlot(s), ClickType.LEFT, InventoryAction.PLACE_ALL);
        }
        int placed = 0;
        for (int s = GrillSim.SLOT_EXTRA_INPUT_START; s <= GrillSim.SLOT_EXTRA_INPUT_END; s++) {
            ItemStack there = session.getInventory().getItem(GrillScreenLayout.chestSlot(s));
            if (there != null && !there.isEmpty()) {
                assertThat(there.getAmount()).isEqualTo(1);
                placed++;
            }
        }
        // two placed one at a time from the stack; the last single item is the framework's own move (not run here)
        assertThat(placed).isEqualTo(2);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(1);
    }

    @Test
    void aShiftClickedFuelGoesOnlyToTheFuelSlotNeverOntoTheGrill() {
        GuiSession session = open(new FakeLink(0));
        int raw = player.getOpenInventory().getTopInventory().getSize(); // player inventory slot 9
        player.getOpenInventory().setItem(raw, new ItemStack(Material.STICK, 3));
        click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(session.getInventory().getItem(FUEL_TOP).getType()).isEqualTo(Material.STICK);
        assertThat(session.getInventory().getItem(FUEL_TOP).getAmount()).isEqualTo(1);
        assertThat(player.getOpenInventory().getItem(raw).getAmount()).isEqualTo(2);
        // the fuel slot is full: TFC's moveStack does not fall back to the grill slots for a fuel item
        click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        for (int tfc = GrillSim.SLOT_EXTRA_INPUT_START; tfc <= GrillSim.SLOT_EXTRA_INPUT_END; tfc++) {
            ItemStack there = session.getInventory().getItem(GrillScreenLayout.chestSlot(tfc));
            assertThat(there == null || there.isEmpty()).as("grill slot " + tfc).isTrue();
        }
        assertThat(player.getOpenInventory().getItem(raw).getAmount()).isEqualTo(2);
        // by hand the same item may go on the grill (isItemValid)
        player.setItemOnCursor(new ItemStack(Material.STICK));
        assertThat(click(GrillScreenLayout.chestSlot(4), ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isFalse();
        // a heatable that is no fuel still shift-clicks onto the grill
        int beef = raw + 1;
        player.getOpenInventory().setItem(beef, new ItemStack(Material.BEEF, 1));
        click(beef, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(player.getOpenInventory().getItem(beef)).as("beef moved").matches(i -> i == null || i.isEmpty());
        boolean onGrill = false;
        for (int tfc = GrillSim.SLOT_EXTRA_INPUT_START; tfc <= GrillSim.SLOT_EXTRA_INPUT_END; tfc++) {
            ItemStack there = session.getInventory().getItem(GrillScreenLayout.chestSlot(tfc));
            onGrill |= there != null && there.getType() == Material.BEEF;
        }
        assertThat(onGrill).isTrue();
    }

    @Test
    void theCommitCascadesTheFuelAndAReloadFollowsTheGrill() {
        FakeLink link = new FakeLink(0);
        GuiSession session = open(link);
        player.setItemOnCursor(new ItemStack(Material.OAK_LOG, 5));
        click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL);
        scheduler.runDelayed();
        assertThat(link.commits).hasSize(1);
        assertThat(session.getInventory().getItem(FUEL_BOTTOM).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(session.getInventory().getItem(FUEL_TOP)).isNull();

        List<ItemStack> cooked = new ArrayList<>(link.grill.slots());
        cooked.set(6, new ItemStack(Material.COOKED_BEEF));
        link.grill = new GrillSnapshot(320f, cooked);
        scheduler.tick();
        assertThat(session.getInventory().getItem(GrillScreenLayout.chestSlot(6)).getType()).isEqualTo(Material.COOKED_BEEF);
        assertThat(GrillScreens.shown(session).orElseThrow().temperature()).isEqualTo(320f);

        // one viewer per grill
        PlayerMock other = server.addPlayer("Other");
        assertThat(screens.open(other, "grill@0,64,0", link, p -> "Grill").refusal()).isEqualTo(GrillScreens.Refusal.BUSY);
        // the grill goes: the screen closes
        link.gone = true;
        scheduler.tick();
        assertThat(screens.viewer("grill@0,64,0")).isEmpty();
    }
}
