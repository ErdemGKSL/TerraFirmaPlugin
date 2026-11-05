/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PAR-INFRA-33: keeps {@link TrackedBlocks} up to date on the region thread that fires the events (snapshot only; no
 * world change). Starting to mine (left click, block damage) records our block at that position for
 * {@link TrackedBlocks#MINING_NANOS}; breaking it records it for {@link TrackedBlocks#BROKEN_NANOS} (the break sound
 * packets follow in the same tick). Join and world change record each player's world; quit forgets it.
 */
public final class BlockSoundTracker implements Listener {
    private final BlockSoundRewrite rewrite;
    private final CustomBlocks blocks;
    private final LongSupplier clock;

    public BlockSoundTracker(BlockSoundRewrite rewrite, CustomBlocks blocks, LongSupplier clock) {
        this.rewrite = rewrite;
        this.blocks = blocks;
        this.clock = clock;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            track(event.getClickedBlock(), TrackedBlocks.MINING_NANOS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent event) {
        track(event.getBlock(), TrackedBlocks.MINING_NANOS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        track(event.getBlock(), TrackedBlocks.BROKEN_NANOS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rewrite.tracked().setWorld(event.getPlayer().getUniqueId(), event.getPlayer().getWorld().getUID());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        rewrite.tracked().setWorld(event.getPlayer().getUniqueId(), event.getPlayer().getWorld().getUID());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rewrite.forget(event.getPlayer().getUniqueId());
    }

    /** Records our block at {@code block}, if the table knows it. Region thread. */
    void track(Block block, long ttlNanos) {
        Optional<BlockStateRef> state;
        try {
            state = blocks.stateAt(block);
        } catch (IllegalStateException e) {
            return; // not the owning thread (should not happen for these events); the sound stays CraftEngine's
        }
        if (state.isEmpty() || !rewrite.table().contains(state.get().id())) {
            return;
        }
        rewrite.tracked().track(new TrackedBlocks.Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()),
                state.get().id(), ttlNanos, clock.getAsLong());
    }
}
