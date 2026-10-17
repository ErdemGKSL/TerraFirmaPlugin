/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/ChooseBiomes.java @ c3b4d9536 (the expressions and their order are TFC's;
 * the karst branch is kept, but no point is karst yet, see the ledger)
 */
package dev.tfp.core.worldgen.biome;

import static dev.tfp.core.worldgen.biome.TfcBiomes.*;

import dev.tfp.core.worldgen.layer.Area;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.region.Region;

/**
 * Chooses the biome of every point of a region from the annotations the earlier tasks wrote: the island, rift, river,
 * mountain, land, barrier and ocean rules, the hotspot ages, the rainfall and temperature limits, the karst variants, and the
 * lake replacement. Pure: the random choices come from the two seeds, and the blob area from the caller. Runs on one thread.
 */
public final class BiomeChoice {
    private static final int[][] ALTITUDE_BIOMES = {
        {PLAINS, PLAINS, HILLS, HILLS, ROLLING_HILLS, LOW_CANYONS, LOWLANDS, LOWLANDS}, // Low
        {PLAINS, HILLS, ROLLING_HILLS, ROLLING_HILLS, ROLLING_HILLS, HIGHLANDS, BUTTES, MESAS, BADLANDS, PLATEAU_WIDE, CANYONS, CANYONS, LOW_CANYONS}, // Mid
        {HIGHLANDS, HIGHLANDS, HIGHLANDS, HIGHLANDS, ROLLING_HILLS, ROLLING_HILLS, BADLANDS, BADLANDS, STAIR_STEP_CANYONS, PLATEAU, PLATEAU, PLATEAU, PLATEAU_WIDE, OLD_MOUNTAINS, OLD_MOUNTAINS, OLD_MOUNTAINS, OLD_MOUNTAINS}, // High
    };
    private static final int[][] ICE_SHEET_ALTITUDE_BIOMES = {
        {ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET_TUYAS}, // Low
        {ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET_TUYAS, ICE_SHEET, ICE_SHEET_TUYAS}, // Mid
        {ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET, ICE_SHEET_TUYAS, ICE_SHEET_TUYAS, ICE_SHEET_MOUNTAINS, ICE_SHEET_MOUNTAINS}, // High
    };
    private static final int[][] PALEO_ICE_SHEET_ALTITUDE_BIOMES = {
        {PATTERNED_GROUND, INVERTED_PATTERNED_GROUND, KNOB_AND_KETTLE, KNOB_AND_KETTLE, KNOB_AND_KETTLE, DRUMLINS, TUYAS, LOWLANDS, LOWLANDS}, // Low
        {PATTERNED_GROUND, KNOB_AND_KETTLE, DRUMLINS, DRUMLINS, DRUMLINS, DRUMLINS, TUYAS, TUYAS}, // Mid
        {DRUMLINS, DRUMLINS, DRUMLINS, BADLANDS, BADLANDS, PLATEAU, PLATEAU, PLATEAU, PLATEAU_WIDE, ICE_SHEET_MOUNTAINS}, // High
    };
    private static final int[][] DESERT_ALTITUDE_BIOMES = {
        {BUTTES, GRASSY_DUNES, DUNE_SEA, DUNE_SEA, DUNE_SEA, SALT_FLATS, SALT_FLATS}, // Low
        {DUNE_SEA, BUTTES, BUTTES, HOODOOS, MESAS, MESAS, STAIR_STEP_CANYONS, BADLANDS, PLATEAU, CANYONS, WHORLED_CANYONS}, // Mid
        {HOODOOS, MESAS, STAIR_STEP_CANYONS, STAIR_STEP_CANYONS, ROCKY_PLATEAU, ROCKY_PLATEAU, OLD_MOUNTAINS, OLD_MOUNTAINS, OLD_MOUNTAINS, WHORLED_CANYONS}, // High
    };
    private static final int[][] SEMI_ARID_ALTITUDE_BIOMES = {
        {PLAINS, HILLS, HILLS, GRASSY_DUNES, GRASSY_DUNES, GRASSY_DUNES, GRASSY_DUNES, LOW_CANYONS, LOWLANDS, LOWLANDS, MUD_FLATS}, // Low
        {PLAINS, HILLS, ROLLING_HILLS, ROLLING_HILLS, HIGHLANDS, HIGHLANDS, BADLANDS, BADLANDS, PLATEAU_WIDE, CANYONS, LOW_CANYONS, WHORLED_CANYONS, BUTTES, MESAS, MESAS, HOODOOS}, // Mid
        {HIGHLANDS, HIGHLANDS, MESAS, HOODOOS, ROLLING_HILLS, ROLLING_HILLS, BADLANDS, BADLANDS, PLATEAU_WIDE, ROCKY_PLATEAU, STAIR_STEP_CANYONS, STAIR_STEP_CANYONS, OLD_MOUNTAINS, OLD_MOUNTAINS, WHORLED_CANYONS}, // High
    };
    private static final int[] KNOB_AND_KETTLE_BIOMES = {KNOB_AND_KETTLE, PATTERNED_GROUND, INVERTED_PATTERNED_GROUND};
    private static final int[] ISLAND_BIOMES = {ROLLING_HILLS, VOLCANIC_OCEANIC_MOUNTAINS, VOLCANIC_MOUNTAIN_ISLANDS, GUANO_ISLAND};
    private static final int[] RIFT_VALLEY_BIOMES = {RIFT_VALLEY, RIFT_VALLEY, RIFT_VALLEY, RIFT_LAKE, RIFT_LAKE};
    private static final int[] VOLCANIC_ARC_BIOMES = {VOLCANIC_OCEANIC_MOUNTAINS, VOLCANIC_OCEANIC_MOUNTAINS, VOLCANIC_OCEANIC_MOUNTAINS, VOLCANIC_ISLAND, OCEANIC_VOLCANIC_ARC};
    private static final int[] BARRIER_ISLAND_BIOMES = {PLAINS, PLAINS, PLAINS, SALT_MARSH, TIDAL_FLATS};

