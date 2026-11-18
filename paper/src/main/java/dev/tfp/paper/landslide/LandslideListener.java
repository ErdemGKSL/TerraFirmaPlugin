/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onBlockPlace, onNeighborUpdate)
 */
package dev.tfp.paper.landslide;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Feeds TFC's two landslide sources to the {@link LandslideService}: {@code onBlockPlace} (an entity placed a block, in
 * any game mode: TFC exempts none) and {@code onNeighborUpdate} (Bukkit's {@code BlockPhysicsEvent} for the notified
 * block, so removing the support of a dirt block, an explosion or a piston next to one queues it). Thin: a filter and an
 * enqueue, both on the owner thread of the block.
 */
public final class LandslideListener implements Listener {
    private final LandslideService service;

    public LandslideListener(LandslideService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        service.requestPlaced(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        service.request(event.getBlock());
    }
}
