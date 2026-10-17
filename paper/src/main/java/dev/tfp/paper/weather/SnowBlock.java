/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import java.util.Objects;
import org.bukkit.block.data.BlockData;

/**
 * A block as the snow pipeline compares and writes it: the block data plus, for a snow or ice pile, the blocks the pile
 * remembers (TFC's {@code PileBlockEntity} hidden states). The pile's placeholder is a vanilla snow layer or ice block,
 * so the memory is what tells a pile from plain snow or ice; {@link WorldBlocks} keeps it in the chunk.
 *
 * @param data         the block
 * @param pile         what the pile covers, or {@code null} when the block is not a pile
 * @param restoreAbove written only: after {@code data}, put this block above if that position is air (a pile giving
 *                     back the block it absorbed); {@code null} on every read
 */
public record SnowBlock(BlockData data, Pile pile, BlockData restoreAbove) {
    public SnowBlock {
        Objects.requireNonNull(data, "data");
    }

    /** A plain block. */
    public static SnowBlock of(BlockData data) {
        return new SnowBlock(data, null, null);
    }

    /**
     * What a pile covers.
     *
     * @param ground the block the pile replaced
     * @param above  the block above it that the pile absorbed (the top half of a plant), or {@code null}; when a pile is
     *               written with one, {@link WorldBlocks} removes that block first
     */
    public record Pile(BlockData ground, BlockData above) {
        public Pile {
            Objects.requireNonNull(ground, "ground");
        }
    }
}