    private BiomeChoice() {}

    /**
     * Writes {@link Region.Point#biome} for every point of the region.
     *
     * @param region       The region, annotated up to the biome task.
     * @param blobArea     The uniform blob field that groups points into biome choices. Not thread-safe: one per thread.
     * @param continentNoise The continent noise, sampled at each point (TFC's {@code continentNoise}).
     * @param rngSeed      The random seed that picks among the biomes of a list.
     * @param climateSeed  The random seed that moves the low-rainfall and badlands limits.
     */
    public static void choose(Region region, Area blobArea, Noise2D continentNoise, long rngSeed, long climateSeed) {
        for (final var point : region.points()) {
            final int areaSeed = blobArea.get(point.x, point.z);
            if (point.island()) {
                point.biome = randomSeededFrom(rngSeed, areaSeed, ISLAND_BIOMES);
            }
            // Rift valleys need to fire before mountains to override them
            else if (point.distanceToEdge < 3 && point.divergence > 0 && point.land()) {
                if (point.distanceToOcean > 2) {
                    point.biome = randomSeededFrom(rngSeed, areaSeed, RIFT_VALLEY_BIOMES);
                } else {
                    point.biome = RIFT_VALLEY;
                }
            }
            // Don't want rivers cutting wide valleys through collisional mountains, rift valleys, or ice sheets
            else if (point.river() && point.distanceToEdge > 4 && point.temperature > -16f + 0.006f * point.rainfall) {
                point.biome = RIVER_VALLEY;
            } else if (point.mountain()) {
                point.biome = mountainBiome(point, continentNoise);
            } else if (point.land()) {
                point.biome = landBiome(point, rngSeed, areaSeed);
            } else if (point.barrierIsland()) {
                if (point.volcanic()) {
                    point.biome = randomSeededFrom(rngSeed, areaSeed, VOLCANIC_ARC_BIOMES);
                } else {
                    point.biome = randomSeededFrom(rngSeed, areaSeed, BARRIER_ISLAND_BIOMES);
                }
            } else if (point.oceanDepth == 1) {
                point.biome = point.volcanic() ? OCEANIC_VOLCANIC_ARC : OCEAN_REEF;
            } else if (point.oceanDepth == 2) {
                point.biome = point.temperature > 12 && point.distanceToLand > 4 ? OCEAN_ATOLLS : OCEAN;
            } else if (point.oceanDepth == 3) {
                point.biome = OCEAN_RIDGE;
            } else if (point.oceanDepth == 5) {
                point.biome = DEEP_OCEAN_TRENCH;
            } else {
                point.biome = point.temperature > 12 && point.distanceToLand > 3 ? DEEP_OCEAN_ATOLLS : DEEP_OCEAN;
            }

            // Add hot spot biomes
            final byte age = point.hotSpotAge;
            if (age > 0) {
                if (age == 4 && (point.biome == OCEAN || point.biome == DEEP_OCEAN || point.biome == OCEAN_REEF || point.biome == DEEP_OCEAN_TRENCH)) {
                    point.biome = SUNKEN_SHIELD_VOLCANO;
                } else {
                    point.biome = hotSpotBiome(point.hotSpotAge);
                }
            }

            // Adjust certain biome placements by climate. Low, freshwater biomes don't make much sense appearing in
            // very low rainfall areas
            final float minRainForLowFreshWaterBiomes = 90f + Math.floorMod(areaSeed ^ climateSeed, 40);
            final float rainfall = point.rainfall;
            final float temperature = point.temperature;
            if (rainfall < minRainForLowFreshWaterBiomes) {
                if (rainfall <= 55) {
                    if (point.biome == LOWLANDS || point.biome == LOW_CANYONS) {
                        point.biome = SALT_FLATS;
                    } else if (point.biome == HILLS || point.biome == ROLLING_HILLS || point.biome == PLATEAU) {
                        point.biome = DUNE_SEA;
                    }
                }
            }
            if (rainfall < 145 && (point.biome == PATTERNED_GROUND || point.biome == INVERTED_PATTERNED_GROUND)) {
                point.biome = STONE_CIRCLES;
            }

            // Prevent badlands from appearing in very high rainfall environments
            final float maxRainfallForBadlands = 420f + Math.floorMod(areaSeed ^ climateSeed, 40);
            if (rainfall > maxRainfallForBadlands) {
                if (point.biome == BADLANDS) {
                    point.biome = HIGHLANDS;
                }
            }

            // Special Biome Glaciation
            final float maxIceSheetTemp = -14f + 0.006f * rainfall;
            if (point.land() && temperature < maxIceSheetTemp) {
                final int biome = point.biome;
                if (biome == ACTIVE_SHIELD_VOLCANO || biome == DORMANT_SHIELD_VOLCANO || biome == EXTINCT_SHIELD_VOLCANO) {
                    point.biome = ICE_SHEET_SHIELD_VOLCANO;
                }
            } else if (temperature < maxIceSheetTemp + 4f) {
                final int biome = point.biome;
                if (biome == ACTIVE_SHIELD_VOLCANO || biome == DORMANT_SHIELD_VOLCANO || biome == EXTINCT_SHIELD_VOLCANO) {
                    point.biome = GLACIATED_SHIELD_VOLCANO;
                }
            }

            // Karst Biomes
            if (point.isSurfaceRockKarst) {
                // High rainfall karst biomes
                if (rainfall > 375) {
                    // Check for hot, wet climates to place tower karsts
                    if (rainfall > 425 && rainfall + 10 * temperature > 500) {
                        point.biome = towerKarstBiome(point.biome);
                    }
                    // Tropical/Subtropical wet areas not filled in by towers are Shilin
                    else if (temperature > 9) {
                        point.biome = shilinBiome(point.biome);
                    }
                    // Colder wet biomes are Burren
                    else if (temperature < 0) {
                        point.biome = burrenBiome(point.biome);
                    } else {
                        point.biome = dolineBiome(point.biome);
                    }
                } else if (rainfall > 250) {
                    if (temperature > 5) {
                        point.biome = cenoteBiome(point.biome);
                    } else {
                        point.biome = dolineBiome(point.biome);
                    }
                }
            }

            // Increase prevalence/size of salt marshes in climates where mangroves can generate
            if (point.distanceToOcean <= 2 && point.biome == LOWLANDS && point.rainfall > 220 && point.temperature > 18) {
                point.biome = SALT_MARSH;
            }

            if (point.lake() && hasLake(point.biome)) {
                point.biome = lakeFor(point.biome);
            }
        }
    }

