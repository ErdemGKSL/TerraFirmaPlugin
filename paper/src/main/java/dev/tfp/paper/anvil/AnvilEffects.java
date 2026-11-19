/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/AnvilBlockEntityRenderer.java @ c3b4d9536 (what is drawn)
 */
package dev.tfp.paper.anvil;

import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The world effects of the anvil device (PAR-METAL-03). Called on the region thread that owns the block.
 */
public interface AnvilEffects {
    /**
     * What an anvil shows.
     *
     * @param tier     the anvil tier (0 for a rock anvil, which draws its items higher)
     * @param rotation the facing's 2D data value (south 0, west 1, north 2, east 3)
     * @param hammer   the hammer slot
     * @param main     the first input
     * @param second   the second input
     * @param catalyst the flux slot
     */
    record View(int tier, int rotation, ItemStack hammer, ItemStack main, ItemStack second, ItemStack catalyst) {
        /** The stack of {@code slot} ({@link dev.tfp.core.anvil.AnvilSlots}). */
        public ItemStack stack(int slot) {
            return switch (slot) {
                case dev.tfp.core.anvil.AnvilSlots.INPUT_MAIN -> main;
                case dev.tfp.core.anvil.AnvilSlots.INPUT_SECOND -> second;
                case dev.tfp.core.anvil.AnvilSlots.HAMMER -> hammer;
                case dev.tfp.core.anvil.AnvilSlots.CATALYST -> catalyst;
                default -> ItemStack.empty();
            };
        }
    }

    /** Draws (or updates) the contents on the top face; an empty slot's display is removed. */
    void show(Block block, View view);

    /** Removes every display of the anvil at {@code block}. */
    void hide(Block block);

    /** Writes the block state {@code wanted} (the placement facing) over the anvil at {@code block}, on the next tick. */
    void state(Block block, BlockStateRef wanted);
}
