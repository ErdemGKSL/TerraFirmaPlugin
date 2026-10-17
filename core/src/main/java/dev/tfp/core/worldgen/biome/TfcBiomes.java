/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/TFCLayers.java @ c3b4d9536 (the biome ids and the lake and ice helpers)
 */
package dev.tfp.core.worldgen.biome;

/**
 * The TFC biome ids. TFC gives each biome an int id in the order its biome is registered with {@code TFCLayers.idFor}
 * (the static fields of that class, in source order), so the id of a biome is its index in {@link #NAMES}. The ids are
 * plain ints here: the Bukkit biome registry binding (M2-GEN-07) maps each id to a biome key. The ids are the same as TFC's
 * at c3b4d9536, which the golden tests check.
 */
public final class TfcBiomes {
    private TfcBiomes() {}

    /** The number of biome ids. TFC's layer id table has room for 128. */
    public static final int COUNT = 125;

    /** The biome names, indexed by id: TFC's constant names. */
    private static final String[] NAMES = {
        "OCEAN",
        "OCEAN_ATOLLS",
        "OCEAN_REEF",
        "DEEP_OCEAN",
        "DEEP_OCEAN_ATOLLS",
        "DEEP_OCEAN_TRENCH",
        "OCEANIC_VOLCANIC_ARC",
        "OCEAN_RIDGE",
        "RIFT_VALLEY",
        "RIFT_LAKE",
        "COLLISIONAL_MOUNTAINS",
        "PLAINS",
        "HILLS",
        "LOWLANDS",
        "SALT_MARSH",
        "LOW_CANYONS",
        "RIVER_VALLEY",
        "ROLLING_HILLS",
        "HIGHLANDS",
        "BADLANDS",
        "PLATEAU",
        "PLATEAU_WIDE",
        "OLD_MOUNTAINS",
        "MOUNTAINS",
        "VOLCANIC_MOUNTAINS",
        "OCEANIC_MOUNTAINS",
        "VOLCANIC_OCEANIC_MOUNTAINS",
        "CANYONS",
        "GUANO_ISLAND",
        "VOLCANIC_ISLAND",
        "VOLCANIC_MOUNTAIN_ISLANDS",
        "SHORE",
        "TIDAL_FLATS",
        "SEA_STACKS",
        "TERRACE_UPPER",
        "TERRACE_LOWER",
        "SETBACK_CLIFFS",
        "COASTAL_DUNES",
        "ROCKY_SHORES",
        "EMBAYMENTS",
        "LAKE",
        "RIVER",
        "MOUNTAIN_LAKE",
        "VOLCANIC_MOUNTAIN_LAKE",
        "OLD_MOUNTAIN_LAKE",
        "OCEANIC_MOUNTAIN_LAKE",
        "VOLCANIC_OCEANIC_MOUNTAIN_LAKE",
        "PLATEAU_LAKE",
        "MUD_FLATS",
        "SALT_FLATS",
        "DUNE_SEA",
        "GRASSY_DUNES",
        "WHORLED_CANYONS",
        "STAIR_STEP_CANYONS",
        "MESAS",
        "BUTTES",
        "HOODOOS",
        "ROCKY_PLATEAU",
        "TOWER_KARST_PLAINS",
        "TOWER_KARST_CANYONS",
        "TOWER_KARST_HILLS",
        "TOWER_KARST_HIGHLANDS",
        "TOWER_KARST_LAKE",
        "TOWER_KARST_BAY",
        "BURREN_PLATEAU",
        "BURREN_BADLANDS",
        "BURREN_BADLANDS_TALL",
        "BURREN_ROCHE_MOUTONEE",
        "BURREN_PLAINS",
        "SHILIN_PLAINS",
        "SHILIN_CANYONS",
        "SHILIN_HILLS",
        "SHILIN_HIGHLANDS",
        "SHILIN_PLATEAU",
        "DOLINE_PLAINS",
        "DOLINE_HILLS",
        "DOLINE_ROLLING_HILLS",
        "DOLINE_HIGHLANDS",
        "DOLINE_PLATEAU",
        "DOLINE_CANYONS",
        "CENOTE_PLAINS",
        "CENOTE_HILLS",
        "CENOTE_ROLLING_HILLS",
        "CENOTE_CANYONS",
        "CENOTE_HIGHLANDS",
        "CENOTE_PLATEAU",
        "EXTREME_DOLINE_PLATEAU",
        "EXTREME_DOLINE_MOUNTAINS",
        "ACTIVE_SHIELD_VOLCANO",
        "DORMANT_SHIELD_VOLCANO",
        "EXTINCT_SHIELD_VOLCANO",
        "ANCIENT_SHIELD_VOLCANO",
        "SUNKEN_SHIELD_VOLCANO",
        "SHIELD_VOLCANO_SHORE",
        "OLD_SHIELD_VOLCANO_SHORE",
        "ICE_SHEET",
        "ICE_SHEET_MOUNTAINS",
        "ICE_SHEET_OCEANIC_MOUNTAINS",
        "ICE_SHEET_VOLCANIC_MOUNTAINS",
        "ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS",
        "ICE_SHEET_SHIELD_VOLCANO",
        "ICE_SHEET_TUYAS",
        "SUBGLACIAL_LAKE",
        "ICE_SHEET_EDGE",
        "ICE_SHEET_TUYAS_EDGE",
        "ICE_SHEET_OCEANIC",
        "ICE_SHEET_OCEANIC_MOUNTAINS_EDGE",
        "ICE_SHEET_MOUNTAINS_EDGE",
        "GLACIATED_MOUNTAINS",
        "GLACIATED_OCEANIC_MOUNTAINS",
        "GLACIATED_VOLCANIC_MOUNTAINS",
        "GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS",
        "MELTWATER_LAKE",
        "GLACIATED_SHIELD_VOLCANO",
        "ICE_SHEET_SHORE",
        "GLACIALLY_CARVED_MOUNTAINS",
        "GLACIALLY_CARVED_OCEANIC_MOUNTAINS",
        "GLACIALLY_CARVED_VOLCANIC_MOUNTAINS",
        "GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS",
        "DRUMLINS",
        "TUYAS",
        "KNOB_AND_KETTLE",
        "PATTERNED_GROUND",
        "INVERTED_PATTERNED_GROUND",
        "STONE_CIRCLES",
    };

