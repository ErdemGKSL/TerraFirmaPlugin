/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

/**
 * One small block that a worldgen feature puts on the ground: a loose rock, a small ore piece above a vein, or a shore
 * groundcover (driftwood, shells, seaweed, sticks). Immutable.
 *
 * @param x           the block x
 * @param y           the block y: the first position above the column's top solid block (TFC's {@code OCEAN_FLOOR_WG})
 * @param z           the block z
 * @param kind        what the block is
 * @param block       the block id in TFC's naming with our namespace ({@code tfp:rock/loose/granite},
 *                    {@code tfp:ore/small_malachite}, {@code tfp:groundcover/driftwood})
 * @param count       the number of pieces ({@code count} of a loose rock, 1 or 2 at worldgen); 1 for the other kinds
 * @param waterlogged whether the position holds water, so the block is filled with it ({@code FluidHelpers.fillWithFluid})
 */
public record Groundcover(int x, int y, int z, Kind kind, String block, int count, boolean waterlogged) {
    /** The kinds of groundcover the worldgen places. */
    public enum Kind {
        /** {@code tfp:rock/loose/<rock>}: the surface rock of the column. */
        LOOSE_ROCK,
        /** {@code tfp:rock/mossy_loose/<rock>}: a loose rock in wet climates. */
        MOSSY_LOOSE_ROCK,
        /** {@code tfp:ore/small_<ore>}: the surface indicator of a vein. */
        SMALL_ORE,
        DRIFTWOOD,
        CLAM,
        MOLLUSK,
        MUSSEL,
        SEAWEED,
        STICK
    }

    public Groundcover {
        if (count < 1 || count > 3) {
            throw new IllegalArgumentException("count must be in [1, 3]: " + count);
        }
    }

    /** Whether the block lies in the chunk {@code (chunkX, chunkZ)}. */
    public boolean inChunk(int chunkX, int chunkZ) {
        return x >> 4 == chunkX && z >> 4 == chunkZ;
    }
}
