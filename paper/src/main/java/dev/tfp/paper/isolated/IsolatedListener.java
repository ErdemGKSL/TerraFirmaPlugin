/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536
 */
package dev.tfp.paper.isolated;

import dev.tfp.paper.scheduler.Scheduler;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Feeds TFC's two isolated-position sources to the {@link IsolatedService}: {@code onBlockPlace} (an entity placed a
 * block; any game mode, TFC exempts none) and {@code onNeighborUpdate} (a block was notified of a neighbour change;
 * Bukkit's form is {@code BlockPhysicsEvent} for the notified block, so breaking the support of a placed raw rock, an
 * explosion or a piston next to one queues it). Thin: a tag filter and an enqueue.
 *
 * <p>A placement is handed to a region task after the event, so the check sees the event's final outcome (a cancelled
 * or reverted placement finds no raw rock there); the region scheduler never runs it inline, so it is the next tick,
 * as TFC's tracker.
 */
public final class IsolatedListener implements Listener {
    private final IsolatedService service;
    private final Scheduler scheduler;

    public IsolatedListener(IsolatedService service, Scheduler scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        scheduler.region(block.getLocation(), () -> service.request(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        service.request(event.getBlock());
    }
}
