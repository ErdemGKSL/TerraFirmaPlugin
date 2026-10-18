/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.Month;
import dev.tfp.paper.climate.SeasonalBiomeRemap;
import dev.tfp.paper.climate.SeasonalChunkTable;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BiomePaletteRewriteTest {
    private static final int PLAINS_ID = 40;
    private static final int OCEAN_ID = 30;

    /** One section; columns with x < 2 expect base 0 (plains), the others base 5; variants are 100 + cell. */
    private static SeasonalChunkTable table() {
        byte[] base = new byte[16];
        for (int column = 0; column < 16; column++) {
            base[column] = (byte) ((column & 3) < 2 ? 0 : 5);
        }
        short[] variants = new short[64];
        for (int cell = 0; cell < 64; cell++) {
            variants[cell] = (short) (100 + cell);
        }
        return new SeasonalChunkTable(0, 0, 0, 1, Month.JULY, base, variants);
    }

    private static BiomePaletteRewrite.IdMap ids() {
        int[] base = new int[SeasonalBiomeRemap.BASE_COUNT];
        Arrays.fill(base, -1);
        base[0] = PLAINS_ID;
        base[5] = OCEAN_ID;
        int[] variants = new int[SeasonalBiomeRemap.VARIANT_COUNT];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = 1000 + i;
        }
        variants[100 + 63] = -1; // not in the client registry
        return new BiomePaletteRewrite.IdMap(base, variants, 2067);
    }

    @Test
    void swapsOnlyCellsThatCarryTheExpectedBaseBiome() {
        int[] cells = new int[64];
        Arrays.fill(cells, PLAINS_ID);
        boolean changed = BiomePaletteRewrite.rewriteSection(cells, table(), 0, ids());
        assertThat(changed).isTrue();
        for (int cell = 0; cell < 64; cell++) {
            boolean plainsColumn = (cell & 3) < 2;
            assertThat(cells[cell]).as("cell %d", cell).isEqualTo(plainsColumn ? 1000 + 100 + cell : PLAINS_ID);
        }
    }

    @Test
    void missingVariantKeepsTheBaseBiome() {
        int[] cells = new int[64];
        Arrays.fill(cells, OCEAN_ID);
        BiomePaletteRewrite.rewriteSection(cells, table(), 0, ids());
        assertThat(cells[63]).isEqualTo(OCEAN_ID); // column x = 3 expects ocean, but its variant is unknown
        assertThat(cells[62]).isEqualTo(1000 + 100 + 62);
    }

    @Test
    void unrelatedBiomesAreUnchanged() {
        int[] cells = new int[64];
        Arrays.fill(cells, 7);
        assertThat(BiomePaletteRewrite.rewriteSection(cells, table(), 0, ids())).isFalse();
        assertThat(cells).containsOnly(7);
    }

    @Test
    void paletteWidthFollowsTheClient() {
        assertThat(BiomePaletteRewrite.bits(1, 2067)).isZero();
        assertThat(BiomePaletteRewrite.bits(2, 2067)).isEqualTo(1);
        assertThat(BiomePaletteRewrite.bits(3, 2067)).isEqualTo(2);
        assertThat(BiomePaletteRewrite.bits(8, 2067)).isEqualTo(3);
        assertThat(BiomePaletteRewrite.bits(9, 2067)).isEqualTo(12); // global palette: ceil(log2(2067))
        assertThat(BiomePaletteRewrite.bits(9, 67)).isEqualTo(7);
    }

    @Test
    void countsDistinctIds() {
        int[] cells = new int[64];
        for (int i = 0; i < 64; i++) {
            cells[i] = i % 5;
        }
        assertThat(BiomePaletteRewrite.distinct(cells)).isEqualTo(5);
    }
}
