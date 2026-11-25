/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PowerLoomBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.paper.powerloom;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loom.LoomEffects;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/** TFC {@code PowerLoomBlock.getStateForPlacement}: the loom faces the way the player looks' opposite (the horizontal direction). */
final class PowerLoomListener implements Listener {
    private final CustomBlocks blocks;
    private final LoomEffects effects;
    private final Scheduler scheduler;

    PowerLoomListener(CustomBlocks blocks, LoomEffects effects, Scheduler scheduler) {
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
        Optional<BlockStateRef> state = blocks.stateAt(placed).filter(s -> s.id().equals(PowerLoomDevice.ID));
        if (state.isEmpty()) {
            return;
        }
        effects.state(placed, state.get().with(PowerLoomDevice.FACING, facing(event.getPlayer().getLocation().getYaw())));
    }

    /** {@code context.getHorizontalDirection()}, the way the player looks: yaw 0 looks south, 90 west, 180 north, 270 east. */
    static String facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 1 -> "west";
            case 2 -> "north";
            case 3 -> "east";
            default -> "south";
        };
    }
}
