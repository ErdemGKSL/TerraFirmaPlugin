/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.biome;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden biome ids. Each line is one id: its TFC name, and TFC's {@code TFCLayers} answers for it ({@code isOcean},
 * {@code isLake}, {@code hasLake}, {@code lakeFor} and {@code isFlatIceSheet}). The ids are the index of the name in TFC's
 * registration order. The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit): the biome oracle compiled
 * TFC's {@code TFCLayers} with the biome constants registered in source order, and {@code TFCBiomes} as name-only stubs.
 */
class TfcBiomesGoldenTest {
    private static final String[] GOLDEN = {
        "ID 0 OCEAN ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 1 OCEAN_ATOLLS ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 2 OCEAN_REEF ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 3 DEEP_OCEAN ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 4 DEEP_OCEAN_ATOLLS ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 5 DEEP_OCEAN_TRENCH ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 6 OCEANIC_VOLCANIC_ARC ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 7 OCEAN_RIDGE ocean=true lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 8 RIFT_VALLEY ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 9 RIFT_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 10 COLLISIONAL_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=42 flatIce=false",
        "ID 11 PLAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 12 HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 13 LOWLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 14 SALT_MARSH ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 15 LOW_CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 16 RIVER_VALLEY ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 17 ROLLING_HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 18 HIGHLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 19 BADLANDS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 20 PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 21 PLATEAU_WIDE ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 22 OLD_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=44 flatIce=false",
        "ID 23 MOUNTAINS ocean=false lake=false hasLake=true lakeFor=42 flatIce=false",
        "ID 24 VOLCANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=43 flatIce=false",
        "ID 25 OCEANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=45 flatIce=false",
        "ID 26 VOLCANIC_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=46 flatIce=false",
        "ID 27 CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 28 GUANO_ISLAND ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 29 VOLCANIC_ISLAND ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 30 VOLCANIC_MOUNTAIN_ISLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 31 SHORE ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 32 TIDAL_FLATS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 33 SEA_STACKS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 34 TERRACE_UPPER ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 35 TERRACE_LOWER ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 36 SETBACK_CLIFFS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 37 COASTAL_DUNES ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 38 ROCKY_SHORES ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 39 EMBAYMENTS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 40 LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 41 RIVER ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 42 MOUNTAIN_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 43 VOLCANIC_MOUNTAIN_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 44 OLD_MOUNTAIN_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 45 OCEANIC_MOUNTAIN_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 46 VOLCANIC_OCEANIC_MOUNTAIN_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 47 PLATEAU_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 48 MUD_FLATS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 49 SALT_FLATS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 50 DUNE_SEA ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 51 GRASSY_DUNES ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 52 WHORLED_CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 53 STAIR_STEP_CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 54 MESAS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 55 BUTTES ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 56 HOODOOS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 57 ROCKY_PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 58 TOWER_KARST_PLAINS ocean=false lake=false hasLake=true lakeFor=62 flatIce=false",
        "ID 59 TOWER_KARST_CANYONS ocean=false lake=false hasLake=true lakeFor=62 flatIce=false",
        "ID 60 TOWER_KARST_HILLS ocean=false lake=false hasLake=true lakeFor=62 flatIce=false",
        "ID 61 TOWER_KARST_HIGHLANDS ocean=false lake=false hasLake=true lakeFor=62 flatIce=false",
        "ID 62 TOWER_KARST_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 63 TOWER_KARST_BAY ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 64 BURREN_PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 65 BURREN_BADLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 66 BURREN_BADLANDS_TALL ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 67 BURREN_ROCHE_MOUTONEE ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 68 BURREN_PLAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 69 SHILIN_PLAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 70 SHILIN_CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 71 SHILIN_HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 72 SHILIN_HIGHLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 73 SHILIN_PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 74 DOLINE_PLAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 75 DOLINE_HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 76 DOLINE_ROLLING_HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 77 DOLINE_HIGHLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 78 DOLINE_PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 79 DOLINE_CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 80 CENOTE_PLAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 81 CENOTE_HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 82 CENOTE_ROLLING_HILLS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 83 CENOTE_CANYONS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 84 CENOTE_HIGHLANDS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 85 CENOTE_PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 86 EXTREME_DOLINE_PLATEAU ocean=false lake=false hasLake=true lakeFor=47 flatIce=false",
        "ID 87 EXTREME_DOLINE_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=44 flatIce=false",
        "ID 88 ACTIVE_SHIELD_VOLCANO ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 89 DORMANT_SHIELD_VOLCANO ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 90 EXTINCT_SHIELD_VOLCANO ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 91 ANCIENT_SHIELD_VOLCANO ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 92 SUNKEN_SHIELD_VOLCANO ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 93 SHIELD_VOLCANO_SHORE ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 94 OLD_SHIELD_VOLCANO_SHORE ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 95 ICE_SHEET ocean=false lake=false hasLake=true lakeFor=102 flatIce=true",
        "ID 96 ICE_SHEET_MOUNTAINS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 97 ICE_SHEET_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 98 ICE_SHEET_VOLCANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 99 ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 100 ICE_SHEET_SHIELD_VOLCANO ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 101 ICE_SHEET_TUYAS ocean=false lake=false hasLake=true lakeFor=102 flatIce=true",
        "ID 102 SUBGLACIAL_LAKE ocean=false lake=true hasLake=false lakeFor=102 flatIce=true",
        "ID 103 ICE_SHEET_EDGE ocean=false lake=false hasLake=true lakeFor=112 flatIce=false",
        "ID 104 ICE_SHEET_TUYAS_EDGE ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 105 ICE_SHEET_OCEANIC ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 106 ICE_SHEET_OCEANIC_MOUNTAINS_EDGE ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 107 ICE_SHEET_MOUNTAINS_EDGE ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 108 GLACIATED_MOUNTAINS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 109 GLACIATED_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 110 GLACIATED_VOLCANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 111 GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 112 MELTWATER_LAKE ocean=false lake=true hasLake=false lakeFor=40 flatIce=false",
        "ID 113 GLACIATED_SHIELD_VOLCANO ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 114 ICE_SHEET_SHORE ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 115 GLACIALLY_CARVED_MOUNTAINS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 116 GLACIALLY_CARVED_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=false lakeFor=40 flatIce=false",
        "ID 117 GLACIALLY_CARVED_VOLCANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 118 GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 119 DRUMLINS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 120 TUYAS ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 121 KNOB_AND_KETTLE ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 122 PATTERNED_GROUND ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 123 INVERTED_PATTERNED_GROUND ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
        "ID 124 STONE_CIRCLES ocean=false lake=false hasLake=true lakeFor=40 flatIce=false",
    };

    @Test
    void idsAndPredicatesMatchTfcAtPinnedCommit() {
        assertThat(GOLDEN).hasSize(TfcBiomes.COUNT);
        for (int id = 0; id < GOLDEN.length; id++) {
            assertThat(describe(id)).as("id %d", id).isEqualTo(GOLDEN[id]);
        }
    }

    static String describe(int id) {
        return String.format("ID %d %s ocean=%b lake=%b hasLake=%b lakeFor=%d flatIce=%b", id, TfcBiomes.name(id),
            TfcBiomes.isOcean(id), TfcBiomes.isLake(id), TfcBiomes.hasLake(id), TfcBiomes.lakeFor(id), TfcBiomes.isFlatIceSheet(id));
    }
}
