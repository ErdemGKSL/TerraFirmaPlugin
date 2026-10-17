/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.calendar.Season;

/**
 * M2-SEA-02: the seasonal remap of one chunk for one season, computed off-thread by
 * {@link SeasonalBiomeRemap#compute} and read by the packet thread. Immutable after construction (the arrays are never
 * written once the table is published).
 *
 * <p>Cells are indexed as in the chunk packet's biome palette: {@code cell = y << 4 | z << 2 | x} within a section of
 * 4 x 4 x 4 cells, so the column of a cell is {@code cell & 15}.
 *
 * @param base     per column ({@code z << 2 | x}): the index into {@code BiomeKeys.VANILLA} of the biome the server sends
 * @param variants per section and cell ({@code section * 64 + cell}): the variant index to send instead
 */
public record SeasonalChunkTable(int chunkX, int chunkZ, int minY, int sections, Season season, byte[] base,
        short[] variants) {
    /** Biome cells per section. */
    public static final int CELLS_PER_SECTION = 64;
    /** Biome columns per chunk. */
    public static final int COLUMNS = 16;

    public SeasonalChunkTable {
        if (base.length != COLUMNS || variants.length != sections * CELLS_PER_SECTION) {
            throw new IllegalArgumentException("table size: base " + base.length + ", variants " + variants.length);
        }
    }

    /** The base index the server sends for {@code cell} (any section). */
    public int baseAt(int cell) {
        return base[cell & 15];
    }

    /** The variant index to send for {@code cell} of {@code section}. */
    public int variantAt(int section, int cell) {
        return variants[section * CELLS_PER_SECTION + cell];
    }
}
