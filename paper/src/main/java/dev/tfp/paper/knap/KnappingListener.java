/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (knapping: Target.AIR, the main hand's
 *       type), src/main/java/net/dries007/tfc/common/container/ItemStackContainer.java @ c3b4d9536 (clicked: the target
 *       stack cannot be picked up, shift-clicked, thrown or swapped, no number-key or offhand swaps, no pickup-all),
 *       src/main/java/net/dries007/tfc/common/container/KnappingContainer.java @ c3b4d9536 (moveStack: the result goes to
 *       the hotbar first, from the last slot; onSlotTake)
 */
package dev.tfp.paper.knap;

import dev.tfp.core.knap.KnappingScreen;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-PROC-01: opens knapping on a right-click in the air with a knappable main hand stack, takes the result from the
 * output slot (so the take is seen and the grid resets, TFC {@code onSlotTake}), and keeps the input stack in its slot.
 * Runs after {@code GuiListener} (LOW), which already refuses everything else on the screen.
 */
public final class KnappingListener implements Listener {
    private final KnappingMenu.Services services;

    public KnappingListener(KnappingMenu.Services services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = e.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || KnappingMenu.typeOf(services, e.getItem() == null
                ? player.getInventory().getItemInMainHand() : e.getItem()).isEmpty()) {
            return;
        }
        if (KnappingMenu.open(services, player).isPresent()) {
            e.setCancelled(true); // no vanilla use of the item (a goat horn would sound)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        KnappingMenu menu = KnappingMenu.of(session);
        if (menu == null || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = e.getClick();
        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true); // TFC: no swaps at all while an item container is open
            return;
        }
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        if (raw >= top.getSize() && e.getClickedInventory() instanceof PlayerInventory && e.getSlot() == menu.heldSlot()) {
            e.setCancelled(true); // the input stack stays where it is
            return;
        }
        if (raw == KnappingScreen.OUTPUT_SLOT) {
            e.setCancelled(true);
            if (take(e, player, top)) {
                menu.taken();
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        KnappingMenu menu = KnappingMenu.of(GuiService.session(e.getView().getTopInventory()));
        if (menu == null) {
            return;
        }
        int heldRaw = e.getView().getTopInventory().getSize() + 27 + menu.heldSlot();
        if (e.getRawSlots().contains(heldRaw)) {
            e.setCancelled(true);
        }
    }

    /** Moves the result out as the click asks; true when it left the output slot. */
    static boolean take(InventoryClickEvent e, Player player, Inventory top) {
        ItemStack out = top.getItem(KnappingScreen.OUTPUT_SLOT);
        if (out == null || out.isEmpty()) {
            return false;
        }
        switch (e.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor.isEmpty()) {
                    player.setItemOnCursor(out);
                } else if (cursor.isSimilar(out) && cursor.getAmount() + out.getAmount() <= cursor.getMaxStackSize()) {
                    cursor.setAmount(cursor.getAmount() + out.getAmount());
                    player.setItemOnCursor(cursor);
                } else {
                    return false;
                }
                top.setItem(KnappingScreen.OUTPUT_SLOT, null);
                return true;
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                ItemStack left = moveToPlayer(player.getInventory(), out);
                if (left != null && left.getAmount() == out.getAmount()) {
                    return false;
                }
                top.setItem(KnappingScreen.OUTPUT_SLOT, left);
                return true;
            }
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                player.getWorld().dropItem(player.getEyeLocation(), out);
                top.setItem(KnappingScreen.OUTPUT_SLOT, null);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Vanilla {@code moveItemStackTo(stack, player slots, reverse)} as TFC's {@code moveStack} calls it for a container slot:
     * the player slots from the last hotbar slot backwards to the first main slot, merging into similar stacks first, then
     * into the first empty slot. Returns what is left, or null.
     */
    static ItemStack moveToPlayer(PlayerInventory inv, ItemStack stack) {
        List<Integer> order = new ArrayList<>();
        for (int i = 8; i >= 0; i--) {
            order.add(i);
        }
        for (int i = 35; i >= 9; i--) {
            order.add(i);
        }
        ItemStack left = stack.clone();
        for (int slot : order) {
            ItemStack there = inv.getItem(slot);
            if (left.getAmount() > 0 && there != null && !there.isEmpty() && there.isSimilar(left)) {
                int move = Math.min(there.getMaxStackSize() - there.getAmount(), left.getAmount());
                if (move > 0) {
                    there.setAmount(there.getAmount() + move);
                    inv.setItem(slot, there);
                    left.setAmount(left.getAmount() - move);
                }
            }
        }
        for (int slot : order) {
            ItemStack there = inv.getItem(slot);
            if (left.getAmount() > 0 && (there == null || there.isEmpty())) {
                inv.setItem(slot, left.clone());
                left.setAmount(0);
            }
        }
        return left.getAmount() > 0 ? left : null;
    }
}
