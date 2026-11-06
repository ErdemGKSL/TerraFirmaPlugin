/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

/**
 * An immutable copy of one chunk for {@link ExposedFaces#compute} (PAR-INFRA-16): the local indices of its ore blocks
 * ({@link OverlayKeys#local}, ascending) and the {@link CellLookup} codes of its own cells. Cells outside the chunk read
 * {@link CellLookup#UNKNOWN} (the 1-block border comes from the neighbour chunks' owners, {@link ExposedFaces#borderNeeds}).
 * Cells below {@link #minY()} read as solid, cells at or above {@link #maxY()} as open (the client draws a face against
 * the sky, never against the void floor). Read from any thread.
 */
public interface OreSnapshot extends CellLookup {
    int chunkX();

    int chunkZ();

    /** The world's minimum y. */
    int minY();

    /** The world's height limit (exclusive). */
    int maxY();

    /** The local indices of the ore blocks, ascending. */
    int[] ores();

    /** True if the world position lies in this chunk (any y). */
    default boolean inChunk(int x, int z) {
        return (x >> 4) == chunkX() && (z >> 4) == chunkZ();
    }
}
