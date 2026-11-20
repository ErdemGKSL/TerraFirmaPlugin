/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemplace;

import org.bukkit.block.Block;

/**
 * Writes snow piles for a player-placed snow item. Provided as a service by the weather blocks (which own the pile memory of
 * what a pile covers); without one the snow item stays vanilla. Region thread of the block.
 */
public interface SnowPilePlacer {
    /** Replaces {@code block} with a snow pile that remembers it (and a snow-pileable block above); false when refused. */
    boolean placePile(Block block);

    /** Adds one layer to the snow pile at {@code block}; false when refused. */
    boolean addLayer(Block block);
}
