/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (each biome's {@code .type(RiverBlendType)} and
 * {@code .noRivers()}), with the defaults of src/main/java/net/dries007/tfc/world/biome/BiomeBuilder.java (NONE, rivers on).
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.biome.TfcBiomes;

/**
 * The river properties of each TFC biome id ({@link TfcBiomes}): its {@link RiverBlendType} ({@code BiomeExtension.riverBlendType})
 * and whether it has rivers ({@code BiomeExtension.hasRivers}: river water flows and the column may become the river biome).
 */
public final class RiverBiomes {
    private static final RiverBlendType[] BLEND = {
        RiverBlendType.NONE, // 0 OCEAN
        RiverBlendType.NONE, // 1 OCEAN_ATOLLS
        RiverBlendType.NONE, // 2 OCEAN_REEF
        RiverBlendType.NONE, // 3 DEEP_OCEAN
        RiverBlendType.NONE, // 4 DEEP_OCEAN_ATOLLS
        RiverBlendType.NONE, // 5 DEEP_OCEAN_TRENCH
        RiverBlendType.NONE, // 6 OCEANIC_VOLCANIC_ARC
        RiverBlendType.NONE, // 7 OCEAN_RIDGE
        RiverBlendType.CAVE, // 8 RIFT_VALLEY
        RiverBlendType.CAVE, // 9 RIFT_LAKE
        RiverBlendType.CAVE, // 10 COLLISIONAL_MOUNTAINS
        RiverBlendType.FLOODPLAIN, // 11 PLAINS
        RiverBlendType.FLOODPLAIN, // 12 HILLS
        RiverBlendType.BANKED, // 13 LOWLANDS
        RiverBlendType.BANKED, // 14 SALT_MARSH
        RiverBlendType.WIDE, // 15 LOW_CANYONS
        RiverBlendType.FLOODPLAIN, // 16 RIVER_VALLEY
        RiverBlendType.CANYON, // 17 ROLLING_HILLS
        RiverBlendType.CANYON, // 18 HIGHLANDS
        RiverBlendType.CANYON, // 19 BADLANDS
        RiverBlendType.TALL_CANYON, // 20 PLATEAU
        RiverBlendType.TALUS, // 21 PLATEAU_WIDE
        RiverBlendType.CAVE, // 22 OLD_MOUNTAINS
        RiverBlendType.CAVE, // 23 MOUNTAINS
        RiverBlendType.CAVE, // 24 VOLCANIC_MOUNTAINS
        RiverBlendType.CAVE, // 25 OCEANIC_MOUNTAINS
        RiverBlendType.CAVE, // 26 VOLCANIC_OCEANIC_MOUNTAINS
        RiverBlendType.CANYON, // 27 CANYONS
        RiverBlendType.CAVE, // 28 GUANO_ISLAND
        RiverBlendType.CANYON, // 29 VOLCANIC_ISLAND
        RiverBlendType.CAVE, // 30 VOLCANIC_MOUNTAIN_ISLANDS
        RiverBlendType.WIDE, // 31 SHORE
        RiverBlendType.WIDE, // 32 TIDAL_FLATS
        RiverBlendType.TALL_CANYON, // 33 SEA_STACKS
        RiverBlendType.TALL_CANYON, // 34 TERRACE_UPPER
        RiverBlendType.TALL_CANYON, // 35 TERRACE_LOWER
        RiverBlendType.CANYON, // 36 SETBACK_CLIFFS
        RiverBlendType.WIDE_DEEP, // 37 COASTAL_DUNES
        RiverBlendType.CANYON, // 38 ROCKY_SHORES
        RiverBlendType.CANYON, // 39 EMBAYMENTS
        RiverBlendType.WIDE, // 40 LAKE
        RiverBlendType.NONE, // 41 RIVER
        RiverBlendType.NONE, // 42 MOUNTAIN_LAKE
        RiverBlendType.NONE, // 43 VOLCANIC_MOUNTAIN_LAKE
        RiverBlendType.NONE, // 44 OLD_MOUNTAIN_LAKE
        RiverBlendType.NONE, // 45 OCEANIC_MOUNTAIN_LAKE
        RiverBlendType.NONE, // 46 VOLCANIC_OCEANIC_MOUNTAIN_LAKE
        RiverBlendType.NONE, // 47 PLATEAU_LAKE
        RiverBlendType.TALL_BANKED, // 48 MUD_FLATS
        RiverBlendType.TALL_BANKED, // 49 SALT_FLATS
        RiverBlendType.WIDE, // 50 DUNE_SEA
        RiverBlendType.WIDE, // 51 GRASSY_DUNES
        RiverBlendType.TALL_CANYON, // 52 WHORLED_CANYONS
        RiverBlendType.TERRACES, // 53 STAIR_STEP_CANYONS
        RiverBlendType.TERRACES, // 54 MESAS
        RiverBlendType.TERRACES, // 55 BUTTES
        RiverBlendType.TERRACES, // 56 HOODOOS
        RiverBlendType.TALUS, // 57 ROCKY_PLATEAU
        RiverBlendType.TALL_CANYON, // 58 TOWER_KARST_PLAINS
        RiverBlendType.CAVE, // 59 TOWER_KARST_CANYONS
        RiverBlendType.TALL_CANYON, // 60 TOWER_KARST_HILLS
        RiverBlendType.CAVE, // 61 TOWER_KARST_HIGHLANDS
        RiverBlendType.TALL_CANYON, // 62 TOWER_KARST_LAKE
        RiverBlendType.TALL_CANYON, // 63 TOWER_KARST_BAY
        RiverBlendType.TALL_CANYON, // 64 BURREN_PLATEAU
        RiverBlendType.CANYON, // 65 BURREN_BADLANDS
        RiverBlendType.TALL_CANYON, // 66 BURREN_BADLANDS_TALL
        RiverBlendType.WIDE, // 67 BURREN_ROCHE_MOUTONEE
        RiverBlendType.WIDE, // 68 BURREN_PLAINS
        RiverBlendType.WIDE, // 69 SHILIN_PLAINS
        RiverBlendType.WIDE, // 70 SHILIN_CANYONS
        RiverBlendType.WIDE, // 71 SHILIN_HILLS
        RiverBlendType.WIDE, // 72 SHILIN_HIGHLANDS
        RiverBlendType.WIDE, // 73 SHILIN_PLATEAU
        RiverBlendType.FLOODPLAIN, // 74 DOLINE_PLAINS
        RiverBlendType.WIDE, // 75 DOLINE_HILLS
        RiverBlendType.CANYON, // 76 DOLINE_ROLLING_HILLS
        RiverBlendType.CANYON, // 77 DOLINE_HIGHLANDS
        RiverBlendType.TALL_CANYON, // 78 DOLINE_PLATEAU
        RiverBlendType.CANYON, // 79 DOLINE_CANYONS
        RiverBlendType.FLOODPLAIN, // 80 CENOTE_PLAINS
        RiverBlendType.WIDE, // 81 CENOTE_HILLS
        RiverBlendType.CANYON, // 82 CENOTE_ROLLING_HILLS
        RiverBlendType.CANYON, // 83 CENOTE_CANYONS
        RiverBlendType.TALL_CANYON, // 84 CENOTE_HIGHLANDS
        RiverBlendType.TALL_CANYON, // 85 CENOTE_PLATEAU
        RiverBlendType.TALL_CANYON, // 86 EXTREME_DOLINE_PLATEAU
        RiverBlendType.CAVE, // 87 EXTREME_DOLINE_MOUNTAINS
        RiverBlendType.CAVE, // 88 ACTIVE_SHIELD_VOLCANO
        RiverBlendType.CAVE, // 89 DORMANT_SHIELD_VOLCANO
        RiverBlendType.CAVE, // 90 EXTINCT_SHIELD_VOLCANO
        RiverBlendType.CAVE, // 91 ANCIENT_SHIELD_VOLCANO
        RiverBlendType.CAVE, // 92 SUNKEN_SHIELD_VOLCANO
        RiverBlendType.TALL_CANYON, // 93 SHIELD_VOLCANO_SHORE
        RiverBlendType.TALL_CANYON, // 94 OLD_SHIELD_VOLCANO_SHORE
        RiverBlendType.CAVE, // 95 ICE_SHEET
        RiverBlendType.CAVE, // 96 ICE_SHEET_MOUNTAINS
        RiverBlendType.CAVE, // 97 ICE_SHEET_OCEANIC_MOUNTAINS
        RiverBlendType.CAVE, // 98 ICE_SHEET_VOLCANIC_MOUNTAINS
        RiverBlendType.CAVE, // 99 ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS
        RiverBlendType.CAVE, // 100 ICE_SHEET_SHIELD_VOLCANO
        RiverBlendType.CAVE, // 101 ICE_SHEET_TUYAS
        RiverBlendType.NONE, // 102 SUBGLACIAL_LAKE
        RiverBlendType.TALL_CANYON, // 103 ICE_SHEET_EDGE
        RiverBlendType.TALL_CANYON, // 104 ICE_SHEET_TUYAS_EDGE
        RiverBlendType.CAVE, // 105 ICE_SHEET_OCEANIC
        RiverBlendType.CAVE, // 106 ICE_SHEET_OCEANIC_MOUNTAINS_EDGE
        RiverBlendType.CAVE, // 107 ICE_SHEET_MOUNTAINS_EDGE
        RiverBlendType.CAVE, // 108 GLACIATED_MOUNTAINS
        RiverBlendType.CAVE, // 109 GLACIATED_OCEANIC_MOUNTAINS
        RiverBlendType.CAVE, // 110 GLACIATED_VOLCANIC_MOUNTAINS
        RiverBlendType.CAVE, // 111 GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS
        RiverBlendType.WIDE, // 112 MELTWATER_LAKE
        RiverBlendType.CAVE, // 113 GLACIATED_SHIELD_VOLCANO
        RiverBlendType.TALL_CANYON, // 114 ICE_SHEET_SHORE
        RiverBlendType.CAVE, // 115 GLACIALLY_CARVED_MOUNTAINS
        RiverBlendType.CAVE, // 116 GLACIALLY_CARVED_OCEANIC_MOUNTAINS
        RiverBlendType.CAVE, // 117 GLACIALLY_CARVED_VOLCANIC_MOUNTAINS
        RiverBlendType.CAVE, // 118 GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS
        RiverBlendType.WIDE, // 119 DRUMLINS
        RiverBlendType.CANYON, // 120 TUYAS
        RiverBlendType.WIDE, // 121 KNOB_AND_KETTLE
        RiverBlendType.WIDE, // 122 PATTERNED_GROUND
        RiverBlendType.WIDE, // 123 INVERTED_PATTERNED_GROUND
        RiverBlendType.WIDE, // 124 STONE_CIRCLES
    };
    private static final boolean[] RIVERS = {
        false, // 0 OCEAN
        false, // 1 OCEAN_ATOLLS
        false, // 2 OCEAN_REEF
        false, // 3 DEEP_OCEAN
        false, // 4 DEEP_OCEAN_ATOLLS
        false, // 5 DEEP_OCEAN_TRENCH
        false, // 6 OCEANIC_VOLCANIC_ARC
        false, // 7 OCEAN_RIDGE
        true, // 8 RIFT_VALLEY
        false, // 9 RIFT_LAKE
        true, // 10 COLLISIONAL_MOUNTAINS
        true, // 11 PLAINS
        true, // 12 HILLS
        true, // 13 LOWLANDS
        true, // 14 SALT_MARSH
        true, // 15 LOW_CANYONS
        true, // 16 RIVER_VALLEY
        true, // 17 ROLLING_HILLS
        true, // 18 HIGHLANDS
        true, // 19 BADLANDS
        true, // 20 PLATEAU
        true, // 21 PLATEAU_WIDE
        true, // 22 OLD_MOUNTAINS
        true, // 23 MOUNTAINS
        true, // 24 VOLCANIC_MOUNTAINS
        true, // 25 OCEANIC_MOUNTAINS
        true, // 26 VOLCANIC_OCEANIC_MOUNTAINS
        true, // 27 CANYONS
        true, // 28 GUANO_ISLAND
        true, // 29 VOLCANIC_ISLAND
        true, // 30 VOLCANIC_MOUNTAIN_ISLANDS
        false, // 31 SHORE
        false, // 32 TIDAL_FLATS
        false, // 33 SEA_STACKS
        false, // 34 TERRACE_UPPER
        false, // 35 TERRACE_LOWER
        false, // 36 SETBACK_CLIFFS
        false, // 37 COASTAL_DUNES
        false, // 38 ROCKY_SHORES
        false, // 39 EMBAYMENTS
        false, // 40 LAKE
        true, // 41 RIVER
        false, // 42 MOUNTAIN_LAKE
        false, // 43 VOLCANIC_MOUNTAIN_LAKE
        false, // 44 OLD_MOUNTAIN_LAKE
        false, // 45 OCEANIC_MOUNTAIN_LAKE
        false, // 46 VOLCANIC_OCEANIC_MOUNTAIN_LAKE
        false, // 47 PLATEAU_LAKE
        true, // 48 MUD_FLATS
        true, // 49 SALT_FLATS
        true, // 50 DUNE_SEA
        true, // 51 GRASSY_DUNES
        true, // 52 WHORLED_CANYONS
        true, // 53 STAIR_STEP_CANYONS
        true, // 54 MESAS
        true, // 55 BUTTES
        true, // 56 HOODOOS
        true, // 57 ROCKY_PLATEAU
        true, // 58 TOWER_KARST_PLAINS
        true, // 59 TOWER_KARST_CANYONS
        true, // 60 TOWER_KARST_HILLS
        true, // 61 TOWER_KARST_HIGHLANDS
        true, // 62 TOWER_KARST_LAKE
        true, // 63 TOWER_KARST_BAY
        true, // 64 BURREN_PLATEAU
        true, // 65 BURREN_BADLANDS
        true, // 66 BURREN_BADLANDS_TALL
        true, // 67 BURREN_ROCHE_MOUTONEE
        true, // 68 BURREN_PLAINS
        true, // 69 SHILIN_PLAINS
        true, // 70 SHILIN_CANYONS
        true, // 71 SHILIN_HILLS
        true, // 72 SHILIN_HIGHLANDS
        true, // 73 SHILIN_PLATEAU
        true, // 74 DOLINE_PLAINS
        true, // 75 DOLINE_HILLS
        true, // 76 DOLINE_ROLLING_HILLS
        true, // 77 DOLINE_HIGHLANDS
        true, // 78 DOLINE_PLATEAU
        true, // 79 DOLINE_CANYONS
        true, // 80 CENOTE_PLAINS
        true, // 81 CENOTE_HILLS
        true, // 82 CENOTE_ROLLING_HILLS
        true, // 83 CENOTE_CANYONS
        true, // 84 CENOTE_HIGHLANDS
        true, // 85 CENOTE_PLATEAU
        true, // 86 EXTREME_DOLINE_PLATEAU
        true, // 87 EXTREME_DOLINE_MOUNTAINS
        true, // 88 ACTIVE_SHIELD_VOLCANO
        true, // 89 DORMANT_SHIELD_VOLCANO
        true, // 90 EXTINCT_SHIELD_VOLCANO
        true, // 91 ANCIENT_SHIELD_VOLCANO
        true, // 92 SUNKEN_SHIELD_VOLCANO
        true, // 93 SHIELD_VOLCANO_SHORE
        true, // 94 OLD_SHIELD_VOLCANO_SHORE
        true, // 95 ICE_SHEET
        true, // 96 ICE_SHEET_MOUNTAINS
        true, // 97 ICE_SHEET_OCEANIC_MOUNTAINS
        true, // 98 ICE_SHEET_VOLCANIC_MOUNTAINS
        true, // 99 ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS
        true, // 100 ICE_SHEET_SHIELD_VOLCANO
        true, // 101 ICE_SHEET_TUYAS
        false, // 102 SUBGLACIAL_LAKE
        true, // 103 ICE_SHEET_EDGE
        true, // 104 ICE_SHEET_TUYAS_EDGE
        true, // 105 ICE_SHEET_OCEANIC
        true, // 106 ICE_SHEET_OCEANIC_MOUNTAINS_EDGE
        true, // 107 ICE_SHEET_MOUNTAINS_EDGE
        true, // 108 GLACIATED_MOUNTAINS
        true, // 109 GLACIATED_OCEANIC_MOUNTAINS
        true, // 110 GLACIATED_VOLCANIC_MOUNTAINS
        true, // 111 GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS
        false, // 112 MELTWATER_LAKE
        true, // 113 GLACIATED_SHIELD_VOLCANO
        true, // 114 ICE_SHEET_SHORE
        true, // 115 GLACIALLY_CARVED_MOUNTAINS
        true, // 116 GLACIALLY_CARVED_OCEANIC_MOUNTAINS
        true, // 117 GLACIALLY_CARVED_VOLCANIC_MOUNTAINS
        true, // 118 GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS
        true, // 119 DRUMLINS
        true, // 120 TUYAS
        true, // 121 KNOB_AND_KETTLE
        true, // 122 PATTERNED_GROUND
        true, // 123 INVERTED_PATTERNED_GROUND
        true, // 124 STONE_CIRCLES
    };

    private RiverBiomes() {}

    /** @return the river blend type of a TFC biome id, or {@link RiverBlendType#NONE} for an unknown id. */
    public static RiverBlendType blendType(int biomeId) {
        return biomeId >= 0 && biomeId < BLEND.length ? BLEND[biomeId] : RiverBlendType.NONE;
    }

    /** @return {@code true} if the biome has rivers ({@code BiomeExtension.hasRivers}); false for an unknown id. */
    public static boolean hasRivers(int biomeId) {
        return biomeId >= 0 && biomeId < RIVERS.length && RIVERS[biomeId];
    }

    /** The number of biome ids in the tables ({@link TfcBiomes#COUNT}). */
    static int size() {
        return BLEND.length;
    }
}
