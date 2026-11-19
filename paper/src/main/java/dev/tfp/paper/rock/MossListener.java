/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rock;

import dev.tfp.paper.scheduler.Scheduler;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Indexes placed mossy rock blocks and stores the unload tick (PAR-GATHER-06). */
final class MossListener implements Listener {
    private final MossService service;
    private final Scheduler scheduler;

    MossListener(MossService service, Scheduler scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        // CraftEngine finishes the custom state right after the event: look one tick later on the block's region
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1L, () -> service.trackIfMossy(block));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent event) {
        service.chunkUnloaded(event.getChunk());
    }
}
