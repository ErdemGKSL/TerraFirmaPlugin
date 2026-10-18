/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (getInstantHydration,
 *       getInstantHydrationFromRainHydration, getInstantRainHydration, getHydrationMultiplier, isSourceBlockPresent,
 *       findMinCostWater),
 *       src/main/java/net/dries007/tfc/common/blockentities/IFarmland.java @ c3b4d9536 (WATER_DISSIPATION_RATE, updateAdditionalWater),
 *       src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (MAX_ADDITIONAL_WATER,
 *       waterTick, setAdditionalWaterWithoutSync)
 */
package dev.tfp.core.farm;

/**
 * Hydration and the water reserve of a farmland block. Pure: the caller reads the rainfall (at the calendar tick it asks for),
 * the hydrating water around the block and the soil block below, and passes them in.
 *
 * <p>Nothing here ticks. {@link #moisture} is a function of the inputs at one instant, so asking for it at any time
 * is a single evaluation. The water reserve catches up with {@link #additionalWater}, over the calendar ticks since the last
 * water update, in one step. The reserve is clamped to {@code [0, 15]}.
 *
 * <p><b>Note:</b> at c3b4d9536 the reserve is accumulated but no hydration formula reads it: {@code getInstantHydration}
 * uses only the rain, the source-block boost and the soil multiplier. The reserve is ported as stored state and is not an
 * input to {@link #moisture}. See the ledger divergence row for the farmland.
 */
public final class FarmlandRules {
    /** TFC {@code IFarmland.WATER_DISSIPATION_RATE}: reserve gained per calendar tick (despite the name). */
    public static final float WATER_DISSIPATION_RATE = 0.001f;
    /** TFC {@code FarmlandBlockEntity.MAX_ADDITIONAL_WATER}. */
    public static final float MAX_ADDITIONAL_WATER = 15.0f;
    /** TFC {@code ClimateModel.MIN_AVERAGE_RAINFALL}. */
    public static final float MIN_AVERAGE_RAINFALL = 0f;
    /** TFC {@code ClimateModel.MAX_CROP_RAINFALL}: the rainfall that gives the full rain hydration. */
    public static final float MAX_CROP_RAINFALL = 600f;
    /** TFC {@code ChunkData.MAX_RAINFALL_CONTRIBUTION}: the most hydration rain can give. */
    public static final float MAX_RAIN_HYDRATION = 60.0f;
    /** TFC {@code FarmlandBlock.getInstantHydrationFromRainHydration}: the boost of a source block within reach. */
    public static final int SOURCE_BOOST = 40;
    /** The most hydration a farmland reports. */
    public static final int MAX_HYDRATION = 100;
    /** TFC {@code findMinCostWater}: the search radius in the x and z axes. */
    public static final int SOURCE_RADIUS = 4;
    /** TFC {@code findMinCostWater}: the cost when no hydrating fluid is in reach; a source needs a cost below this. */
    public static final int NO_SOURCE_COST = 5;

    /** TFC {@code FarmlandBlock.getHydrationMultiplier}: the soil block under the farmland. */
    public enum SoilHydration {
        /** {@code INCREASES_SOIL_HYDRATION}. */
        INCREASES(2f),
        /** No tag: the default. */
        NORMAL(1f),
        /** {@code DECREASES_SOIL_HYDRATION}. */
        DECREASES(0.5f);

        private final float multiplier;

        SoilHydration(float multiplier) {
            this.multiplier = multiplier;
        }

        /** The multiplier on the water and rain hydration. */
        public float multiplier() {
            return multiplier;
        }
    }

    /** Whether the hydrating fluid sits at the given offset from the farmland's position. */
    @FunctionalInterface
    public interface WaterProbe {
        boolean hydrating(int dx, int dy, int dz);
    }

    private FarmlandRules() {
    }

    /**
     * TFC {@code getInstantRainHydration(float)}: rainfall mapped from {@code [0, 600]} to {@code [0, 60]}, truncated.
     * The rainfall is the climate's instant rainfall at the calendar tick being asked for.
     */
    public static int rainHydration(float rainfall) {
        return (int) clampedMap(rainfall, MIN_AVERAGE_RAINFALL, MAX_CROP_RAINFALL, 0f, MAX_RAIN_HYDRATION);
    }

    /**
     * TFC {@code getInstantHydrationFromRainHydration}: {@code 100} when the crop is waterlogged, otherwise the source
     * boost plus the rain hydration, times the soil multiplier, truncated and clamped to {@code [0, 100]}.
     */
    public static int instantHydration(int rainHydration, boolean waterlogged, boolean sourcePresent, float soilMultiplier) {
        if (waterlogged) {
            return MAX_HYDRATION;
        }
        int waterBoost = sourcePresent ? SOURCE_BOOST : 0;
        return clampInt((int) ((waterBoost + rainHydration) * soilMultiplier), 0, MAX_HYDRATION);
    }

    /**
     * The moisture of a farmland at one instant, from the rainfall at that instant. A function of its inputs only, so it is
     * evaluated lazily on read and never stored.
     */
    public static int moisture(float rainfall, boolean waterlogged, boolean sourcePresent, SoilHydration soil) {
        return instantHydration(rainHydration(rainfall), waterlogged, sourcePresent, soil.multiplier());
    }

    /**
     * TFC {@code findMinCostWater}: the cheapest hydrating fluid within reach. The cost of an offset is
     * {@code max(|dx|, |dz|) + (-2 * dy)} over {@code dx, dz} in {@code [-4, 4]} and {@code dy} in {@code {-1, 0}}. A hit at cost
     * 1 returns early, as TFC does. The result is in {@code [1, 5]}; {@code 5} means no source.
     */
    public static int minCostWater(WaterProbe probe) {
        int minCostWater = NO_SOURCE_COST;
        for (int dx = -SOURCE_RADIUS; dx <= SOURCE_RADIUS; dx++) {
            for (int dz = -SOURCE_RADIUS; dz <= SOURCE_RADIUS; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    int cost = Math.max(Math.abs(dx), Math.abs(dz)) + (-2 * dy);
                    if (cost < minCostWater && probe.hydrating(dx, dy, dz)) {
                        minCostWater = cost;
                        if (minCostWater == 1) {
                            return 1;
                        }
                    }
                }
            }
        }
        return minCostWater;
    }

    /** TFC {@code isSourceBlockPresent}: a source is in reach when the minimum cost is below 5. */
    public static boolean sourcePresent(int minCostWater) {
        return minCostWater < NO_SOURCE_COST;
    }

    /**
     * TFC {@code updateAdditionalWater} followed by {@code setAdditionalWater}: the reserve grows by
     * {@code (to - from) * 0.001} and is clamped to {@code [0, 15]}. The arithmetic is float32, as TFC's.
     *
     * @param previous the reserve at the last water update
     * @param fromCalendarTick the calendar tick at the last water update
     * @param toCalendarTick the calendar tick now
     */
    public static float additionalWater(float previous, long fromCalendarTick, long toCalendarTick) {
        long deltaTicks = toCalendarTick - fromCalendarTick;
        float grown = previous + deltaTicks * WATER_DISSIPATION_RATE;
        return clampFloat(grown, 0f, MAX_ADDITIONAL_WATER);
    }

    static float clampFloat(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    static int clampInt(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code Mth.clampedMap(float, float, float, float, float)}: {@code inverseLerp}, clamped to {@code [0, 1]}, then lerped. */
    static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        float delta = (value - inMin) / (inMax - inMin);
        if (delta < 0f) {
            return outMin;
        }
        if (delta > 1f) {
            return outMax;
        }
        return outMin + delta * (outMax - outMin);
    }
}
