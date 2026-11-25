/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java @ c3b4d9536 (getStateForPlacement: the facing is
 *       the placing player's horizontal direction)
 */
package dev.tfp.paper.largevessel;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/** TFC {@code LargeVesselBlock.getStateForPlacement}: a placed vessel faces the way the player looks. */
final class LargeVesselListener implements Listener {
    private final CustomBlocks blocks;
    private final PaperLargeVesselEffects effects;
    private final Scheduler scheduler;

    LargeVesselListener(CustomBlocks blocks, PaperLargeVesselEffects effects, Scheduler scheduler) {
        this.blocks = blocks;
        this.effects = effects;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!scheduler.isOwner(placed.getWorld(), placed.getX() >> 4, placed.getZ() >> 4)) {
            return;
        }
        Optional<BlockStateRef> state = blocks.stateAt(placed).filter(s -> LargeVesselDevice.covers(s.id()));
        if (state.isEmpty()) {
            return;
        }
        effects.facing(placed, facing(event.getPlayer().getLocation().getYaw()));
    }

    /** Yaw 0 looks south: the horizontal direction (not its opposite, unlike the loom). */
    static String facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 1 -> "west";
            case 2 -> "north";
            case 3 -> "east";
            default -> "south";
        };
    }
}
