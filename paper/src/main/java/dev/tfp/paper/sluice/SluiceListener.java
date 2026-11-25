/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java @ c3b4d9536 (getStateForPlacement, setPlacedBy)
 */
package dev.tfp.paper.sluice;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * The placement of the two-block sluice (PAR-PROC-13). TFC places the block facing the player's horizontal direction and
 * needs both its own and the next block free ({@code getStateForPlacement}); {@code setPlacedBy} then puts the lower half
 * in front of it. CraftEngine places one block, so the event only decides and enqueues; the region commit writes both
 * halves.
 */
final class SluiceListener implements Listener {
    private final CustomBlocks blocks;
    private final SluiceEffects effects;
    private final Scheduler scheduler;

    SluiceListener(CustomBlocks blocks, SluiceEffects effects, Scheduler scheduler) {
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
        Optional<BlockStateRef> state = blocks.stateAt(placed).filter(s -> s.id().startsWith(SluiceDevice.ID + "/"));
        if (state.isEmpty()) {
            return;
        }
        BlockFace facing = facing(event.getPlayer().getLocation().getYaw());
        Block lower = placed.getRelative(facing);
        if (!(lower.isEmpty() || lower.isReplaceable())) {
            event.setCancelled(true); // TFC: getStateForPlacement answers null, the block is not placed
            return;
        }
        effects.place(placed, state.get(), facing);
    }

    /** TFC {@code context.getHorizontalDirection()}: the way the player looks, from the yaw. */
    static BlockFace facing(float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (quarter) {
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
    }
}