    private static int mountainBiome(Region.Point point, Noise2D continentNoise) {
        // Collisional mountains and surrounding oceanic mountains
        final float temp = point.temperature;
        if (point.divergence < 0 && point.distanceToEdge < 1.2 * continentNoise.noise(point.x, point.z) - 5.2) {
            // Different temperature limits used because biomes at different elevations
            final float maxIceSheetTemp = -16f + 0.006f * point.rainfall;
            if (point.distanceToOcean < 3) {
                if (temp < maxIceSheetTemp + 2) {
                    return ICE_SHEET_OCEANIC_MOUNTAINS;
                } else if (temp < maxIceSheetTemp + 6) {
                    return GLACIATED_OCEANIC_MOUNTAINS;
                } else if (temp < maxIceSheetTemp + 10) {
                    return GLACIALLY_CARVED_OCEANIC_MOUNTAINS;
                }
                return OCEANIC_MOUNTAINS;
            }
            if (temp < maxIceSheetTemp + 4) {
                return ICE_SHEET_MOUNTAINS;
            } else if (temp < maxIceSheetTemp + 8) {
                return GLACIATED_MOUNTAINS;
            } else if (temp < maxIceSheetTemp + 11) {
                return GLACIALLY_CARVED_MOUNTAINS;
            }
            return COLLISIONAL_MOUNTAINS;
        } else if (point.coastalMountain()) {
            // Different temperature limits used because biomes at different elevations
            final float maxIceSheetTemp = -16f + 0.006f * point.rainfall;
            if (point.volcanic()) {
                if (temp < maxIceSheetTemp + 2) {
                    return ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS;
                } else if (temp < maxIceSheetTemp + 6) {
                    return GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS;
                } else if (temp < maxIceSheetTemp + 10) {
                    return GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS;
                }
                return VOLCANIC_OCEANIC_MOUNTAINS;
            }
            if (temp < maxIceSheetTemp + 2) {
                return ICE_SHEET_OCEANIC_MOUNTAINS;
            } else if (temp < maxIceSheetTemp + 6) {
                return GLACIATED_OCEANIC_MOUNTAINS;
            } else if (temp < maxIceSheetTemp + 10) {
                return GLACIALLY_CARVED_OCEANIC_MOUNTAINS;
            }
            return OCEANIC_MOUNTAINS;
        }
        final float maxIceSheetTemp = -14f + 0.006f * point.rainfall;
        if (point.volcanic()) {
            if (temp < maxIceSheetTemp + 4) {
                return ICE_SHEET_VOLCANIC_MOUNTAINS;
            } else if (temp < maxIceSheetTemp + 8) {
                return GLACIATED_VOLCANIC_MOUNTAINS;
            } else if (temp < maxIceSheetTemp + 11) {
                return GLACIALLY_CARVED_VOLCANIC_MOUNTAINS;
            }
            return VOLCANIC_MOUNTAINS;
        }
        if (temp < maxIceSheetTemp + 4) {
            return ICE_SHEET_MOUNTAINS;
        } else if (temp < maxIceSheetTemp + 8) {
            return GLACIATED_MOUNTAINS;
        } else if (temp < maxIceSheetTemp + 11) {
            return GLACIALLY_CARVED_MOUNTAINS;
        }
        return MOUNTAINS;
    }

