/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/AnvilContainer.java @ c3b4d9536 (moveStack: taking a stack out of the
 *       container clears the recipe of an unworked item)
 */
package dev.tfp.paper.anvil.forge;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.paper.anvil.plan.ForgingPdc;
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
 * PAR-METAL-05: the forging screen's slot limits and sync. A click that places a stack onto a one-item slot puts one item in
 * and leaves the rest on the cursor; a swap with a bigger cursor stack is refused. A stack of the main or second input that
 * leaves the screen loses the plan of an unworked item (TFC {@code clearRecipeIfNotWorked}). After any click or drag
 * {@link AnvilForgeScreens#touched} queues the commit. Viewer thread.
 */
public final class AnvilForgeListener implements Listener {
    private final AnvilForgeScreens screens;

    public AnvilForgeListener(AnvilForgeScreens screens) {
        this.screens = Objects.requireNonNull(screens, "screens");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || !AnvilForgeScreens.isForge(session)) {
            return;
        }
        int raw = e.getRawSlot();
        int slot = raw >= 0 && raw < e.getView().getTopInventory().getSize() ? AnvilForgeScreens.deviceSlot(raw) : -1;
        if (slot < 0) {
            return;
        }
        InventoryAction action = e.getAction();
        if ((slot == AnvilSlots.INPUT_MAIN || slot == AnvilSlots.INPUT_SECOND) && leaves(action)) {
            ItemStack current = e.getCurrentItem();
            if (current != null && !current.isEmpty() && ForgingPdc.read(current) != null) {
                ItemStack cleared = current.clone();
                if (ForgingPdc.clearIfNotWorked(cleared)) {
                    e.setCurrentItem(cleared);
                }
            }
        }
        int limit = AnvilSlots.limit(slot);
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.isEmpty() || cursor.getAmount() <= limit) {
            return;
        }
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

    /** The actions that take the slot's stack out of the screen (or swap it for the cursor's). */
    static boolean leaves(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                DROP_ALL_SLOT, DROP_ONE_SLOT, SWAP_WITH_CURSOR, CLONE_STACK -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || !AnvilForgeScreens.isForge(session)) {
            return;
        }
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getRawSlot() >= e.getView().getTopInventory().getSize()) {
            screens.clamp(session); // the framework moved the stack itself; give the excess back now
        }
        screens.touched(session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && AnvilForgeScreens.isForge(session)) {
            screens.touched(session);
        }
    }
}
