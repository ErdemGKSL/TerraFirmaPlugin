/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/Container.java @ c3b4d9536 (quickMoveStack / moveStack: player
 *       slots shift-click into the container's slot range, container slots into the player inventory)
 */
package dev.tfp.paper.gui;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-03: click routing for {@link TfpGui} screens. Inventory events run on the clicking player's thread, so the
 * checks and the item moves happen inline; button handlers are handed to the player's entity scheduler (TFC sends a
 * {@code ScreenButtonPacket} that the server handles on its thread the same way).
 *
 * <ul>
 *   <li>Button slots: always cancelled; the press goes to the handler.</li>
 *   <li>Input slots: taking is free; putting in (place, swap with the cursor, number keys, drag) needs the slot's
 *       {@link SlotPredicate}.</li>
 *   <li>Output slots: taking only. Decoration slots: nothing.</li>
 *   <li>Shift-click from the player inventory: our own move into the input slots in {@link TfpGui#quickMoveOrder}
 *       (vanilla {@code moveItemStackTo}: merge into matching stacks first, then the first empty slot), checked per
 *       slot.</li>
 *   <li>A locked screen (TFC: sealed) refuses every container slot click and shift-clicks into it.</li>
 *   <li>Double-click collecting is refused, so it cannot pull items out of slots it may not touch.</li>
 * </ul>
 */
public final class GuiListener implements Listener {
    private final GuiService service;

    public GuiListener(GuiService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || !(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = e.getRawSlot();
        if (raw < 0) {
            return; // outside the window
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getClick() == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (raw < top.getSize()) {
            onTopClick(e, session, player, raw);
        } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            ItemStack moving = e.getCurrentItem();
            if (moving != null && !moving.isEmpty() && !session.gui().locked(session)) {
                e.setCurrentItem(quickMoveIn(session, moving));
            }
        }
    }

    private void onTopClick(InventoryClickEvent e, GuiSession session, Player player, int slot) {
        TfpGui gui = session.gui();
        TfpGui.Button button = gui.button(slot);
        if (button != null) {
            e.setCancelled(true);
            ClickType click = e.getClick();
            service.scheduler().entity(player, () -> {
                if (!session.closed()) {
                    button.handler().press(session, click);
                }
            }, () -> { });
            return;
        }
        TfpGui.SlotSpec spec = gui.slot(slot);
        if (spec == null || gui.locked(session)) {
            e.setCancelled(true);
            return;
        }
        ItemStack incoming = switch (e.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> e.getCursor();
            case HOTBAR_SWAP -> e.getClick() == ClickType.SWAP_OFFHAND
                ? player.getInventory().getItemInOffHand()
                : e.getHotbarButton() >= 0 ? player.getInventory().getItem(e.getHotbarButton()) : null;
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY,
                CLONE_STACK, NOTHING -> null;
            default -> {
                e.setCancelled(true); // anything we do not know (and pickup-to-bundle style actions) is refused
                yield null;
            }
        };
        if (incoming != null && !incoming.isEmpty()
            && (spec.kind() != TfpGui.Kind.INPUT || !spec.accepts().accepts(session, slot, incoming))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null) {
            return;
        }
        int size = e.getView().getTopInventory().getSize();
        ItemStack dragged = e.getOldCursor();
        for (int raw : e.getRawSlots()) {
            if (raw >= size) {
                continue;
            }
            TfpGui.SlotSpec spec = session.gui().slot(raw);
            if (spec == null || spec.kind() != TfpGui.Kind.INPUT || session.gui().locked(session)
                || !spec.accepts().accepts(session, raw, dragged)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && session.viewer() == e.getPlayer()) {
            session.close();
        }
    }

    /**
     * Moves {@code stack} into the session's input slots (vanilla {@code moveItemStackTo}, forward): first onto matching
     * stacks with room, then into the first accepting empty slot. Returns what is left (null when all went in).
     */
    static ItemStack quickMoveIn(GuiSession session, ItemStack stack) {
        Inventory inv = session.getInventory();
        List<Integer> order = session.gui().quickMoveOrder();
        ItemStack left = stack.clone();
        for (int slot : order) { // pass 1: merge
            if (left.isEmpty()) {
                break;
            }
            ItemStack there = inv.getItem(slot);
            if (there != null && !there.isEmpty() && there.isSimilar(left)
                && session.gui().slot(slot).accepts().accepts(session, slot, left)) {
                int room = Math.min(there.getMaxStackSize(), inv.getMaxStackSize()) - there.getAmount();
                int move = Math.min(room, left.getAmount());
                if (move > 0) {
                    there.setAmount(there.getAmount() + move);
                    inv.setItem(slot, there);
                    left.setAmount(left.getAmount() - move);
                }
            }
        }
        for (int slot : order) { // pass 2: the first empty slot takes up to a full stack
            if (left.isEmpty()) {
                break;
            }
            ItemStack there = inv.getItem(slot);
            if ((there == null || there.isEmpty()) && session.gui().slot(slot).accepts().accepts(session, slot, left)) {
                int move = Math.min(left.getAmount(), Math.min(left.getMaxStackSize(), inv.getMaxStackSize()));
                ItemStack placed = left.clone();
                placed.setAmount(move);
                inv.setItem(slot, placed);
                left.setAmount(left.getAmount() - move);
            }
        }
        return left.isEmpty() ? null : left;
    }
}
