/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

/**
 * One block a volcanic feature of {@link VolcanicFeatures} places.
 *
 * @param kind what is placed
 * @param rock the rock index ({@code PlaceholderRocks.names()} order) for the kinds with a rock, else -1
 * @param gem  the gem of a {@link VolcanicKind#GEM_ORE}, else null
 */
public record VolcanicBlock(int x, int y, int z, VolcanicKind kind, int rock, Gem gem) {
    public VolcanicBlock {
        if (kind.hasRock() != (rock >= 0)) {
            throw new IllegalArgumentException("rock " + rock + " does not fit " + kind);
        }
        if ((kind == VolcanicKind.GEM_ORE) != (gem != null)) {
            throw new IllegalArgumentException("gem " + gem + " does not fit " + kind);
        }
    }

    /** Whether the block is inside chunk {@code (chunkX, chunkZ)}. */
    public boolean inChunk(int chunkX, int chunkZ) {
        return (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
}
