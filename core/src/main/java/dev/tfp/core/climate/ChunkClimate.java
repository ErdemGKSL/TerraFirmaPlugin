/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/ChunkData.java @ c3b4d9536
 */
package dev.tfp.core.climate;

/**
 * The per-position climate values that TFC stores in chunk data and the climate model reads. They are built from the
 * region map by {@code dev.tfp.core.worldgen.chunk.ChunkData} (M2-GEN-05), so this model takes them as inputs.
 *
 * @param averageSeaLevelTemperature the average annual temperature at sea level, degrees Celsius
 * @param averageRainfall            the average annual rainfall, mm, in [0, 500]
 * @param rainfallVariance           the seasonal variance of rainfall, in [-1, 1]; positive means a dry January
 * @param baseGroundwater            the groundwater added by nearby fresh water, mm
 */
public record ChunkClimate(float averageSeaLevelTemperature, float averageRainfall, float rainfallVariance, float baseGroundwater) {
    /**
     * The fallback for a position without chunk data ({@code ChunkData.climateOrNeutral(null, x, z)}): a temperate 20 C
     * average at sea level, mid-range rainfall (250 mm), no seasonal rainfall variance and no groundwater. These are not
     * TFC's values for any chunk. The paper call sites read the real chunk climate (paper {@code ChunkClimateSource}) and
     * use this only when no chunk data can be obtained.
     */
    public static final ChunkClimate NEUTRAL = new ChunkClimate(20f, 250f, 0f, 0f);
}
