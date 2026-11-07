/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/button/PlayerInventoryTabButton.java @ c3b4d9536 (the tab buttons of
 *       the inventory and of each page),
 *       src/main/java/net/dries007/tfc/network/SwitchInventoryTabPacket.java @ c3b4d9536 (INVENTORY returns to the player
 *       inventory, the others open their page)
 */
package dev.tfp.paper.tabs;

import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tabs.TabLayout.Page;
import dev.tfp.paper.tabs.TabLayout.Tab;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-SURV-07: the tab trigger and the page clicks.
 *
 * <ul>
 *   <li>Trigger: a left or right click outside the player's own inventory window (raw slot -999 while the view is
 *       {@code CRAFTING}) with an empty cursor opens the calendar page; the pack draws TFC's tab column right of the
 *       window, but the server cannot see where outside the window the click was. A click holding a stack still drops it
 *       (vanilla). The creative inventory sends no click for this, so creative players have no trigger.</li>
 *   <li>On a page: the top slots are the tab buttons and decoration; every click there is cancelled and a tab button
 *       switches the page (INVENTORY closes it: the server cannot open a client's own inventory screen). The player
 *       inventory below works as in TFC's tab screens, except shift-clicks and double-click collecting, which would move
 *       items into or out of the page.</li>
 *   <li>The crafting table shares the {@code container.crafting} title with the inventory, so it would show the tab art
 *       too; it gets a plain title override instead.</li>
 * </ul>
 */
public final class TabListener implements Listener {
    private final TabService service;
    private final Scheduler scheduler;
    private final Messages messages;

    public TabListener(TabService service, Scheduler scheduler, Messages messages) {
        this.service = service;
        this.scheduler = scheduler;
        this.messages = messages;
    }

    /** {@code InventoryView.OUTSIDE}: the raw slot of a click outside the window. */
    static final int OUTSIDE = -999;

    /** True for the click that opens the tabs from the player's own inventory screen. */
    static boolean isTrigger(InventoryClickEvent e) {
        if (e.getRawSlot() != OUTSIDE || e.getView().getType() != InventoryType.CRAFTING) {
            return false;
        }
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        ItemStack cursor = e.getCursor();
        return (cursor == null || cursor.isEmpty()) && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (isTrigger(e)) {
            e.setCancelled(true);
            service.open((Player) e.getWhoClicked(), Page.CALENDAR);
            return;
        }
        TabSession session = TabSession.of(e.getView().getTopInventory());
        if (session == null || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = e.getRawSlot();
        if (raw < 0) {
            return; // outside the page: vanilla (drops the cursor stack, if any)
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getClick() == ClickType.DOUBLE_CLICK
            || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            return;
        }
        if (raw >= e.getView().getTopInventory().getSize()) {
            return; // the player's own inventory
        }
        e.setCancelled(true);
        Tab tab = Tab.atSlot(raw);
        if (tab == null) {
            return;
        }
        ItemStack cursor = e.getCursor();
        if (cursor != null && !cursor.isEmpty()) {
            return;
        }
        scheduler.entity(player, () -> press(session, tab), () -> { });
    }

    /** Player's thread: a tab button press. Pressing the active tab refreshes the page. */
    void press(TabSession session, Tab tab) {
        if (session.closed()) {
            return;
        }
        Page page = Page.of(tab);
        if (page == null) {
            session.viewer().closeInventory(); // INVENTORY
            return;
        }
        service.open(session.viewer(), page);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (TabSession.of(e.getView().getTopInventory()) == null) {
            return;
        }
        int size = e.getView().getTopInventory().getSize();
        if (e.getRawSlots().stream().anyMatch(s -> s < size)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        TabSession session = TabSession.of(e.getView().getTopInventory());
        if (session != null) {
            session.close();
        }
    }

    /** The crafting table keeps a plain "Crafting" title (the pack turns {@code container.crafting} into the tab art). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!service.glyphs() || e.getInventory().getType() != InventoryType.WORKBENCH || e.titleOverride() != null) {
            return;
        }
        if (e.getView().title() instanceof TranslatableComponent t && t.key().equals("container.crafting")
            && e.getPlayer() instanceof Player p) {
            e.titleOverride(Component.text(messages.get(p, "tabs.crafting_table")));
        }
    }
}
