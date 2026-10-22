/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.paper.scheduler.Scheduler;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The device callbacks that come from Bukkit events rather than from the {@code tfp:device} CraftEngine behaviour
 * (PAR-INFRA-02): a left click ({@link DeviceHandler#attack}), a placement ({@link DeviceHandler#placed}) and the
 * dropped item of a {@link DeviceKind.RemoveBehaviour#SAVE} device (PAR-INFRA-18). All only hand over to the
 * {@link DeviceService} on the region thread.
 *
 * <ul>
 *   <li>Placement is handed to a region task after the event, so it sees the final outcome of the event (a later
 *       cancellation reverts the block and the task finds no device). The placed stack is copied during the event,
 *       before the server uses it up. Order with CraftEngine 26.9.2 ({@code BlockItemBehavior.place}, read from its
 *       bytecode by PAR-INFRA-18): the block is set first, which creates the block entity and stamps a fresh record
 *       ({@code DeviceController.onLoad}); then {@code BlockPlaceEvent} and {@code CustomBlockPlaceEvent} fire; if
 *       neither is cancelled, {@code loadCustomDataFromItem} restores a SAVE record from the item and the stack
 *       shrinks, all in the same call. The region task runs on a later tick on Paper and on Folia (the region
 *       scheduler never runs a task inline), so {@code placed} always sees the stamped or restored record.</li>
 *   <li>Attack runs inline when the event thread owns the block (always on Paper; on Folia the player's region),
 *       otherwise on the block's region. As vanilla, which only calls {@code Block.attack} from a survival/adventure
 *       start-destroy that was not refused: skipped in creative and when the click was denied.</li>
 * </ul>
 */
public final class DeviceListener implements Listener {
    private final DeviceService devices;
    private final Scheduler scheduler;

    public DeviceListener(DeviceService devices, Scheduler scheduler) {
        this.devices = devices;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack placed = event.getItemInHand().clone();
        scheduler.region(block.getLocation(), () -> devices.placed(block, player, hand, placed));
    }

    /** A SAVE device's own drop gets its record; the item's region thread, which is also the removed block's. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        devices.stampSavedDrop(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.useInteractedBlock() == Event.Result.DENY
                || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            devices.attack(block, player);
        } else {
            scheduler.region(block.getLocation(), () -> devices.attack(block, player));
        }
    }
}
