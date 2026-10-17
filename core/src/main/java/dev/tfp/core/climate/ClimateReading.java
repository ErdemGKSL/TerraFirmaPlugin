/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.world.BlockPos;

/**
 * The climate at one position and calendar time (M2-CLIM-03): what {@code tfp_ambient_temp} shows and what
 * {@code /tfp debug chunk} prints. Pure: {@link #of} is the snapshot-building function, and it runs on any thread.
 *
 * @param clockSequence      the clock reading it was computed from ({@link ClockSnapshot#sequence()}; 0 if none)
 * @param calendarTicks      the calendar time it was computed at
 * @param temperature        the instant temperature at the position, degrees Celsius (NaN when unknown)
 * @param averageTemperature the annual average at the position, degrees Celsius (NaN when unknown)
 * @param rainfall           the instant rainfall, mm/year (NaN when unknown)
 */
public record ClimateReading(long clockSequence, long calendarTicks, float temperature, float averageTemperature,
        float rainfall) {
    /** A reading with no values, for a clock that has not published yet or a dimension without the model. */
    public static ClimateReading unknown(long clockSequence, long calendarTicks) {
        return new ClimateReading(clockSequence, calendarTicks, Float.NaN, Float.NaN, Float.NaN);
    }

    /**
     * The reading at {@code pos} for the published {@code clock}, with the chunk's climate inputs.
     *
     * @param climate the overworld climate of the position's world
     * @param chunk   the chunk's climate inputs ({@link ChunkClimate#NEUTRAL} until M2-GEN-05)
     */
    public static ClimateReading of(OverworldClimate climate, ChunkClimate chunk, BlockPos pos, ClockSnapshot clock) {
        if (!clock.isSet()) {
            return unknown(0, 0);
        }
        float temperature = climate.instantTemperature(pos, chunk, clock.calendarTicks(), clock.daysInMonth());
        float average = climate.averageTemperature(pos, chunk);
        float rainfall = climate.instantRainfall(chunk, clock.calendarTicks(), clock.daysInMonth());
        return new ClimateReading(clock.sequence(), clock.calendarTicks(), temperature, average, rainfall);
    }

    /** True if the temperature is known (the HUD and the debug command show a value, not a dash). */
    public boolean isKnown() {
        return !Float.isNaN(temperature);
    }
}
