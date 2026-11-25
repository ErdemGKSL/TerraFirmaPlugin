/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.placed;

import dev.tfp.core.placed.PlacedState;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** What a placed item block shows and plays in the world. Region thread of the block. */
public interface PlacedItemEffects {
    /** Draws the four slots ({@code slots}, empty stacks for none) with the state's rotations and large flag. */
    void show(Block block, PlacedState state, List<ItemStack> slots);

    /** Removes everything {@link #show} drew. */
    void hide(Block block);

    /** The placement sound of a completed hold (no particles). */
    void placed(Block block);
}
