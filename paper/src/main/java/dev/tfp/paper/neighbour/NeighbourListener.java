/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Feeds neighbour changes to the {@link NeighbourService} (PAR-INFRA-10). Thin: no neighbour reads, only a cheap id
 * filter and an enqueue. {@code BlockPhysicsEvent} is the Bukkit form of vanilla's neighbour/shape update (fired for
 * the notified block, which is what TFC's {@code updateShape} receives); a placed block gets no physics event for
 * itself, hence {@code BlockPlaceEvent}.
 */
public final class NeighbourListener implements Listener {
    private final NeighbourService service;

    public NeighbourListener(NeighbourService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        service.notified(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        service.placed(event.getBlockPlaced());
    }
}
