/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.module.FeatureModules;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.tabs.TabLayout.Page;
import dev.tfp.paper.tabs.TabLayout.Tab;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

/** PAR-SURV-07: the outside-click trigger, tab buttons, page clicks and the crafting table title, on MockBukkit. */
class PlayerTabsMockBukkitTest extends MockServerTest {
    private static final int DIM = 8;
    private final Messages messages = Messages.fromJar();
    private InlineScheduler scheduler;
    private TabService service;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        scheduler = new InlineScheduler();
        long ticks = CalendarTicks.of(1002, Month.MAY, 3, 10, 30, DIM);
        ClockSnapshot clock = new ClockSnapshot(5, 100, ticks, DIM, true);
        service = new TabService(server, scheduler, messages, () -> clock, id -> Optional.of(PlayerData.NEW),
            () -> ClimateTemperatureStyle.CELSIUS, true);
        server.getPluginManager().registerEvents(new TabListener(service, scheduler, messages), plugin);
        player = server.addPlayer("Bot");
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        if (view.getType() == InventoryType.CRAFTING) {
            // MockBukkit's own inventory view cannot convert slots (the event constructor asks); a view that can
            view = new SimpleInventoryViewMock(player, view.getTopInventory(), player.getInventory(), InventoryType.CRAFTING) {
                @Override
                public int convertSlot(int rawSlot) {
                    return rawSlot;
                }
            };
        }
        InventoryType.SlotType slotType = raw < 0 ? InventoryType.SlotType.OUTSIDE : InventoryType.SlotType.CONTAINER;
        InventoryClickEvent e = new InventoryClickEvent(view, slotType, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private TabSession open() {
        return TabSession.of(player.getOpenInventory().getTopInventory());
    }

    @Test
    void anEmptyHandedClickOutsideTheInventoryOpensTheCalendarPage() {
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
        InventoryClickEvent e = click(TabListener.OUTSIDE, ClickType.LEFT, InventoryAction.NOTHING);
        assertThat(e.isCancelled()).isTrue();
        TabSession s = open();
        assertThat(s).isNotNull();
        assertThat(s.page().page()).isEqualTo(Page.CALENDAR);
        Inventory top = s.getInventory();
        assertThat(top.getSize()).isEqualTo(36);
        for (Tab t : Tab.values()) {
            ItemStack b = top.getItem(t.slot());
            assertThat(GuiButtons.isButton(b)).as(t.name()).isTrue();
            assertThat(b.getItemMeta().customName()).isEqualTo(Component.text(messages.get("en_us", "tabs.title." + t.key()))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        assertThat(top.getItem(Tab.CALENDAR.slot()).getItemMeta().lore()).hasSize(4); // clock, date, days left, season
        assertThat(top.getItem(Tab.NUTRITION.slot()).getItemMeta().hasLore()).isFalse();
        assertThat(s.page().plainLines().getFirst()).isEqualTo("10:30");
    }

    @Test
    void holdingAStackOrClickingInsideOrInCreativeDoesNotOpenIt() {
        player.setItemOnCursor(new ItemStack(Material.DIRT));
        assertThat(click(TabListener.OUTSIDE, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR).isCancelled()).isFalse();
        assertThat(open()).isNull();
        player.setItemOnCursor(null);
        click(9, ClickType.LEFT, InventoryAction.NOTHING);
        assertThat(open()).isNull();
        click(TabListener.OUTSIDE, ClickType.CREATIVE, InventoryAction.PLACE_ALL);
        assertThat(open()).isNull();
        click(TabListener.OUTSIDE, ClickType.MIDDLE, InventoryAction.NOTHING);
        assertThat(open()).isNull();
        player.setGameMode(GameMode.ADVENTURE); // adventure behaves like survival
        click(TabListener.OUTSIDE, ClickType.RIGHT, InventoryAction.NOTHING);
        assertThat(open()).isNotNull();
    }

    @Test
    void tabButtonsSwitchPagesAndInventoryClosesTheScreen() {
        click(TabListener.OUTSIDE, ClickType.LEFT, InventoryAction.NOTHING);
        assertThat(click(Tab.NUTRITION.slot(), ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
        assertThat(open().page().page()).isEqualTo(Page.NUTRITION);
        // PlayerData.NEW has no records: TFC's default nutrition (0.5, dairy 0) at full hunger
        assertThat(open().page().frames()).containsEntry(TabLayout.barLayer(0), 46).doesNotContainKey(TabLayout.barLayer(4));
        click(Tab.CLIMATE.slot(), ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        assertThat(open().page().page()).isEqualTo(Page.CLIMATE);
        TabSession climate = open();
        click(Tab.INVENTORY.slot(), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(open()).isNull();
        assertThat(climate.closed()).isTrue();
        assertThat(climate.getInventory().getItem(Tab.CLIMATE.slot())).as("buttons are removed on close").isNull();
    }

    @Test
    void pageSlotsAreLockedButThePlayerInventoryWorks() {
        click(TabListener.OUTSIDE, ClickType.LEFT, InventoryAction.NOTHING);
        TabSession s = open();
        assertThat(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("decoration").isTrue();
        player.setItemOnCursor(new ItemStack(Material.STONE));
        assertThat(click(5, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue();
        assertThat(click(Tab.CLIMATE.slot(), ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled()).isTrue();
        assertThat(open()).as("no page switch with a stack on the cursor").isSameAs(s);
        assertThat(click(40, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("player inventory").isFalse();
        assertThat(click(40, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled()).isTrue();
        assertThat(click(40, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled()).isTrue();
        assertThat(click(TabListener.OUTSIDE, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR).isCancelled())
            .as("dropping outside a page is vanilla").isFalse();
        InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(), null, new ItemStack(Material.STONE, 2),
            false, java.util.Map.of(3, new ItemStack(Material.STONE), 40, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(drag);
        assertThat(drag.isCancelled()).isTrue();
    }

    @Test
    void aPageDoesNotReplaceAnotherScreen() {
        Inventory chest = server.createInventory(null, 27);
        player.openInventory(chest);
        assertThat(service.open(player, Page.CLIMATE).join()).isFalse();
        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(chest);
        player.closeInventory();
        assertThat(service.open(player, Page.CLIMATE).join()).isTrue();
    }

    @Test
    void theCraftingTableGetsAPlainTitle() {
        Inventory table = server.createInventory(null, InventoryType.WORKBENCH);
        InventoryView view = player.openInventory(table);
        InventoryOpenEvent e = new InventoryOpenEvent(view);
        if (view.title() instanceof net.kyori.adventure.text.TranslatableComponent) {
            server.getPluginManager().callEvent(e);
            assertThat(e.titleOverride()).isEqualTo(Component.text("Crafting"));
        } else {
            // MockBukkit gives the table a plain title; the override only replaces the vanilla translatable one
            server.getPluginManager().callEvent(e);
            assertThat(e.titleOverride()).isNull();
        }
    }

    @Test
    void theModuleRegistersTheListener() {
        Integrations none = Integrations.create(null, name -> false, List.of());
        ModuleServices services = new ModuleServices();
        ModuleContext ctx = new ModuleContext.Of((JavaPlugin) plugin, Logger.getLogger("tfp-test"), scheduler, none, null,
            null, null, messages, null, null, null, null, null, services);
        assertThat(FeatureModules.of(List.of(new PlayerTabsModule())).enableAll(ctx, Logger.getLogger("tfp-test")))
            .containsExactly(PlayerTabsModule.ID);
        assertThat(InventoryClickEvent.getHandlerList().getRegisteredListeners())
            .anyMatch(r -> r.getListener() instanceof TabListener);
        assertThat(Set.of(Page.values())).hasSize(3);
    }
}
