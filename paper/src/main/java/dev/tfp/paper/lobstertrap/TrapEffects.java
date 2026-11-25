/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java @ c3b4d9536 (tryBait, catchEntity: the sounds,
 *       the bubbles, updateTrapState)
 */
package dev.tfp.paper.lobstertrap;

import dev.tfp.core.fishing.LobsterTrap;
import org.bukkit.block.Block;

/** What the trap does to the world around it (PAR-FAUNA-19). Applied on the block's region; the device only decides. */
public interface TrapEffects {
    /** TFC's sounds. */
    enum Sound {
        /** {@code SoundEvents.BUNDLE_INSERT}: bait put in. */
        BAIT,
        /** {@code SoundEvents.IRON_DOOR_CLOSE}: a mob caught. */
        CAUGHT
    }

    /** Sets the block state's {@code trap} property to {@code state} (TFC {@code updateTrapState}). */
    void show(Block block, LobsterTrap.State state);

    void sound(Block block, Sound sound);

    /** TFC {@code bubbles(level, count)}: the explicit feedback of baiting (4) and catching (12). */
    void bubbles(Block block, int count);

    /** Records whether the trap at {@code block} is baited with a free slot, for the animals that look for one. */
    void baited(Block block, boolean baited);

    /** The trap is gone. */
    void removed(Block block);
}
