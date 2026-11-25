/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536 (getAverageTemperature,
 *       getRainfallVariance: the chunk data at the block)
 */
package dev.tfp.paper.plant.lifecycle;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.worldgen.WorldSettingsLookup;

/**
 * PAR-WORLD-61: the {@link PlantStageRemap.ColumnClimate} of a TFC level from its seed: the chunk climate of the column
 * ({@link ChunkClimateSource}, pure and memoised) and TFC's average temperature with the elevation lapse rate. Any thread.
 */
public final class PlantStageClimate implements PlantStageRemap.ColumnClimate {
    private final long seed;
    private final OverworldClimate model;

    public PlantStageClimate(long seed, OverworldClimate model) {
        this.seed = seed;
        this.model = model;
    }

    /** The climate of the loaded TFC world with {@code seed}. */
    public static PlantStageClimate forSeed(long seed) {
        return new PlantStageClimate(seed, WorldSettingsLookup.climate(seed));
    }

    private ChunkClimate chunk(int x, int z) {
        return ChunkClimateSource.climateAt(seed, x, z);
    }

    @Override
    public float rainfallVariance(int x, int z) {
        return chunk(x, z).rainfallVariance();
    }

    @Override
    public float averageTemperature(int x, int y, int z) {
        return model.averageTemperature(new BlockPos(x, y, z), chunk(x, z));
    }
}
