/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * The inputs of one block column, as TFC's {@code SurfaceBuilderContext} holds them for one column: the position, the sea
 * level, the slope, the climate of the chunk at the column (sea-level temperature, rainfall, rain variance and base
 * groundwater), the tide level of the shore, the rocks the surface states ask for, the weight of the column's biome, and the
 * inputs of the builders that look outside the column (the original biome under a river, the cinder cone, the warmest month).
 *
 * @param x            the block x
 * @param z            the block z
 * @param seaLevel     the sea level, absolute Y ({@code 63} in TFC)
 * @param slope        the slope of the column, in TFC's units ({@code 0} is flat, {@code 15} and above is a steep cliff)
 * @param temperature  the annual average sea-level temperature, degrees Celsius
 * @param rainfall     the average annual rainfall, mm
 * @param baseGroundwater the groundwater from nearby rivers, mm
 * @param rainVariance the seasonal rainfall variance, in [-1, 1]
 * @param tideLevel    the shore tide level of the column, absolute Y ({@code (int)} of TFC's shore tide noise)
 * @param seaLevelMafic whether the rock at sea level is mafic ({@code getSeaLevelRock().mafic()})
 * @param weight       the weight of the column's biome among the blended biomes, in [0, 1] ({@code localBiomeWeights})
 * @param preVolcanicHeight the surface height before the centered features (cinder cones) raised it
 * @param cinderEasing TFC's {@code CenteredFeatureNoise.cinder(seed).calculateEasing} at the column, with the frequency of
 *                     the chunk's cinder cone biome; {@code 0} when that biome has no cinder cones
 * @param warmestMonth the warmest month's temperature at sea level ({@code getAverageMonthlyTemperature(z, seaLevel,
 *                     averageTemperature, 1, true)}, {@code SeaIce.warmestMonth}), which decides the sea ice and the icebergs; positive infinity
 *                     where there is no climate model, so nothing freezes
 * @param rockKarst    whether the rock at {@code y = 0} of the column is karst. TFC's badlands read {@code getRock()} before
 *                     the cursor moves, so they see the rock at height zero
 * @param rockMafic    whether the rock at {@code y = 0} of the column is mafic (the same read)
 * @param originalBiome the TFC biome id of the column without its river ({@code localBiomesNoRivers}); {@code -1} when
 *                     unknown, which the river builder treats as a biome with sandy river shores
 */
public record SurfaceColumn(
    int x,
    int z,
    int seaLevel,
    double slope,
    float temperature,
    float rainfall,
    float baseGroundwater,
    float rainVariance,
    int tideLevel,
    boolean seaLevelMafic,
    double weight,
    int preVolcanicHeight,
    float cinderEasing,
    float warmestMonth,
    boolean rockKarst,
    boolean rockMafic,
    int originalBiome
) {
    /**
     * A column with only the inputs of the normal, simple and shore builders: full biome weight, no cinder cone, no frozen
     * sea, plain rock and an unknown original biome.
     */
    public SurfaceColumn(int x, int z, int seaLevel, double slope, float temperature, float rainfall, float baseGroundwater,
                         float rainVariance, int tideLevel, boolean seaLevelMafic) {
        this(x, z, seaLevel, slope, temperature, rainfall, baseGroundwater, rainVariance, tideLevel, seaLevelMafic,
            1.0, 0, 0f, Float.POSITIVE_INFINITY, false, false, -1);
    }

    /** TFC's {@code SurfaceBuilderContext.groundWater()}: the base groundwater plus the rainfall. */
    public float groundWater() {
        return baseGroundwater + rainfall;
    }
}
