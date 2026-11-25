/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (stoneMatcher, insideChimney,
 *       center: the block tests of the multiblock)
 */
package dev.tfp.paper.bloomery;

import java.util.Optional;
import org.bukkit.block.Block;

/**
 * What the bloomery multiblock reads about a block (PAR-METAL-14), behind an interface for the tests. Region thread of the
 * block; a block of another region or an unloaded chunk answers {@link #readable} false and counts as no match.
 */
public interface BloomeryProbe {
    /** Whether this thread may read {@code block} (loaded, and in the region the caller runs on). */
    boolean readable(Block block);

    /** The CraftEngine block id at {@code block}, empty for a vanilla block. */
    Optional<String> blockId(Block block);

    /** Whether {@code block} is air (no custom block, no solid vanilla block). */
    boolean air(Block block);

    /** Whether {@code block} is in {@code #tfc:bloomery_insulation}. */
    boolean insulation(Block block);
}