    public static final int OCEAN = 0;
    public static final int OCEAN_ATOLLS = 1;
    public static final int OCEAN_REEF = 2;
    public static final int DEEP_OCEAN = 3;
    public static final int DEEP_OCEAN_ATOLLS = 4;
    public static final int DEEP_OCEAN_TRENCH = 5;
    public static final int OCEANIC_VOLCANIC_ARC = 6;
    public static final int OCEAN_RIDGE = 7;
    public static final int RIFT_VALLEY = 8;
    public static final int RIFT_LAKE = 9;
    public static final int COLLISIONAL_MOUNTAINS = 10;
    public static final int PLAINS = 11;
    public static final int HILLS = 12;
    public static final int LOWLANDS = 13;
    public static final int SALT_MARSH = 14;
    public static final int LOW_CANYONS = 15;
    public static final int RIVER_VALLEY = 16;
    public static final int ROLLING_HILLS = 17;
    public static final int HIGHLANDS = 18;
    public static final int BADLANDS = 19;
    public static final int PLATEAU = 20;
    public static final int PLATEAU_WIDE = 21;
    public static final int OLD_MOUNTAINS = 22;
    public static final int MOUNTAINS = 23;
    public static final int VOLCANIC_MOUNTAINS = 24;
    public static final int OCEANIC_MOUNTAINS = 25;
    public static final int VOLCANIC_OCEANIC_MOUNTAINS = 26;
    public static final int CANYONS = 27;
    public static final int GUANO_ISLAND = 28;
    public static final int VOLCANIC_ISLAND = 29;
    public static final int VOLCANIC_MOUNTAIN_ISLANDS = 30;
    public static final int SHORE = 31;
    public static final int TIDAL_FLATS = 32;
    public static final int SEA_STACKS = 33;
    public static final int TERRACE_UPPER = 34;
    public static final int TERRACE_LOWER = 35;
    public static final int SETBACK_CLIFFS = 36;
    public static final int COASTAL_DUNES = 37;
    public static final int ROCKY_SHORES = 38;
    public static final int EMBAYMENTS = 39;
    public static final int LAKE = 40;
    public static final int RIVER = 41;
    public static final int MOUNTAIN_LAKE = 42;
    public static final int VOLCANIC_MOUNTAIN_LAKE = 43;
    public static final int OLD_MOUNTAIN_LAKE = 44;
    public static final int OCEANIC_MOUNTAIN_LAKE = 45;
    public static final int VOLCANIC_OCEANIC_MOUNTAIN_LAKE = 46;
    public static final int PLATEAU_LAKE = 47;
    public static final int MUD_FLATS = 48;
    public static final int SALT_FLATS = 49;
    public static final int DUNE_SEA = 50;
    public static final int GRASSY_DUNES = 51;
    public static final int WHORLED_CANYONS = 52;
    public static final int STAIR_STEP_CANYONS = 53;
    public static final int MESAS = 54;
    public static final int BUTTES = 55;
    public static final int HOODOOS = 56;
    public static final int ROCKY_PLATEAU = 57;
    public static final int TOWER_KARST_PLAINS = 58;
    public static final int TOWER_KARST_CANYONS = 59;
    public static final int TOWER_KARST_HILLS = 60;
    public static final int TOWER_KARST_HIGHLANDS = 61;
    public static final int TOWER_KARST_LAKE = 62;
    public static final int TOWER_KARST_BAY = 63;
    public static final int BURREN_PLATEAU = 64;
    public static final int BURREN_BADLANDS = 65;
    public static final int BURREN_BADLANDS_TALL = 66;
    public static final int BURREN_ROCHE_MOUTONEE = 67;
    public static final int BURREN_PLAINS = 68;
    public static final int SHILIN_PLAINS = 69;
    public static final int SHILIN_CANYONS = 70;
    public static final int SHILIN_HILLS = 71;
    public static final int SHILIN_HIGHLANDS = 72;
    public static final int SHILIN_PLATEAU = 73;
    public static final int DOLINE_PLAINS = 74;
    public static final int DOLINE_HILLS = 75;
    public static final int DOLINE_ROLLING_HILLS = 76;
    public static final int DOLINE_HIGHLANDS = 77;
    public static final int DOLINE_PLATEAU = 78;
    public static final int DOLINE_CANYONS = 79;
    public static final int CENOTE_PLAINS = 80;
    public static final int CENOTE_HILLS = 81;
    public static final int CENOTE_ROLLING_HILLS = 82;
    public static final int CENOTE_CANYONS = 83;
    public static final int CENOTE_HIGHLANDS = 84;
    public static final int CENOTE_PLATEAU = 85;
    public static final int EXTREME_DOLINE_PLATEAU = 86;
    public static final int EXTREME_DOLINE_MOUNTAINS = 87;
    public static final int ACTIVE_SHIELD_VOLCANO = 88;
    public static final int DORMANT_SHIELD_VOLCANO = 89;
    public static final int EXTINCT_SHIELD_VOLCANO = 90;
    public static final int ANCIENT_SHIELD_VOLCANO = 91;
    public static final int SUNKEN_SHIELD_VOLCANO = 92;
    public static final int SHIELD_VOLCANO_SHORE = 93;
    public static final int OLD_SHIELD_VOLCANO_SHORE = 94;
    public static final int ICE_SHEET = 95;
    public static final int ICE_SHEET_MOUNTAINS = 96;
    public static final int ICE_SHEET_OCEANIC_MOUNTAINS = 97;
    public static final int ICE_SHEET_VOLCANIC_MOUNTAINS = 98;
    public static final int ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS = 99;
    public static final int ICE_SHEET_SHIELD_VOLCANO = 100;
    public static final int ICE_SHEET_TUYAS = 101;
    public static final int SUBGLACIAL_LAKE = 102;
    public static final int ICE_SHEET_EDGE = 103;
    public static final int ICE_SHEET_TUYAS_EDGE = 104;
    public static final int ICE_SHEET_OCEANIC = 105;
    public static final int ICE_SHEET_OCEANIC_MOUNTAINS_EDGE = 106;
    public static final int ICE_SHEET_MOUNTAINS_EDGE = 107;
    public static final int GLACIATED_MOUNTAINS = 108;
    public static final int GLACIATED_OCEANIC_MOUNTAINS = 109;
    public static final int GLACIATED_VOLCANIC_MOUNTAINS = 110;
    public static final int GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS = 111;
    public static final int MELTWATER_LAKE = 112;
    public static final int GLACIATED_SHIELD_VOLCANO = 113;
    public static final int ICE_SHEET_SHORE = 114;
    public static final int GLACIALLY_CARVED_MOUNTAINS = 115;
    public static final int GLACIALLY_CARVED_OCEANIC_MOUNTAINS = 116;
    public static final int GLACIALLY_CARVED_VOLCANIC_MOUNTAINS = 117;
    public static final int GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS = 118;
    public static final int DRUMLINS = 119;
    public static final int TUYAS = 120;
    public static final int KNOB_AND_KETTLE = 121;
    public static final int PATTERNED_GROUND = 122;
    public static final int INVERTED_PATTERNED_GROUND = 123;
    public static final int STONE_CIRCLES = 124;

