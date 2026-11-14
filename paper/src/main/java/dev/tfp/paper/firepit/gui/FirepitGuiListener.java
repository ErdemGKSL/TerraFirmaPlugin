/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536
 *       (getSlotStackLimit: one item in every slot but the outputs)
 */
package dev.tfp.paper.firepit.gui;

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
 * PAR-HEAT-07: the firepit screen's slot limits and sync. A click that places a stack onto a one-item slot (after the GUI
 * framework allowed it) puts one item in and leaves the rest on the cursor, as a vanilla slot with
 * {@code getMaxStackSize() == 1}; a swap with a bigger cursor stack is refused. After a shift-click into the screen the
 * excess goes back at once; after any click or drag {@link FirepitScreens#touched} queues the commit. Viewer thread.
 */
public final class FirepitGuiListener implements Listener {
    private final FirepitScreens screens;

    public FirepitGuiListener(FirepitScreens screens) {
        this.screens = Objects.requireNonNull(screens, "screens");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || !FirepitScreens.isFirepit(session)) {
            return;
        }
        int raw = e.getRawSlot();
        int tfc = raw >= 0 && raw < e.getView().getTopInventory().getSize() ? FirepitScreenLayout.tfcSlot(raw) : -1;
        if (tfc < 0) {
            return;
        }
        int limit = FirepitScreenLayout.limit(tfc);
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
        if (session == null || !FirepitScreens.isFirepit(session)) {
            return;
        }
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getRawSlot() >= e.getView().getTopInventory().getSize()) {
            FirepitScreens.clamp(session); // the framework moved the stack itself; give the excess back now
        }
        screens.touched(session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && FirepitScreens.isFirepit(session)) {
            screens.touched(session);
        }
    }
}
