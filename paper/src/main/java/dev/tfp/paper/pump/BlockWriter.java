/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pump;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import java.util.function.Supplier;
import org.bukkit.block.Block;

/** Writes a custom block state over a block of the same block (no physics). Region thread. */
@FunctionalInterface
public interface BlockWriter {
    void set(Block block, BlockStateRef next);

    /** The real writer: the state's server block data from the visuals adapter. */
    static BlockWriter visuals(Supplier<BlockVisuals> visuals) {
        return (block, next) -> visuals.get().blockData(next).ifPresent(d -> block.setBlockData(d, false));
    }
}
