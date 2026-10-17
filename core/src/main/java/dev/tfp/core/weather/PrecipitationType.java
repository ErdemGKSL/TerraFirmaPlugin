/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536
 * Also: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java (isPrecipitating, calculateRealRainIntensity)
 * and the rain-or-snow choice in getPrecipitationAt. The sky-visible gate is vanilla's Level.isRainingAt, which TFC
 * keeps in front of its biome call.
 */
package dev.tfp.core.weather;

import dev.tfp.core.climate.OverworldClimate;

/**
 * What falls at one position, as TFC decides it (M2-WTH-01). This is the decision only: nothing is placed in the
 * world here (snow layers and ice are M2-WTH-02).
 *
 * <p>Pure and thread-safe. All arithmetic is {@code float}, as in TFC.
 */
public enum PrecipitationType {
    RAIN,
    SNOW,
    NONE;

    /**
     * TFC {@code WeatherHelpers.getPrecipitationAt} (with vanilla's sky gate in front of it): NONE when the position
     * cannot see the sky or nothing is precipitating; otherwise SNOW when the instant temperature is not above zero and
     * RAIN when it is.
     *
     * @param temperature   the instant temperature at the position, degrees Celsius
     * @param rainfall      the instant rainfall at the position, mm/year (TFC {@code getInstantRainfall})
     * @param rainIntensity the rain intensity at the time (TFC {@code getRain}), or {@code -1} when no rain covers it
     * @param skyVisible    whether the position can see the sky (vanilla {@code Level.canSeeSky})
     * @return the precipitation at the position, or {@link #NONE} when a value is NaN (unknown)
     */
    public static PrecipitationType decide(float temperature, float rainfall, float rainIntensity, boolean skyVisible) {
        if (!skyVisible || Float.isNaN(temperature) || Float.isNaN(rainfall) || Float.isNaN(rainIntensity)) {
            return NONE;
        }
        if (!isPrecipitating(rainIntensity, rainfall)) {
            return NONE;
        }
        return temperature > 0f ? RAIN : SNOW;
    }

    /** TFC {@code WeatherHelpers.isPrecipitating}: the real intensity is above zero. */
    static boolean isPrecipitating(float rainIntensity, float rainfall) {
        return realRainIntensity(rainIntensity, rainfall) > 0;
    }

    /** TFC {@code WeatherHelpers.calculateRealRainIntensity}: intensity minus the rainfall's dry-side term. */
    static float realRainIntensity(float rainIntensity, float rainfall) {
        return rainIntensity - clampedMap(rainfall, OverworldClimate.MIN_AVERAGE_RAINFALL, OverworldClimate.MAX_AVERAGE_RAINFALL, 1, 0);
    }

    /** {@code Mth.clampedMap}, in the same float arithmetic as {@code TfcMath.clampedMap} (package-private there). */
    private static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        final float delta = (value - inMin) / (inMax - inMin);
        if (delta < 0) {
            return outMin;
        }
        if (delta > 1) {
            return outMax;
        }
        return outMin + delta * (outMax - outMin);
    }
}