    private static int landBiome(Region.Point point, long rngSeed, int areaSeed) {
        final float rain = point.rainfall;
        final float maxIceSheetTemp = -17f + 0.006f * rain;
        final float temp = point.temperature;
        if (temp < maxIceSheetTemp) {
            int biome = randomSeededFrom(rngSeed, areaSeed, ICE_SHEET_ALTITUDE_BIOMES[point.discreteBiomeAltitude()]);
            if (point.distanceToOcean < 3 && isFlatIceSheet(biome)) {
                biome = ICE_SHEET_OCEANIC;
            }
            return biome;
        } else if (temp < maxIceSheetTemp + 1) {
            return ICE_SHEET_EDGE;
        } else if (temp < maxIceSheetTemp + 2.5) {
            return randomSeededFrom(rngSeed, areaSeed, KNOB_AND_KETTLE_BIOMES);
        } else if (temp < maxIceSheetTemp + 6) {
            return randomSeededFrom(rngSeed, areaSeed, PALEO_ICE_SHEET_ALTITUDE_BIOMES[point.discreteBiomeAltitude()]);
        } else if (rain < 60) {
            return randomSeededFrom(rngSeed, areaSeed, DESERT_ALTITUDE_BIOMES[point.discreteBiomeAltitude()]);
        } else if (rain < 155) {
            return randomSeededFrom(rngSeed, areaSeed, SEMI_ARID_ALTITUDE_BIOMES[point.discreteBiomeAltitude()]);
        }
        return randomSeededFrom(rngSeed, areaSeed, ALTITUDE_BIOMES[point.discreteBiomeAltitude()]);
    }

