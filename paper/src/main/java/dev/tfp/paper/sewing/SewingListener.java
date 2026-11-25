/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/SewingTableBlock.java @ c3b4d9536 (useItemOn),
 *       src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java @ c3b4d9536 (SewingInputSlot.mayPickup,
 *       onSlotTake, moveStack)
 */
package dev.tfp.paper.sewing;

import dev.tfp.core.sewing.SewingScreen;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-PROC-07: opens the sewing table on a right-click on a {@code tfp:wood/sewing_table/<wood>} block, keeps used cloth,
 * yarn and needle in their slots, and takes the result (so the take is seen and the board resets, TFC {@code onSlotTake}).
 * Runs after {@code GuiListener} (LOW), which already refuses everything else on the screen.
 */
public final class SewingListener implements Listener {
    public static final String BLOCK_PREFIX = "tfp:wood/sewing_table/";

    private final SewingMenu.Services services;
    private final CustomBlocks blocks;

    public SewingListener(SewingMenu.Services services, CustomBlocks blocks) {
        this.services = services;
        this.blocks = blocks;
    }

    /** True for a sewing table block. */
    public static boolean isTable(CustomBlocks blocks, Block block) {
        return blocks.stateAt(block).map(BlockStateRef::id).filter(id -> id.startsWith(BLOCK_PREFIX)).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (player.getGameMode() == GameMode.SPECTATOR || !isTable(blocks, block)) {
            return;
        }
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return; // vanilla: sneaking with an item places it
        }
        event.setCancelled(true);
        SewingMenu.open(services, player, block);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        SewingMenu menu = SewingMenu.of(session);
        if (menu == null || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        if (raw == SewingScreen.SLOT_RESULT) {
            e.setCancelled(true);
            if (!menu.stillValid()) {
                menu.closeNextTick();
            } else if (take(e, player, top)) {
                menu.taken();
            }
            return;
        }
        if (raw >= 0 && raw < top.getSize() && SewingScreen.isInput(raw)) {
            ItemStack there = top.getItem(raw);
            if (!menu.mayTake(there) && !isMerge(e)) {
                e.setCancelled(true); // TFC mayPickup: used cloth, yarn and needle stay
                return;
            }
        }
        if (raw >= top.getSize() && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || raw >= 0 && raw < top.getSize() && SewingScreen.isInput(raw)) {
            menu.updateNextTick(); // the stack moves after this event
        }
    }

    /** Putting more of the same item on a stack in a locked slot only adds to it. */
    private static boolean isMerge(InventoryClickEvent e) {
        return switch (e.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        SewingMenu menu = SewingMenu.of(GuiService.session(e.getView().getTopInventory()));
        if (menu != null) {
            menu.updateNextTick();
        }
    }

    /** Moves the result out as the click asks; true when it left the result slot. */
    static boolean take(InventoryClickEvent e, Player player, Inventory top) {
        ItemStack out = top.getItem(SewingScreen.SLOT_RESULT);
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
                top.setItem(SewingScreen.SLOT_RESULT, null);
                return true;
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                ItemStack left = moveToPlayer(player.getInventory(), out);
                if (left != null && left.getAmount() == out.getAmount()) {
                    return false;
                }
                top.setItem(SewingScreen.SLOT_RESULT, left);
                return true;
            }
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                player.getWorld().dropItem(player.getEyeLocation(), out);
                top.setItem(SewingScreen.SLOT_RESULT, null);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Vanilla {@code moveItemStackTo} into the player slots, hotbar from the last slot first, then the main inventory. */
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

    /** Used by tests: the optional menu of a player's open screen. */
    static Optional<SewingMenu> menuOf(Player player) {
        return Optional.ofNullable(SewingMenu.of(GuiService.session(player.getOpenInventory().getTopInventory())));
    }
}
