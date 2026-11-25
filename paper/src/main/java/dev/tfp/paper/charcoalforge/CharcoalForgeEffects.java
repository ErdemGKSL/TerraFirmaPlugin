/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoalforge;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** PAR-METAL-08: what the charcoal forge does to the world, behind an interface for the tests. Region thread of the block. */
public interface CharcoalForgeEffects {
    /** Shows the {@code heat_level} of the forge block (TFC {@code setBlockAndUpdate(HEAT)}). */
    void show(Block block, int heatLevel);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}. */
    void give(Player player, ItemStack stack);

    /** Queues the device router's update for the block in {@code ticks} ticks (only the latest per block runs). */
    void callbackIn(Block block, long ticks);
}