    private static int hotSpotBiome(int age) {
        if (age == 4) {
            return ANCIENT_SHIELD_VOLCANO;
        }
        if (age == 3) {
            return EXTINCT_SHIELD_VOLCANO;
        }
        if (age == 2) {
            return DORMANT_SHIELD_VOLCANO;
        }
        if (age == 1) {
            return ACTIVE_SHIELD_VOLCANO;
        }
        return PLAINS;
    }

    private static int towerKarstBiome(int biome) {
        if (biome == SALT_MARSH) {
            return TOWER_KARST_BAY;
        } else if (biome == LOWLANDS) {
            return TOWER_KARST_LAKE;
        } else if (biome == PLAINS || biome == LOW_CANYONS) {
            return TOWER_KARST_PLAINS;
        } else if (biome == CANYONS) {
            return TOWER_KARST_CANYONS;
        } else if (biome == HILLS || biome == ROLLING_HILLS || biome == BADLANDS) {
            return TOWER_KARST_HILLS;
        } else if (biome == HIGHLANDS) {
            return TOWER_KARST_HIGHLANDS;
        } else if (biome == PLATEAU || biome == PLATEAU_WIDE) {
            return EXTREME_DOLINE_PLATEAU;
        } else if (biome == OLD_MOUNTAINS || biome == MOUNTAINS || biome == OCEANIC_MOUNTAINS) {
            return EXTREME_DOLINE_MOUNTAINS;
        }
        return biome;
    }

    private static int shilinBiome(int biome) {
        if (biome == PLAINS) {
            return SHILIN_PLAINS;
        }
        if (biome == CANYONS) {
            return SHILIN_CANYONS;
        }
        if (biome == ROLLING_HILLS || biome == BADLANDS) {
            return SHILIN_HILLS;
        }
        if (biome == PLATEAU || biome == PLATEAU_WIDE) {
            return SHILIN_PLATEAU;
        }
        if (biome == HIGHLANDS) {
            return SHILIN_HIGHLANDS;
        }
        return biome;
    }

    private static int burrenBiome(int biome) {
        if (biome == PLAINS || biome == CANYONS) {
            return BURREN_PLAINS;
        }
        if (biome == BADLANDS || biome == HILLS || biome == ROLLING_HILLS) {
            return BURREN_BADLANDS;
        }
        if (biome == DRUMLINS) {
            return BURREN_ROCHE_MOUTONEE;
        }
        if (biome == HIGHLANDS) {
            return BURREN_BADLANDS_TALL;
        }
        if (biome == PLATEAU || biome == PLATEAU_WIDE) {
            return BURREN_PLATEAU;
        }
        return biome;
    }

    private static int dolineBiome(int biome) {
        if (biome == CANYONS) {
            return DOLINE_CANYONS;
        } else if (biome == PLAINS || biome == LOW_CANYONS) {
            return DOLINE_PLAINS;
        } else if (biome == HILLS) {
            return DOLINE_HILLS;
        } else if (biome == ROLLING_HILLS) {
            return DOLINE_ROLLING_HILLS;
        } else if (biome == HIGHLANDS) {
            return DOLINE_HIGHLANDS;
        } else if (biome == PLATEAU || biome == PLATEAU_WIDE) {
            return DOLINE_PLATEAU;
        }
        return biome;
    }

    private static int cenoteBiome(int biome) {
        if (biome == CANYONS) {
            return CENOTE_CANYONS;
        } else if (biome == PLAINS || biome == LOW_CANYONS) {
            return CENOTE_PLAINS;
        } else if (biome == HILLS) {
            return CENOTE_HILLS;
        } else if (biome == ROLLING_HILLS) {
            return CENOTE_ROLLING_HILLS;
        } else if (biome == HIGHLANDS) {
            return CENOTE_HIGHLANDS;
        } else if (biome == PLATEAU || biome == PLATEAU_WIDE) {
            return CENOTE_PLATEAU;
        }
        return biome;
    }

    /** TFC's {@code randomSeededFrom}: the choice is picked by the seeds, with a floor mod so negative values wrap. */
    static int randomSeededFrom(long rngSeed, int areaSeed, int[] choices) {
        return choices[Math.floorMod(rngSeed ^ areaSeed, choices.length)];
    }
}
