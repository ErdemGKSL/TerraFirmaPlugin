/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 * getSlotStackLimit, tick),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement)
 */
package dev.tfp.paper.loom;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/** TFC {@code TFCLoomBlock.getStateForPlacement}: the loom faces the player (opposite of the horizontal direction). */
final class LoomListener implements Listener {
    private final CustomBlocks blocks;
    private final LoomEffects effects;
    private final Scheduler scheduler;

    LoomListener(CustomBlocks blocks, LoomEffects effects, Scheduler scheduler) {
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
        Optional<BlockStateRef> state = blocks.stateAt(placed).filter(s -> s.id().startsWith(LoomDevice.ID + "/"));
        if (state.isEmpty()) {
            return;
        }
        effects.state(placed, state.get().with(LoomDevice.FACING, facing(event.getPlayer().getLocation().getYaw())));
    }

    /** The opposite of the way the player looks: yaw 0 looks south, so the loom faces north. */
    static String facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
            case 1 -> "east";
            case 2 -> "south";
            case 3 -> "west";
            default -> "north";
        };
    }
}
