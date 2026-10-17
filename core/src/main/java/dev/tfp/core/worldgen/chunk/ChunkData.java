/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/ChunkData.java @ c3b4d9536 (the climate getters and the
 * PARTIAL layers; the forest type, the surface heights and the rock cache are not here, see the ledger)
 */
package dev.tfp.core.worldgen.chunk;

import dev.tfp.core.climate.ChunkClimate;

/**
 * The climate inputs of one chunk, as TFC stores them in its chunk data: the bilinear layers of sea-level temperature,
 * average rainfall, rainfall variance and base groundwater, each scaled from the region map to the 16 x 16 chunk. The
 * getters take block coordinates; the chunk-local position is {@code x & 15}, as in TFC. Immutable and recomputed from the
 * seed by {@link ChunkDataGenerator}, so it is never persisted.
 *
 * @param chunkX             the chunk coordinate ({@code blockX >> 4})
 * @param chunkZ             the chunk coordinate ({@code blockZ >> 4})
 * @param rainfall           the average annual rainfall layer, mm, in [0, 500]
 * @param rainVariance       the seasonal rainfall variance layer, in [-1, 1]
 * @param baseGroundwater    the groundwater added by nearby rivers, mm, in [0, 500]
 * @param temperature        the average sea-level temperature layer, degrees Celsius
 */
public record ChunkData(
    int chunkX,
    int chunkZ,
    LerpFloatLayer rainfall,
    LerpFloatLayer rainVariance,
    LerpFloatLayer baseGroundwater,
    LerpFloatLayer temperature
) {
    /** The cap of total groundwater, mm, as in TFC's {@code getAverageGroundwater}. */
    public static final float MAX_GROUNDWATER = 500f;

    /**
     * The climate input of a block position: the chunk's values, or {@link ChunkClimate#NEUTRAL} when {@code data} is null
     * (the chunk has no data yet, the fallback until the world's chunk source is installed).
     */
    public static ChunkClimate climateOrNeutral(ChunkData data, int x, int z) {
        return data == null ? ChunkClimate.NEUTRAL : data.climateAt(x, z);
    }

    /** @return The annual average sea-level temperature at the block position, degrees Celsius. */
    public float averageSeaLevelTemperature(int x, int z) {
        return temperature.getValue((x & 15) / 16f, (z & 15) / 16f);
    }

    /** @return The time-invariant rainfall at the block position, mm. */
    public float averageRainfall(int x, int z) {
        return rainfall.getValue((x & 15) / 16f, (z & 15) / 16f);
    }

    /** @return The seasonal rainfall variance at the block position, in [-1, 1]. */
    public float rainVariance(int x, int z) {
        return rainVariance.getValue((x & 15) / 16f, (z & 15) / 16f);
    }

    /** @return The groundwater from nearby rivers at the block position, mm. */
    public float baseGroundwater(int x, int z) {
        return baseGroundwater.getValue((x & 15) / 16f, (z & 15) / 16f);
    }

    /** @return The time-invariant total groundwater (rivers and rainfall) at the block position, mm, capped at 500. */
    public float averageGroundwater(int x, int z) {
        return Math.min(baseGroundwater(x, z) + averageRainfall(x, z), MAX_GROUNDWATER);
    }

    /** @return The climate input of the block position, as the climate model reads it. */
    public ChunkClimate climateAt(int x, int z) {
        return new ChunkClimate(averageSeaLevelTemperature(x, z), averageRainfall(x, z), rainVariance(x, z), baseGroundwater(x, z));
    }
}
