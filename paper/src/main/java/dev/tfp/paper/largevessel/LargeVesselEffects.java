/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (onSeal, onUnseal: the
 *       open and close sounds), src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java @ c3b4d9536 (toggleSeal:
 *       the SEALED block state)
 */
package dev.tfp.paper.largevessel;

import org.bukkit.block.Block;

/** PAR-POT-06: what the large vessel does to the world; the Paper implementation runs it on the block's region. */
public interface LargeVesselEffects {
    /** Writes the sealed state onto the CraftEngine block at {@code block}, keeping its facing. */
    void show(Block block, boolean sealed);

    /** TFC {@code onSeal} ({@code CLOSE_VESSEL}) and {@code onUnseal} ({@code OPEN_VESSEL}) sounds. */
    void sound(Block block, boolean sealed);
}
