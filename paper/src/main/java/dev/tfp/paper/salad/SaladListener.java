/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (SALAD_BOWLS, Target.BOTH: the salad
 *       screen opens only while sneaking),
 *       src/main/java/net/dries007/tfc/common/container/SaladContainer.java @ c3b4d9536 (onSlotTake, quickMoveStack),
 *       src/main/java/net/dries007/tfc/common/component/Bowl.java @ c3b4d9536 (onItemUse: the bowl comes back after
 *       eating, in the hand when the stack is used up, else into the inventory; nothing in creative)
 */
package dev.tfp.paper.salad;

import dev.tfp.core.salad.SaladScreen;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-PROC-14: opens the salad screen on a sneaking right-click (in the air or on a block) with a salad bowl in either
 * hand, routes taking the salad (so the inputs shrink, TFC {@code onSlotTake}), recomputes the salad after every other
 * change, and gives the bowl back after a salad is eaten. Runs after {@code GuiListener} (LOW), which already refuses
 * everything else on the screen.
 */
public final class SaladListener implements Listener {
    private final SaladMenu.Services services;
    private final Function<String, Optional<ItemStack>> bowls;

    /** @param bowls makes one bowl from a stored bowl id ({@link SaladPdc#bowl}) */
    public SaladListener(SaladMenu.Services services, Function<String, Optional<ItemStack>> bowls) {
        this.services = services;
        this.bowls = bowls;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.isEmpty() || !player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR
                || !services.bowl().test(item)) {
            return;
        }
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack main = player.getInventory().getItemInMainHand();
            if (!main.isEmpty() && services.bowl().test(main)) {
                return; // the main hand's event already opened it
            }
        }
        if (GuiService.session(player.getOpenInventory().getTopInventory()) != null) {
            return;
        }
        e.setCancelled(true); // TFC returns success: no block use, no placement
        SaladMenu.open(services, player);
    }

    /**
     * Not {@code ignoreCancelled}: {@code GuiListener} cancels a cursor that is put on an output slot, but a cursor of
     * the same salad takes from the output in vanilla (and TFC), so this screen decides output clicks itself.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        SaladMenu menu = SaladMenu.of(session);
        if (menu == null || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (e.getRawSlot() == SaladScreen.OUTPUT_CHEST_SLOT) {
            e.setCancelled(true);
            int taken = take(e, player, menu);
            if (taken > 0) {
                menu.taken(taken);
            }
            return;
        }
        menu.queueUpdate();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        SaladMenu menu = SaladMenu.of(GuiService.session(e.getView().getTopInventory()));
        if (menu != null) {
            menu.queueUpdate();
        }
    }

    /**
     * Moves the salad out as the click asks and returns how many left the output slot. The salad is recomputed first, so
     * a take in the same tick as a change of the inputs takes what the inputs make now. Shift-clicks do nothing (TFC's
     * {@code moveStack} refuses the salad slot), and so do number-key swaps.
     */
    static int take(InventoryClickEvent e, Player player, SaladMenu menu) {
        Inventory top = e.getView().getTopInventory();
        menu.update();
        ItemStack out = top.getItem(SaladScreen.OUTPUT_CHEST_SLOT);
        if (out == null || out.isEmpty()) {
            return 0;
        }
        int count = out.getAmount();
        switch (e.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> {
                ItemStack cursor = player.getItemOnCursor();
                int amount;
                if (cursor == null || cursor.isEmpty()) {
                    amount = e.getClick() == ClickType.RIGHT ? (count + 1) / 2 : count; // vanilla right click takes half
                    ItemStack moved = out.clone();
                    moved.setAmount(amount);
                    player.setItemOnCursor(moved);
                } else if (cursor.isSimilar(out)) {
                    amount = Math.min(count, cursor.getMaxStackSize() - cursor.getAmount());
                    if (amount <= 0) {
                        return 0;
                    }
                    cursor.setAmount(cursor.getAmount() + amount);
                    player.setItemOnCursor(cursor);
                } else {
                    return 0;
                }
                return amount;
            }
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                int amount = e.getAction() == InventoryAction.DROP_ONE_SLOT ? 1 : count;
                ItemStack thrown = out.clone();
                thrown.setAmount(amount);
                player.getWorld().dropItem(player.getEyeLocation(), thrown);
                return amount;
            }
            default -> {
                return 0;
            }
        }
    }

    /** TFC {@code Bowl.onItemUse}: the bowl comes back after a salad is eaten (not in creative). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player player = e.getPlayer();
        Optional<String> bowlId = SaladPdc.bowl(e.getItem());
        if (bowlId.isEmpty() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Optional<ItemStack> bowl = bowls.apply(bowlId.get());
        if (bowl.isEmpty()) {
            return;
        }
        EquipmentSlot hand = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        // vanilla shrinks the stack after the event; the bowl goes in on the next tick on the player's thread
        services.scheduler().entityDelayed(player, 1, () -> giveBowl(player, hand, bowl.get()), () -> { });
    }

    static void giveBowl(Player player, EquipmentSlot hand, ItemStack bowl) {
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItem(hand);
        if (held.isEmpty()) {
            inv.setItem(hand, bowl); // the used-up stack becomes the bowl
        } else {
            SaladMenu.give(player, bowl);
        }
    }
}
