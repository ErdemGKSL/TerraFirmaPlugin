/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (the biomes registered with {@code .spawnable()})
 */
package dev.tfp.core.worldgen.spawn;

import dev.tfp.core.worldgen.biome.TfcBiomes;

/**
 * The biomes TFC can spawn a player in: the biome extensions registered with {@code .spawnable()} at c3b4d9536. The ocean,
 * river, lake, shore and ice-edge biomes are not spawnable. Listed by TFC field name and resolved to ids through
 * {@link TfcBiomes#name}, so a name that does not resolve fails at class load (the golden test checks all 93).
 */
public final class SpawnBiomes {
    /** TFC's spawnable biome names, in TFC's registration order. */
    private static final String[] NAMES = {
        "RIFT_VALLEY", "COLLISIONAL_MOUNTAINS", "PLAINS", "HILLS", "LOWLANDS", "SALT_MARSH", "LOW_CANYONS",
        "RIVER_VALLEY", "ROLLING_HILLS", "HIGHLANDS", "BADLANDS", "PLATEAU", "PLATEAU_WIDE", "CANYONS", "MOUNTAINS",
        "OLD_MOUNTAINS", "OCEANIC_MOUNTAINS", "GUANO_ISLAND", "MUD_FLATS", "SALT_FLATS", "DUNE_SEA", "GRASSY_DUNES",
        "WHORLED_CANYONS", "STAIR_STEP_CANYONS", "MESAS", "BUTTES", "HOODOOS", "ROCKY_PLATEAU", "TOWER_KARST_PLAINS",
        "TOWER_KARST_HILLS", "TOWER_KARST_CANYONS", "TOWER_KARST_HIGHLANDS", "TOWER_KARST_LAKE", "TOWER_KARST_BAY",
        "BURREN_PLATEAU", "BURREN_BADLANDS", "BURREN_BADLANDS_TALL", "BURREN_PLAINS", "BURREN_ROCHE_MOUTONEE",
        "SHILIN_PLAINS", "SHILIN_CANYONS", "SHILIN_HILLS", "SHILIN_HIGHLANDS", "SHILIN_PLATEAU", "DOLINE_PLAINS",
        "DOLINE_HILLS", "DOLINE_ROLLING_HILLS", "DOLINE_HIGHLANDS", "DOLINE_PLATEAU", "DOLINE_CANYONS",
        "CENOTE_PLAINS", "CENOTE_HILLS", "CENOTE_ROLLING_HILLS", "CENOTE_CANYONS", "CENOTE_HIGHLANDS",
        "CENOTE_PLATEAU", "EXTREME_DOLINE_PLATEAU", "EXTREME_DOLINE_MOUNTAINS", "ACTIVE_SHIELD_VOLCANO",
        "DORMANT_SHIELD_VOLCANO", "EXTINCT_SHIELD_VOLCANO", "ANCIENT_SHIELD_VOLCANO", "SUNKEN_SHIELD_VOLCANO",
        "SHIELD_VOLCANO_SHORE", "OLD_SHIELD_VOLCANO_SHORE", "ICE_SHEET", "ICE_SHEET_MOUNTAINS",
        "ICE_SHEET_OCEANIC_MOUNTAINS", "ICE_SHEET_VOLCANIC_MOUNTAINS", "ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS",
        "ICE_SHEET_SHIELD_VOLCANO", "ICE_SHEET_TUYAS", "ICE_SHEET_EDGE", "ICE_SHEET_TUYAS_EDGE",
        "ICE_SHEET_MOUNTAINS_EDGE", "ICE_SHEET_OCEANIC_MOUNTAINS_EDGE", "ICE_SHEET_OCEANIC", "ICE_SHEET_SHORE",
        "GLACIATED_MOUNTAINS", "GLACIATED_OCEANIC_MOUNTAINS", "GLACIATED_VOLCANIC_MOUNTAINS",
        "GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS", "GLACIATED_SHIELD_VOLCANO", "GLACIALLY_CARVED_MOUNTAINS",
        "GLACIALLY_CARVED_OCEANIC_MOUNTAINS", "GLACIALLY_CARVED_VOLCANIC_MOUNTAINS",
        "GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS", "DRUMLINS", "TUYAS", "KNOB_AND_KETTLE", "PATTERNED_GROUND",
        "INVERTED_PATTERNED_GROUND", "STONE_CIRCLES",
    };

    private static final boolean[] SPAWNABLE = spawnable();

    private SpawnBiomes() {}

    /** @return whether TFC's {@code findSpawnBiome} may pick this biome id (false for any id outside the TFC range). */
    public static boolean isSpawnable(int biomeId) {
        return biomeId >= 0 && biomeId < SPAWNABLE.length && SPAWNABLE[biomeId];
    }

    /** @return the number of spawnable biome ids. */
    public static int count() {
        int n = 0;
        for (boolean s : SPAWNABLE) {
            if (s) {
                n++;
            }
        }
        return n;
    }

    private static boolean[] spawnable() {
        final boolean[] spawnable = new boolean[TfcBiomes.COUNT];
        for (String name : NAMES) {
            boolean found = false;
            for (int id = 0; id < TfcBiomes.COUNT; id++) {
                if (TfcBiomes.name(id).equals(name)) {
                    spawnable[id] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("spawnable TFC biome is not a biome id: " + name);
            }
        }
        return spawnable;
    }
}
