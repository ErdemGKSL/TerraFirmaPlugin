/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 * getSlotStackLimit, tick),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement)
 */
package dev.tfp.paper.loom;

import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;

/** What the loom device does to the world (PAR-PROC-06). Region thread of the block. */
public interface LoomEffects {
    /** TFC {@code TFCSounds.LOOM_WEAVE}: pitch 1 + (r - r) / 16. */
    void weave(Block block);

    /** Calls the device back after {@code ticks} so a pushed step is counted on time; one pending call per loom. */
    void wake(Block block, long ticks);

    /** Writes the placement facing over the loom at {@code block}, on the next tick (TFC {@code getStateForPlacement}). */
    void state(Block block, BlockStateRef wanted);
}
