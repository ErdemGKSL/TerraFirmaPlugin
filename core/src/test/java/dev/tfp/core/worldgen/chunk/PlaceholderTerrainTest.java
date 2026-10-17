/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The placeholder rock choice of M2-GEN-07: categories, layers, bounds. Not TFC golden values. */
class PlaceholderTerrainTest {
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

    @Test
    void onlyBasaltAndGabbroAreMafic() {
        for (int i = 0; i < PlaceholderRocks.count(); i++) {
            String name = PlaceholderRocks.names().get(i);
            assertThat(PlaceholderRocks.isMafic(i)).isEqualTo(name.equals("basalt") || name.equals("gabbro"));
        }
    }
}
