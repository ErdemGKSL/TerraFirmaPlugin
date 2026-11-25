/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.paper.leaves;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-GATHER-15: leaves a player places are persistent (TFC {@code PERSISTENT = context.getPlayer() != null}), so
 * {@link LeafDecay} never removes them. {@link BlockPlaceEvent} fires only for a player's placement, on the thread that owns the
 * block, so the one-property rewrite is applied right there (no neighbour scan, nothing to decide off-thread). Worldgen,
 * saplings and dispensers do not raise it and keep the default {@code persistent=false}.
 */
public final class PlacedLeaves implements Listener {
    private static final String PREFIX = "tfp:wood/leaves/";

    private final CustomBlocks blocks;

    public PlacedLeaves(CustomBlocks blocks) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    /** The persistent variant of a natural leaf state, or empty when {@code state} is not a non-persistent leaf block. */
    static Optional<BlockStateRef> persistent(BlockStateRef state) {
        if (!state.id().startsWith(PREFIX) || !"false".equals(state.properties().get("persistent"))) {
            return Optional.empty();
        }
        return Optional.of(state.with("persistent", "true"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        blocks.stateAt(block).flatMap(PlacedLeaves::persistent).ifPresent(state -> blocks.place(block, state, false));
    }
}
