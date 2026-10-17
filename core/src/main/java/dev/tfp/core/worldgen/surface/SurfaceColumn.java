/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * The inputs of one block column, as TFC's {@code SurfaceBuilderContext} holds them for one column: the position, the sea
 * level, the slope, the climate of the chunk at the column (sea-level temperature, rainfall, rain variance and base
 * groundwater), the tide level of the shore, and whether the sea-level rock is mafic.
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
 * @param seaLevelMafic whether the rock at sea level is mafic (basalt or gabbro in TFC's rock settings)
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
    boolean seaLevelMafic
) {
    /** TFC's {@code SurfaceBuilderContext.groundWater()}: the base groundwater plus the rainfall. */
    public float groundWater() {
        return baseGroundwater + rainfall;
    }
}
