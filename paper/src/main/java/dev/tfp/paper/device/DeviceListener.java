/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.paper.scheduler.Scheduler;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The two device callbacks that come from Bukkit events rather than from the {@code tfp:device} CraftEngine behaviour
 * (PAR-INFRA-02): a left click ({@link DeviceHandler#attack}) and a placement ({@link DeviceHandler#placed}). Both only
 * hand over to the {@link DeviceService} on the block's region thread.
 *
 * <ul>
 *   <li>Placement runs one tick later: during {@code BlockPlaceEvent} CraftEngine has not stored the custom block and
 *       its block entity yet.</li>
 *   <li>Attack runs inline when the event thread owns the block (always on Paper; on Folia the player's region),
 *       otherwise on the block's region.</li>
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
        scheduler.region(block.getLocation(), () -> devices.placed(block, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
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
