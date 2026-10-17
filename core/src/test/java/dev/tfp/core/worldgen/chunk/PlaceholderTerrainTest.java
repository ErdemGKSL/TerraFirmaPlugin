/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The placeholder heights and rock choice of M2-GEN-07: shape, bounds, determinism. Not TFC golden values. */
class PlaceholderTerrainTest {
    private static final int MIN_HEIGHT = ChunkData.SEA_LEVEL_Y - PlaceholderHeights.OCEAN_BASE - 5 * PlaceholderHeights.OCEAN_PER_DEPTH;
    private static final int MAX_HEIGHT = ChunkData.SEA_LEVEL_Y + PlaceholderHeights.LAND_BASE
        + (int) (PlaceholderHeights.LAND_RISE_CAP * PlaceholderHeights.LAND_RISE) + 1;

    @Test
    void heightsStayInsideTheShapeRangeAndCoverAChunk() {
        RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(42L));
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                int[] heights = PlaceholderHeights.heights(regions, cx, cz);
                assertThat(heights).hasSize(ChunkData.COLUMNS);
                for (int h : heights) {
                    assertThat(h).isBetween(MIN_HEIGHT, MAX_HEIGHT);
                }
            }
        }
    }

    @Test
    void heightsAreDeterministicForASeed() {
        int[] first = PlaceholderHeights.heights(new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(7L)), -2, 5);
        int[] second = PlaceholderHeights.heights(new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(7L)), -2, 5);
        assertThat(first).containsExactly(second);
    }

    @Test
    void theShapeHasBothLandAndOcean() {
        RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(42L));
        Set<Boolean> sides = new HashSet<>();
        for (int cx = -8; cx <= 8; cx++) {
            for (int cz = -8; cz <= 8; cz++) {
                for (int h : PlaceholderHeights.heights(regions, cx, cz)) {
                    sides.add(h > ChunkData.SEA_LEVEL_Y);
                }
            }
        }
        assertThat(sides).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void everyRockIndexNamesARockOfItsSupertypeCategory() {
        assertThat(PlaceholderRocks.count()).isEqualTo(21);
        assertThat(PlaceholderRocks.names()).hasSize(21).doesNotHaveDuplicates();
        // Ocean (type 0): sedimentary
        assertThat(names(0)).containsOnly("shale", "claystone", "limestone", "conglomerate", "dolomite", "chert", "chalk", "tuff");
        // Volcanic (type 1): igneous extrusive
        assertThat(names(1)).containsOnly("rhyolite", "basalt", "andesite", "dacite");
        // Land (type 2): igneous intrusive
        assertThat(names(2)).containsOnly("granite", "diorite", "gabbro");
        // Uplift (type 3): metamorphic
        assertThat(names(3)).containsOnly("quartzite", "slate", "phyllite", "schist", "gneiss", "marble");
    }

    @Test
    void layersOfOnePointDifferInsideTheCategory() {
        int rock = (12345 << 2) | 2;
        assertThat(PlaceholderRocks.index(rock, 0)).isNotEqualTo(PlaceholderRocks.index(rock, 1));
        assertThat(PlaceholderRocks.index(rock, 3)).isBetween(12, 14);
    }

    @Test
    void negativeRockIdsStillIndexInRange() {
        for (int rock = -64; rock < 64; rock++) {
            for (int layer = 0; layer < 12; layer++) {
                assertThat(PlaceholderRocks.index(rock, layer)).isBetween(0, PlaceholderRocks.count() - 1);
            }
        }
    }

    private static Set<String> names(int type) {
        Set<String> out = new HashSet<>();
        for (int layer = 0; layer < 40; layer++) {
            for (int rock = type; rock < type + 4 * 64; rock += 4) {
                out.add(PlaceholderRocks.names().get(PlaceholderRocks.index(rock, layer)));
            }
        }
        return out;
    }
}
