/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/mixin/SnowLayerBlockMixin.java @ c3b4d9536 (onDestroyedByPlayer, canSurvive)
 */
package dev.tfp.paper.worldrules;

import dev.tfp.core.worldrules.WorldRules;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Vanilla snow layers (CraftEngine snow piles are PAR-CLIM-04's): a player out of creative breaking a snow layer of more
 * than one layer takes one layer. The break itself runs as vanilla (drops of the whole block as TFC, tool wear,
 * statistics); the region thread then puts the remaining layers back into the hole if it is still empty. Placing a snow
 * layer that would make two or more layers on TFC leaves is refused (TFC's leaves hold one layer only).
 */
final class SnowLayerListener implements Listener {
    private final Scheduler scheduler;
    private final Predicate<Block> custom;
    private final Predicate<Block> tfcLeaves;

    SnowLayerListener(Scheduler scheduler, Predicate<Block> custom, Predicate<Block> tfcLeaves) {
        this.scheduler = scheduler;
        this.custom = custom;
        this.tfcLeaves = tfcLeaves;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SNOW || !(block.getBlockData() instanceof Snow snow) || custom.test(block)) {
            return;
        }
        int left = WorldRules.snowLayersAfterBreak(snow.getLayers(), event.getPlayer().getGameMode() == GameMode.CREATIVE);
        if (left == 0) {
            return;
        }
        Snow next = (Snow) snow.clone();
        next.setLayers(left);
        scheduler.region(block.getLocation(), () -> {
            if (block.getType().isAir()) { // still the hole the break left
                block.setBlockData(next, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!(block.getBlockData() instanceof Snow snow) || custom.test(block)) {
            return;
        }
        if (!WorldRules.snowPlacementAllowed(snow.getLayers(), tfcLeaves.test(block.getRelative(BlockFace.DOWN)))) {
            event.setCancelled(true);
        }
    }
}
