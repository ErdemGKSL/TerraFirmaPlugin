/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/IPlayerInfo.java @ c3b4d9536 (getHealthModifier)
 */
package dev.tfp.core.food;

import java.util.Arrays;

/**
 * A player's derived nutrition at one moment: the five nutrient values in {@code [0, 1]} (TFC
 * {@code NutritionData.getNutrients}) and their average ({@code getAverageNutrition}). Immutable.
 */
public final class NutritionLevels {
    /** TFC {@code IPlayerInfo.DEFAULT_AVERAGE_NUTRITION}: the average a new player starts with. */
    public static final float DEFAULT_AVERAGE_NUTRITION = 0.4f;

    private final float[] values;
    private final float average;

    NutritionLevels(float[] values) {
        this.values = values.clone();
        float avg = 0;
        for (float v : this.values) {
            avg += v;
        }
        this.average = avg / Nutrient.TOTAL;
    }

    public float get(Nutrient nutrient) {
        return values[nutrient.ordinal()];
    }

    /** A copy of the values, indexed by {@link Nutrient#ordinal()}. */
    public float[] values() {
        return values.clone();
    }

    public float average() {
        return average;
    }

    /** {@link #healthModifier(float, NutritionConfig)} of this average. */
    public float healthModifier(NutritionConfig config) {
        return healthModifier(average, config);
    }

    /**
     * TFC {@code IPlayerInfo.getHealthModifier}: the effect of nutrition. Below the default average 0.4 the
     * modifier maps linearly from {@code [0, 0.4]} to {@code [min, default]} (unclamped); above it maps from
     * {@code [0.4, 0.95]} to {@code [default, max]} and is clamped, so 0.95 and above give the maximum. TFC
     * divides incoming damage by it and multiplies displayed health by it (20 HP x 0.85 = 17 shown at start).
     */
    public static float healthModifier(float average, NutritionConfig config) {
        return average < DEFAULT_AVERAGE_NUTRITION
                ? map(average, 0.0f, DEFAULT_AVERAGE_NUTRITION, config.minHealthModifier(), config.defaultHealthModifier())
                : clampedMap(average, DEFAULT_AVERAGE_NUTRITION, 0.95f, config.defaultHealthModifier(), config.maxHealthModifier());
    }

    // Vanilla Mth float helpers, operation for operation.
    private static float inverseLerp(float delta, float start, float end) {
        return (delta - start) / (end - start);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float map(float input, float inMin, float inMax, float outMin, float outMax) {
        return lerp(inverseLerp(input, inMin, inMax), outMin, outMax);
    }

    private static float clampedMap(float input, float inMin, float inMax, float outMin, float outMax) {
        float delta = inverseLerp(input, inMin, inMax);
        return delta < 0 ? outMin : delta > 1 ? outMax : lerp(delta, outMin, outMax);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NutritionLevels other && Arrays.equals(values, other.values) && average == other.average;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "NutritionLevels" + Arrays.toString(values) + " avg " + average;
    }
}
