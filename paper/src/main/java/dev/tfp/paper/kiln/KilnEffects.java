/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.kiln;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The world writes of a pit kiln besides its device record. Region thread of the block. */
public interface KilnEffects {
    /** The sounds TFC plays around the kiln. */
    enum Sound {
        /** {@code convertPlacedItemToPitKiln}: {@code GRASS_PLACE} at volume 0.5, pitch 1. */
        CONVERT,
        /** {@code playPlaceSound(SoundType.GRASS)}: a straw layer. */
        STRAW,
        /** {@code playPlaceSound(SoundType.WOOD)}: a log. */
        LOG,
        /** {@code convertPitKilnToPlacedItem(..., true)}: {@code FIRE_EXTINGUISH}. */
        EXTINGUISH
    }

    /**
     * Sets the kiln block to {@code stage} (TFC {@code setValue(STAGE, stage)}); {@code physics} for TFC's flag 3
     * ({@code setBlockAndUpdate}), without for its flag 10.
     */
    void stage(Block block, int stage, boolean physics);

    void sound(Block block, Sound sound);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    void give(Player player, ItemStack stack);

    /** TFC {@code Helpers.spawnItem}: an item at the block centre. */
    void spawn(Block block, ItemStack stack);
}
