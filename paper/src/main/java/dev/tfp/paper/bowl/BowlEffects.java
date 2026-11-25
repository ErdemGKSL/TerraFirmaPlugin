/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/BowlBlockEntityRenderer.java @ c3b4d9536
 */
package dev.tfp.paper.bowl;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** What a bowl does in the world besides storing the powder: its layer, the sound. Region thread. */
public interface BowlEffects {
    /** Draws (or removes, for an empty stack) the powder layer of the bowl at {@code block}. */
    void show(Block block, ItemStack content);

    /** Removes the layer (the bowl is gone). */
    void hide(Block block);

    /** {@code SoundEvents.SAND_PLACE}, played at every use. */
    void sand(Block block);

    /** The place sound of a bowl of this wood or stone kind. */
    void placed(Block block, boolean wooden);
}
