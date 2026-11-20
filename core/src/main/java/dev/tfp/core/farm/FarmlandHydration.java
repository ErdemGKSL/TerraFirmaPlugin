/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (addHoeOverlayInfo),
 *       src/main/java/net/dries007/tfc/world/chunkdata/ChunkData.java @ c3b4d9536 (getMinRainfallHydration, getMaxRainfallHydration),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (getInstantHydrationFromRainHydration)
 */
package dev.tfp.core.farm;

/**
 * The hydration numbers of the hoe overlay (PAR-FARM-01): the value now, and the value at the driest and the wettest rain of
 * the year. Pure; the water search ({@link FarmlandRules#minCostWater}) and the rainfall are inputs.
 *
 * @param now the hydration now, 0 to 100
 * @param min the hydration at the lowest rainfall of the year
 * @param max the hydration at the highest rainfall of the year
 */
public record FarmlandHydration(int now, int min, int max) {
    /** TFC {@code ChunkData.getMinRainfallHydration}: {@code rainfall * (1 - |variance|) * (60 / 600)}. */
    public static float minRainHydration(float averageRainfall, float rainfallVariance) {
        return averageRainfall * (1 - Math.abs(rainfallVariance)) * (FarmlandRules.MAX_RAIN_HYDRATION / FarmlandRules.MAX_CROP_RAINFALL);
    }

    /** TFC {@code ChunkData.getMaxRainfallHydration}: as the min with {@code 1 + |variance|}, capped at 60. */
    public static float maxRainHydration(float averageRainfall, float rainfallVariance) {
        return Math.min(averageRainfall * (1 + Math.abs(rainfallVariance))
            * (FarmlandRules.MAX_RAIN_HYDRATION / FarmlandRules.MAX_CROP_RAINFALL), FarmlandRules.MAX_RAIN_HYDRATION);
    }

    /**
     * The overlay numbers: {@code instantRainfall} gives the value now; the min and max use the truncated min and max rain
     * hydration of the chunk climate. All three share the waterlogged, source and soil inputs.
     */
    public static FarmlandHydration of(float instantRainfall, float averageRainfall, float rainfallVariance, boolean waterlogged,
            int minCostWater, FarmlandRules.SoilHydration soil) {
        boolean source = FarmlandRules.sourcePresent(minCostWater);
        int now = FarmlandRules.instantHydration(FarmlandRules.rainHydration(instantRainfall), waterlogged, source, soil.multiplier());
        int min = FarmlandRules.instantHydration((int) minRainHydration(averageRainfall, rainfallVariance), waterlogged, source,
            soil.multiplier());
        int max = FarmlandRules.instantHydration((int) maxRainHydration(averageRainfall, rainfallVariance), waterlogged, source,
            soil.multiplier());
        return new FarmlandHydration(now, min, max);
    }
}
