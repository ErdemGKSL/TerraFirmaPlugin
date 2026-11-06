/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent;
import java.util.function.Consumer;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * The server events of the ore overlays (PAR-INFRA-16); each handler only forwards a few values, on the thread the event
 * fires on (the block's or entity's region thread).
 *
 * <ul>
 *   <li>dig progress ({@link BlockBreakProgressUpdateEvent}, the server's own progress, the value vanilla sends as
 *       {@code BLOCK_DESTRUCTION}): the block's overlays swap to the cracked variant of the stage, so cracks are never
 *       hidden under the specks (S2 verdict); an abort or the break restores them;</li>
 *   <li>a falling block carrying an ore: its overlays ride it ({@link OreOverlayService#fallingSpawned});</li>
 *   <li>chunk unload drops the table, quit drops the viewer, world load registers the world's key.</li>
 * </ul>
 */
public final class OreOverlayListener implements Listener {
    private final OreOverlayService service;
    private final OreBlockSource source;
    private final Consumer<java.util.UUID> quit;

    public OreOverlayListener(OreOverlayService service, OreBlockSource source, Consumer<java.util.UUID> quit) {
        this.service = service;
        this.source = source;
        this.quit = quit;
    }

    /** The crack stage of a progress value, as the S2 spike measured it ({@code <= 0}: none). */
    static int stage(float progress) {
        return progress <= 0 || progress >= 1 ? -1 : Math.min(9, (int) (progress * 10));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProgress(BlockBreakProgressUpdateEvent event) {
        Block b = event.getBlock();
        service.crack(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), stage(event.getProgress()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        Block b = event.getBlock();
        service.crack(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        service.crack(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof FallingBlock falling) {
            String id = source.blockId(falling.getBlockData());
            int kind = id == null ? -1 : service.data().kind(id);
            if (kind >= 0) {
                service.fallingSpawned(falling.getEntityId(), kind);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            service.fallingRemoved(event.getEntity().getEntityId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        service.serverChunkUnloaded(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        quit.accept(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        service.world(event.getWorld().getKey().toString(), event.getWorld().getUID());
    }
}