    /** @return The TFC name of the biome id, for example {@code "PLAINS"}. */
    public static String name(int id) {
        if (id < 0 || id >= COUNT) {
            throw new IllegalArgumentException("Biome id out of range: " + id);
        }
        return NAMES[id];
    }

    public static boolean isOcean(int value) {
        return value == OCEAN || value == DEEP_OCEAN || value == DEEP_OCEAN_TRENCH || value == OCEAN_REEF || value == OCEAN_RIDGE
            || value == OCEANIC_VOLCANIC_ARC || value == OCEAN_ATOLLS || value == DEEP_OCEAN_ATOLLS;
    }

    public static boolean isLake(int value) {
        return value == LAKE || value == MELTWATER_LAKE || value == MOUNTAIN_LAKE || value == OCEANIC_MOUNTAIN_LAKE || value == PLATEAU_LAKE
            || value == OLD_MOUNTAIN_LAKE || value == SUBGLACIAL_LAKE || value == RIFT_LAKE || value == TOWER_KARST_LAKE
            || value == VOLCANIC_OCEANIC_MOUNTAIN_LAKE || value == VOLCANIC_MOUNTAIN_LAKE;
    }

    public static boolean isFlatIceSheet(int value) {
        return value == ICE_SHEET || value == ICE_SHEET_TUYAS || value == SUBGLACIAL_LAKE;
    }

