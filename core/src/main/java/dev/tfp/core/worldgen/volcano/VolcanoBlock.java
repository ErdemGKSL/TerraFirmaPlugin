/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

/**
 * One block a volcano or hot spring feature places.
 *
 * @param kind what is placed
 * @param rock the rock index ({@code PlaceholderRocks.names()} order) for the kinds with a rock, else -1
 * @param gem  the gem of a {@link VolcanoKind#GEM_ORE}, else null
 */
public record VolcanoBlock(int x, int y, int z, VolcanoKind kind, int rock, Gem gem) {
    public VolcanoBlock {
        if (kind.hasRock() != (rock >= 0)) {
            throw new IllegalArgumentException("rock " + rock + " does not fit " + kind);
        }
        if ((kind == VolcanoKind.GEM_ORE) != (gem != null)) {
            throw new IllegalArgumentException("gem " + gem + " does not fit " + kind);
        }
    }

    /** Whether the block is inside chunk {@code (chunkX, chunkZ)}. */
    public boolean inChunk(int chunkX, int chunkZ) {
        return (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
}
