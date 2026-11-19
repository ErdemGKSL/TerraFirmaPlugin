/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/BiomeBasedClimateModel.java @ c3b4d9536
 */
package dev.tfp.core.climate.model;

import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.clock.ClockSnapshot;

/**
 * TFC's biome-based climate model (PAR-CLIM-11), used for every level that is not an overworld with our generator: the Nether,
 * the End and vanilla-generated worlds. Pure; the vanilla biome inputs are captured on the owning region thread.
 * <p>
 * The average temperature is the vanilla biome temperature at the position mapped to degrees Celsius; the rainfall is a flat
 * {@value #WET_RAINFALL} mm when the biome has precipitation, else 0. As in TFC's {@code ClimateModel} defaults the instant
 * values equal the averages (no seasonal or daily variation, no rain events).
 */
public final class BiomeBasedClimate {
    /** Average rainfall, mm/year, of a biome that has precipitation. */
    public static final float WET_RAINFALL = 300f;

    private BiomeBasedClimate() {
    }

    /** TFC {@code Climate.fromVanilla}: 0 C is 0.15, -30 C is -0.51, +30 C is 0.801. */
    public static float fromVanilla(float vanillaTemperature) {
        return (vanillaTemperature - 0.15f) / 0.0217f;
    }

    /** @return The annual average (and instant) temperature, Celsius, for a vanilla biome temperature at the position. */
    public static float averageTemperature(float vanillaTemperature) {
        return fromVanilla(vanillaTemperature);
    }

    /** @return The annual average (and instant) rainfall, mm/year. */
    public static float averageRainfall(boolean hasPrecipitation) {
        return hasPrecipitation ? WET_RAINFALL : 0f;
    }

    /** The reading for the published {@code clock}; unknown while the clock has not published. */
    public static ClimateReading reading(float vanillaTemperature, boolean hasPrecipitation, ClockSnapshot clock) {
        if (!clock.isSet()) {
            return ClimateReading.unknown(0, 0);
        }
        float t = averageTemperature(vanillaTemperature);
        return new ClimateReading(clock.sequence(), clock.calendarTicks(), t, t, averageRainfall(hasPrecipitation));
    }
}
