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

/** The rock choice: TFC's rock layer walk of the preset (PAR-WORLD-23; goldens in rock/RockLayerGoldenTest), bounds. */
class PlaceholderTerrainTest {
    @Test
    void theSurfaceLayerOfEachSupertypeIsTfcs() {
        assertThat(PlaceholderRocks.count()).isEqualTo(21);
        assertThat(PlaceholderRocks.names()).hasSize(21).doesNotHaveDuplicates();
        // Ocean (type 0): ocean_floor = igneous_extrusive
        assertThat(names(0, 0)).containsOnly("andesite", "basalt", "dacite", "rhyolite");
        // Volcanic (type 1): igneous extrusive (x2) and intrusive
        assertThat(names(1, 0)).containsOnly("andesite", "basalt", "dacite", "rhyolite", "diorite", "gabbro", "granite");
        // Land (type 2): sedimentary three times in four, igneous extrusive
        assertThat(names(2, 0)).containsOnly("chalk", "chert", "claystone", "conglomerate", "dolomite", "limestone", "shale",
            "andesite", "basalt", "dacite", "rhyolite");
        // Uplift (type 3): the uplift layer and sedimentary
        assertThat(names(3, 0)).containsOnly("diorite", "gabbro", "gneiss", "granite", "marble", "phyllite", "quartzite", "schist",
            "slate", "chalk", "chert", "claystone", "conglomerate", "dolomite", "limestone", "shale");
    }

    @Test
    void deepLayersReachTheBottomIntrusives() {
        for (int type = 0; type < 4; type++) {
            assertThat(names(type, 12)).containsOnly("gneiss", "schist", "diorite", "granite", "gabbro");
        }
        // land: sedimentary over marble / quartzite / low grade metamorphic
        assertThat(names(2, 1)).contains("marble", "quartzite", "phyllite", "slate");
    }

    @Test
    void negativeRockIdsStillIndexInRange() {
        for (int rock = -64; rock < 64; rock++) {
            for (int layer = 0; layer < 12; layer++) {
                assertThat(PlaceholderRocks.index(rock, layer)).isBetween(0, PlaceholderRocks.count() - 1);
            }
        }
    }

    private static Set<String> names(int type, int layer) {
        Set<String> out = new HashSet<>();
        for (int rock = type; rock < type + 4 * 2000; rock += 4) {
            out.add(PlaceholderRocks.names().get(PlaceholderRocks.index(rock, layer)));
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
