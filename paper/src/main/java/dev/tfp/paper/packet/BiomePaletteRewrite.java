/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import dev.tfp.paper.climate.SeasonalBiomeRemap;
import dev.tfp.paper.climate.SeasonalChunkTable;

/**
 * M2-SEA-02, the library-free part of the chunk packet rewrite: swaps the biome ids of one section's 64 cells and picks
 * the palette the client expects for the result. Pure and allocation-free, so the Netty thread only does array work.
 */
public final class BiomePaletteRewrite {
    /** Largest bits per entry of a biome list palette (vanilla: 1 to 3 bits linear, more is the global palette). */
    public static final int MAX_LIST_BITS = 3;

    private BiomePaletteRewrite() {
    }

    /**
     * The network ids of one user, by our indices.
     *
     * @param base         per base index (see {@link SeasonalBiomeRemap#baseIdOf}): the client's id, or -1
     * @param variants     per variant index (see {@link SeasonalBiomeRemap#variantIdOf}): the client's id, or -1 when the
     *                     client's registry does not have it (datapack missing)
     * @param registrySize the size of the client's biome registry (bits of the global palette)
     */
    public record IdMap(int[] base, int[] variants, int registrySize) {
        /** True when at least one variant is known to the client. */
        public boolean usable() {
            for (int id : variants) {
                if (id >= 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Rewrites one section in place: each cell whose id is the base biome the table expects for its column becomes that
     * cell's variant. Cells with another id (a biome the server changed, another world's chunk) are left alone.
     *
     * @param cells the section's ids, {@code cells[y << 4 | z << 2 | x]}, rewritten in place
     * @return whether any cell changed
     */
    public static boolean rewriteSection(int[] cells, SeasonalChunkTable table, int section, IdMap ids) {
        boolean changed = false;
        for (int cell = 0; cell < SeasonalChunkTable.CELLS_PER_SECTION; cell++) {
            int expected = ids.base()[table.baseAt(cell)];
            if (expected < 0 || cells[cell] != expected) {
                continue;
            }
            int variant = ids.variants()[table.variantAt(section, cell)];
            if (variant >= 0 && variant != cells[cell]) {
                cells[cell] = variant;
                changed = true;
            }
        }
        return changed;
    }

    /** Number of distinct ids in {@code cells}. */
    public static int distinct(int[] cells) {
        int count = 0;
        outer:
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < i; j++) {
                if (cells[j] == cells[i]) {
                    continue outer;
                }
            }
            count++;
        }
        return count;
    }

    /**
     * The bits per entry the client reads for a section with {@code distinct} ids: 0 (one value), 1 to
     * {@value #MAX_LIST_BITS} (list palette), else the global palette's {@code ceil(log2(registrySize))}. The global
     * width must be exact: since 1.21.5 the client derives the data length from it.
     */
    public static int bits(int distinct, int registrySize) {
        if (distinct <= 1) {
            return 0;
        }
        int list = ceilLog2(distinct);
        return list <= MAX_LIST_BITS ? list : ceilLog2(registrySize);
    }

    static int ceilLog2(int n) {
        return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1);
    }
}