    /** @return {@code true} if a lake may be placed on this biome (TFC's {@code TFCLayers.hasLake}). */
    public static boolean hasLake(int value) {
        return !isOcean(value) && !isLake(value) && value != BADLANDS && value != ACTIVE_SHIELD_VOLCANO && value != DORMANT_SHIELD_VOLCANO
            && value != EXTINCT_SHIELD_VOLCANO && value != ANCIENT_SHIELD_VOLCANO && value != ICE_SHEET_MOUNTAINS
            && value != ICE_SHEET_MOUNTAINS_EDGE && value != ICE_SHEET_OCEANIC_MOUNTAINS && value != ICE_SHEET_OCEANIC_MOUNTAINS_EDGE
            && value != ICE_SHEET_SHIELD_VOLCANO && value != ICE_SHEET_SHORE && value != GLACIATED_SHIELD_VOLCANO
            && value != GLACIATED_MOUNTAINS && value != GLACIATED_OCEANIC_MOUNTAINS && value != GLACIALLY_CARVED_MOUNTAINS
            && value != GLACIALLY_CARVED_OCEANIC_MOUNTAINS && value != SALT_FLATS && value != MUD_FLATS;
    }

    /** @return The lake biome that replaces a biome with a lake (TFC's {@code TFCLayers.lakeFor}). */
    public static int lakeFor(int value) {
        if (value == MOUNTAINS || value == COLLISIONAL_MOUNTAINS) {
            return MOUNTAIN_LAKE;
        }
        if (value == VOLCANIC_MOUNTAINS) {
            return VOLCANIC_MOUNTAIN_LAKE;
        }
        if (value == OLD_MOUNTAINS || value == EXTREME_DOLINE_MOUNTAINS) {
            return OLD_MOUNTAIN_LAKE;
        }
        if (value == OCEANIC_MOUNTAINS) {
            return OCEANIC_MOUNTAIN_LAKE;
        }
        if (value == VOLCANIC_OCEANIC_MOUNTAINS) {
            return VOLCANIC_OCEANIC_MOUNTAIN_LAKE;
        }
        if (value == PLATEAU || value == PLATEAU_WIDE || value == ROCKY_PLATEAU || value == BURREN_PLATEAU || value == SHILIN_PLATEAU
            || value == DOLINE_PLATEAU || value == CENOTE_PLATEAU || value == EXTREME_DOLINE_PLATEAU) {
            return PLATEAU_LAKE;
        }
        if (isFlatIceSheet(value)) {
            return SUBGLACIAL_LAKE;
        }
        if (value == ICE_SHEET_EDGE) {
            return MELTWATER_LAKE;
        }
        if (value == TOWER_KARST_CANYONS || value == TOWER_KARST_HIGHLANDS || value == TOWER_KARST_HILLS || value == TOWER_KARST_PLAINS) {
            return TOWER_KARST_LAKE;
        }
        return LAKE;
    }
}
