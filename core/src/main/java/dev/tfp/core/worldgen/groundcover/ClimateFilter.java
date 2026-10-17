/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536 (the constructor's targets and
 * isValid), with src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java (adjustAvgTempForElev).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * TFC's {@code tfc:climate} placement filter: a feature origin passes when its temperature (adjusted for elevation),
 * groundwater, rain variance and height lie in the ranges, and, for a fuzzy filter, when a random draw beats the distance
 * from the middle of the ranges. Immutable.
 *
 * <p>Not ported: the forest density and forest types (the chunk data has no forest yet, so only filters without a forest
 * bound are used), and the southern hemisphere (the rain variance is not flipped). See the ledger.
 */
public final class ClimateFilter {
    /** {@code OverworldClimateModel.SEA_LEVEL}. */
    private static final float SEA_LEVEL = 63f;

    private final float minTemp;
    private final float maxTemp;
    private final float targetTemp;
    private final float minGroundwater;
    private final float maxGroundwater;
    private final float targetGroundwater;
    private final float minRainVariance;
    private final float maxRainVariance;
    private final boolean rainVarianceAbsolute;
    private final float targetRainVariance;
    private final int minElevation;
    private final int maxElevation;
    private final boolean fuzzy;
    private final boolean ignoreRivers;

    public ClimateFilter(float minTemp, float maxTemp, float minGroundwater, float maxGroundwater, float minRainVariance,
                         float maxRainVariance, boolean rainVarianceAbsolute, int minElevation, int maxElevation, boolean fuzzy,
                         boolean ignoreRivers) {
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.targetTemp = (minTemp + maxTemp) / 2f;
        this.minGroundwater = minGroundwater;
        this.maxGroundwater = maxGroundwater;
        this.targetGroundwater = (minGroundwater + maxGroundwater) / 2f;
        this.minRainVariance = minRainVariance;
        this.maxRainVariance = maxRainVariance;
        this.targetRainVariance = (minRainVariance + maxRainVariance) / 2f;
        this.rainVarianceAbsolute = rainVarianceAbsolute;
        this.minElevation = minElevation;
        this.maxElevation = maxElevation;
        this.fuzzy = fuzzy;
        this.ignoreRivers = ignoreRivers;
    }

    /** A filter on temperature and groundwater only, with the codec's defaults for the rest (not fuzzy). */
    public static ClimateFilter of(float minTemp, float maxTemp, float minGroundwater, float maxGroundwater) {
        return new ClimateFilter(minTemp, maxTemp, minGroundwater, maxGroundwater, -1f, 1f, false, -64, 320, false, false);
    }

    /** {@code EnvironmentHelpers.adjustAvgTempForElev}: -1.6 degrees per 10 blocks above sea level, at most 17.822. */
    public static float adjustAvgTempForElev(int y, float averageTemp) {
        if (y > SEA_LEVEL) {
            final float elevationTemperature = Math.clamp((y - SEA_LEVEL) * 0.16225f, 0f, 17.822f);
            return averageTemp - elevationTemperature;
        }
        return averageTemp;
    }

    /** {@code ClimatePlacement.isValid} at a position of the northern hemisphere. Draws two floats when fuzzy and in range. */
    public boolean isValid(GroundView view, int x, int y, int z, XoroshiroRandomSource random) {
        final float rainVar = rainVarianceAbsolute ? Math.abs(view.rainVariance(x, z)) : view.rainVariance(x, z);
        final float temperature = adjustAvgTempForElev(y, view.averageTemperature(x, z));
        final float groundwater = ignoreRivers ? view.averageRainfall(x, z) : view.groundwater(x, z);
        if (y >= minElevation && y <= maxElevation && minTemp <= temperature && temperature <= maxTemp && minGroundwater <= groundwater
            && groundwater <= maxGroundwater && minRainVariance <= rainVar && maxRainVariance >= rainVar) {
            if (fuzzy) {
                final float normTempDelta = Math.abs(temperature - targetTemp) / (maxTemp - minTemp);
                final float normGroundwaterDelta = Math.abs(groundwater - targetGroundwater) / (maxGroundwater - minGroundwater);
                final float normRainVarDelta = Math.abs(rainVar - targetRainVariance) / (maxRainVariance - minRainVariance);
                return random.nextFloat() * random.nextFloat() > Math.max(normTempDelta, Math.max(normGroundwaterDelta, normRainVarDelta));
            }
            return true;
        }
        return false;
    }
}
