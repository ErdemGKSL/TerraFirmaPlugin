/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill.gui;

import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-08: the grill screen's one-item slots and sync, as the firepit screen's listener: a click that places a stack
 * puts one item in and leaves the rest on the cursor, a swap with a bigger cursor stack is refused, a shift-click's excess
 * goes back at once, and every click or drag queues the commit. Viewer thread.
 */
public final class GrillGuiListener implements Listener {
    private final GrillScreens screens;

    public GrillGuiListener(GrillScreens screens) {
        this.screens = Objects.requireNonNull(screens, "screens");
    }

    /**
     * Before the GUI routes a shift-click from the player inventory (LOW): marks it, so the grill slots refuse
     * {@code #tfc:firepit_fuel} as TFC's {@code GrillContainer.moveStack} does; cleared at MONITOR.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onShift(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        GrillScreens.Screen screen = session == null ? null : GrillScreens.state(session);
        if (screen != null) {
            screen.shiftIn = e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && e.getRawSlot() >= e.getView().getTopInventory().getSize();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || !GrillScreens.isGrill(session)) {
            return;
        }
        int raw = e.getRawSlot();
        int tfc = raw >= 0 && raw < e.getView().getTopInventory().getSize() ? GrillScreenLayout.tfcSlot(raw) : -1;
        if (tfc < 0) {
            return;
        }
        int limit = GrillScreenLayout.limit(tfc);
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.isEmpty() || cursor.getAmount() <= limit) {
            return;
        }
        InventoryAction action = e.getAction();
        if (action == InventoryAction.SWAP_WITH_CURSOR) {
            e.setCancelled(true);
        } else if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_SOME) {
            e.setCancelled(true);
            ItemStack there = session.getInventory().getItem(raw);
            int have = there == null || there.isEmpty() ? 0 : there.getAmount();
            int move = Math.min(limit - have, cursor.getAmount());
            if (move <= 0) {
                return;
            }
            ItemStack placed = cursor.clone();
            placed.setAmount(have + move);
            session.getInventory().setItem(raw, placed);
            ItemStack rest = cursor.clone();
            rest.setAmount(cursor.getAmount() - move);
            e.getView().setCursor(rest);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || !GrillScreens.isGrill(session)) {
            return;
        }
        GrillScreens.state(session).shiftIn = false;
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getRawSlot() >= e.getView().getTopInventory().getSize()) {
            GrillScreens.clamp(session);
        }
        screens.touched(session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && GrillScreens.isGrill(session)) {
            screens.touched(session);
        }
    }
}
