/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/tree/ForestConfig.java @ c3b4d9536 (Entry: isValid, distanceFromMean
 * and the averages; codec removed; the bush, fallen tree, groundcover and soil disc fields are not carried), with the climate
 * fields of src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java
 */
package dev.tfp.core.worldgen.tree;

import java.util.Objects;
import java.util.Optional;

/**
 * One tree species of a forest ({@code tfc:forest_entry}): the climate it grows in and the features it places. Immutable.
 *
 * @param name                    the entry id, e.g. {@code tfp:tree/oak_entry}
 * @param normalTree              {@code normal_tree}
 * @param deadTree                {@code dead_tree}
 * @param oldGrowthTree           {@code old_growth_tree}; the normal tree when absent
 * @param krummholz               {@code krummholz}
 * @param oldGrowthChance         {@code old_growth_chance} (default 6)
 * @param spoilerOldGrowthChance  {@code spoiler_old_growth_chance} (default 200)
 * @param deadChance              {@code dead_chance} (default 75)
 * @param floating                {@code floating} (mangroves)
 */
public record ForestEntry(String name, Climate climate, TreeFeature normalTree, TreeFeature deadTree,
                          Optional<TreeFeature> oldGrowthTree, Optional<TreeFeature> krummholz, int oldGrowthChance,
                          int spoilerOldGrowthChance, int deadChance, boolean floating) {
    /** The codec defaults of {@code ForestConfig.Entry}. */
    public static final int DEFAULT_OLD_GROWTH_CHANCE = 6;
    public static final int DEFAULT_SPOILER_OLD_GROWTH_CHANCE = 200;
    public static final int DEFAULT_DEAD_CHANCE = 75;

    /** The climate ranges of {@code ClimatePlacement} that a forest entry reads (with the codec's defaults). */
    public record Climate(float minTemp, float maxTemp, float minGroundwater, float maxGroundwater, float minRainVariance,
                          float maxRainVariance, boolean rainVarianceAbsolute, int minElevation, int maxElevation) {}

    public ForestEntry {
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(normalTree, "normalTree");
        Objects.requireNonNull(deadTree, "deadTree");
    }

    /** {@code Entry.isValid}. */
    public boolean isValid(float temperature, float groundwater, float rainVar, float elevation) {
        final float adjustedRainVar = climate.rainVarianceAbsolute() ? Math.abs(rainVar) : rainVar;
        return groundwater >= climate.minGroundwater() && groundwater <= climate.maxGroundwater()
            && adjustedRainVar >= climate.minRainVariance() && adjustedRainVar <= climate.maxRainVariance()
            && temperature >= climate.minTemp() && temperature <= climate.maxTemp()
            && elevation >= climate.minElevation() && elevation <= climate.maxElevation();
    }

    /** {@code Entry.distanceFromMean}: a signed sum, with TFC's averages (half the range width, as TFC computes them). */
    public float distanceFromMean(float temperature, float groundwater, float rainVar, float elevation) {
        final float adjustedRainVar = climate.rainVarianceAbsolute() ? Math.abs(rainVar) : rainVar;
        final float tempDist = (temperature - averageTemp()) * 10f; // Normalize everything to a 0-500 scale
        final float waterDist = groundwater - averageGroundwater();
        final float rainVarDist = (adjustedRainVar - averageRainVar()) * 250f;
        final float elevationDistance = (elevation - averageElevation()) * 5;
        return tempDist + waterDist + rainVarDist + elevationDistance;
    }

    float averageTemp() {
        return (climate.maxTemp() - climate.minTemp()) / 2;
    }

    float averageGroundwater() {
        return (climate.maxGroundwater() - climate.minGroundwater()) / 2;
    }

    float averageRainVar() {
        return (climate.maxRainVariance() - climate.minRainVariance()) / 2;
    }

    float averageElevation() {
        return (climate.maxElevation() - climate.minElevation()) / 2f;
    }

    /** {@code getOldGrowthFeature}. */
    TreeFeature oldGrowthFeature() {
        return oldGrowthTree.orElse(normalTree);
    }
}
