/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/ItemStackHooks.java @ c3b4d9536 (food stacks with close creation dates merge)
 */
package dev.tfp.paper.food;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * TFC's food stack rule in vanilla stacking (M3-SURV-01). Vanilla only stacks identical items, so two foods
 * whose rounded creation dates differ but still stack by TFC's rule ({@link FoodStacks#canMerge}) would not
 * merge. Three places get the rule, each taking the earlier creation date ({@link FoodStacks#mergeInto}):
 * <ul>
 *   <li><b>Inventory click</b> (left/right click of a food cursor on a food slot that vanilla would swap):
 *       cancelled and merged here, the rest stays on the cursor.</li>
 *   <li><b>Pickup</b> by a player: the first inventory slot that merges gets the merged date and the item on
 *       the ground is re-dated to match, so vanilla's own pickup stacks it. Vanilla decides how much fits
 *       before the event, so a full inventory whose only room is such a slot still refuses the pickup.</li>
 *   <li><b>Hopper move</b>: the same re-dating on the destination slot and the moved item.</li>
 * </ul>
 * Cheap by construction: undated stacks return after one PDC check, at most one inventory's storage slots
 * (36 for a player, or the container) are scanned, no world access. All three events run on the thread that owns the inventory or
 * entity (Folia region/entity thread), so stacks are changed in place without scheduling.
 */
public final class FoodMergeListener implements Listener {
    private final ItemFoodHooks food;

    public FoodMergeListener(ItemFoodHooks food) {
        this.food = Objects.requireNonNull(food, "food");
    }

    // ---- inventory click ------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getAction() != InventoryAction.SWAP_WITH_CURSOR
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack slot = event.getCurrentItem();
        if (!dated(cursor) || !dated(slot) || cursor.isSimilar(slot) || !food.canMerge(slot, cursor)) {
            return;
        }
        int space = slot.getMaxStackSize() - slot.getAmount();
        if (space <= 0) {
            return; // full slot: vanilla swaps, as with any full stack
        }
        int moved = Math.min(space, event.getClick() == ClickType.LEFT ? cursor.getAmount() : 1);
        ItemStack merged = slot.clone();
        FoodStacks.mergeInto(merged, cursor);
        merged.setAmount(slot.getAmount() + moved);
        ItemStack rest = cursor.clone();
        rest.setAmount(cursor.getAmount() - moved);

        event.setCancelled(true);
        event.setCurrentItem(merged);
        event.getView().setCursor(rest.getAmount() > 0 ? rest : null);
    }

    // ---- pickup ---------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // On Paper getItemStack() mirrors the entity's live stack, the very stack vanilla adds after this event,
        // so re-dating it in place is enough. Never setItemStack here: that swaps in a copy while vanilla keeps
        // adding (and shrinking) the old one, which would duplicate the rest of a partial pickup.
        redateAgainst(player.getInventory(), event.getItem().getItemStack());
    }

    // ---- hopper ---------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        ItemStack moving = event.getItem();
        if (redateAgainst(event.getDestination(), moving)) {
            event.setItem(moving);
        }
    }

    /**
     * Finds the first slot of {@code inv} with room that holds no identical stack but merges with
     * {@code incoming} by TFC's rule, writes the merged food state into that slot and into {@code incoming}
     * (both now identical, so vanilla stacks them). Returns false and changes nothing when an identical stack
     * with room exists already, or no slot merges.
     */
    boolean redateAgainst(Inventory inv, ItemStack incoming) {
        if (!dated(incoming)) {
            return false;
        }
        ItemStack[] contents = inv.getStorageContents();
        int target = -1;
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s == null || s.isEmpty() || s.getAmount() >= s.getMaxStackSize() || s.getType() != incoming.getType()) {
                continue;
            }
            if (s.isSimilar(incoming)) {
                return false; // vanilla stacks it already
            }
            if (target < 0 && dated(s) && food.canMerge(s, incoming)) {
                target = i;
            }
        }
        if (target < 0) {
            return false;
        }
        ItemStack merged = contents[target].clone();
        FoodStacks.mergeInto(merged, incoming);
        inv.setItem(target, merged);
        FoodStacks.mergeInto(incoming, merged);
        return true;
    }

    private static boolean dated(ItemStack stack) {
        return stack != null && !stack.isEmpty() && FoodPdc.has(stack);
    }
}
