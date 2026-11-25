/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (FIRE_EXTINGUISH, canSurvive)
 */
package dev.tfp.paper.lamp;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;

/** What a lamp does to the world (the Paper side of {@link LampDevice}). Every call is safe from the block's region thread. */
public interface LampEffects {
    /** Writes the custom block state (lit, hanging) on the block, next tick, if it is still a lamp. */
    void show(Block block, BlockStateRef state);

    /** TFC {@code Helpers.playSound(level, pos, SoundEvents.FIRE_EXTINGUISH)}: a lamp is put out. */
    void extinguished(Block block);

    /** The bucket sound of a fluid moved between the lamp and a held container. */
    void transfer(Block block, FluidAmount fluid, boolean intoItem);

    /** Calls the lamp back (a neighbour update) in {@code ticks} ticks while its chunk stays loaded. */
    void callbackIn(Block block, long ticks);

    /** Removes the lamp (it lost its support), next tick; the block drops its item. */
    void breakBlock(Block block);
}
