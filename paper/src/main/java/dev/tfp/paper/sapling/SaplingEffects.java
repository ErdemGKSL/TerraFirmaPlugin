/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java @ c3b4d9536 (the world reads and writes of
 *       randomTick and advanceTree)
 */
package dev.tfp.paper.sapling;

import dev.tfp.core.worldgen.tree.Tree;
import dev.tfp.core.worldgen.tree.Wood;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import org.bukkit.block.Block;

/** Everything the sapling growth needs from the world. Every method runs on the region thread of the block it is given. */
public interface SaplingEffects {
    /** The custom block state at {@code block}, or empty for a vanilla block. */
    Optional<BlockStateRef> blockAt(Block block);

    /** The light at the block above the sapling ({@code getMaxLocalRawBrightness(pos.above())}, without the night darkening). */
    int lightAbove(Block sapling);

    /**
     * The tree the wood's feature would place with the sapling removed, or empty when the feature refuses the position
     * (TFC then puts the sapling back, so it keeps waiting).
     */
    Optional<Tree> plan(Block sapling, Wood wood, long seed);

    /** Replaces the sapling with the planned tree. */
    void grow(Block sapling, Tree tree);

    /** Lists the block for catch-up on chunk load. */
    void track(Block block);
}
