/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tooltip/BlockEntityTooltips.java @ c3b4d9536 (THERMOMETER, ANEMOMETER, VANE,
 *       CALENDAR_CLOCK: what the Jade tooltip shows), src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java
 *       @ c3b4d9536 (getWind and the rain test it makes), src/main/java/net/dries007/tfc/common/blocks/devices/CalendarClockBlock.java
 *       @ c3b4d9536 (useWithoutItem: the mode cycle)
 */
package dev.tfp.paper.instrument;

import dev.tfp.core.calendar.CalendarFormat;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.instrument.Instruments;
import dev.tfp.core.instrument.Instruments.ClockMode;
import dev.tfp.core.instrument.WindField;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.List;
import java.util.Set;

/**
 * The readout of the four instruments (PAR-CLIM-09), as pure functions of a {@link Capture} that the region thread took.
 * TFC shows the same lines as a Jade block tooltip; a vanilla client has no tooltip for a block, so the readout goes to the
 * action bar of the player who right-clicked (ledger divergence).
 */
public final class InstrumentReadout {
    private InstrumentReadout() {}

    /** The four instruments. */
    public enum Kind { THERMOMETER, ANEMOMETER, VANE, CALENDAR_CLOCK }

    /** TFC {@code #tfc:is_ocean} without the namespace: the biomes whose wind is predictable ({@code HAS_PREDICTABLE_WINDS}). */
    public static final Set<String> OCEAN_BIOMES = Set.of("deep_ocean", "deep_ocean_atolls", "deep_ocean_trench", "ocean",
        "ocean_atolls", "ocean_reef", "ocean_ridge");

    /**
     * Everything the computation reads, taken on the instrument's region thread.
     *
     * @param worldSeed         the world's seed
     * @param overworld         whether the world has TFC's overworld climate
     * @param pos               the instrument's block
     * @param ocean             whether the biome has predictable winds
     * @param daytime           whether {@code dayTime % 24000 < 12000}
     * @param clock             the published clock
     * @param windScale         TFC {@code oceanWindScale}
     * @param attached          the thermometer's {@code attached} state
     * @param deviceTemperature the heat of the device behind an attached thermometer, or NaN when none answers
     * @param clockMode         the clock's mode state
     * @param timer             the recipe timer behind the clock, or null
     */
    public record Capture(long worldSeed, boolean overworld, BlockPos pos, boolean ocean, boolean daytime, ClockSnapshot clock,
            int windScale, boolean attached, float deviceTemperature, ClockMode clockMode, InstrumentSources.Timer timer) {
    }

    /** A message key and its already formatted arguments. */
    public record Readout(String key, List<String> args) {
        public static Readout of(String key, String... args) {
            return new Readout(key, List.of(args));
        }
    }

    /**
     * What an instrument shows, and the block-state numbers of its visual ({@code -1}: this instrument has none, or no reading):
     * the thermometer's {@code power}, the vane arrow's {@code wind} step and the clock's {@code dial} (PAR-CLIM-28: the moving
     * parts rest at the reading taken on a click, a placement or a neighbour change; nothing ticks).
     */
    public record Result(Readout readout, int power, int wind, int dial) {
        public Result(Readout readout, int power) {
            this(readout, power, -1, -1);
        }
    }

    /** The result for {@code kind}. Pure; any thread. */
    public static Result compute(Capture c, Kind kind) {
        if (kind == Kind.CALENDAR_CLOCK) {
            final int dial = !c.clock().isSet() ? -1 : c.clockMode() == ClockMode.MONTH
                ? Instruments.clockMonthDial(c.clock().calendarTicks(), c.clock().daysInMonth())
                : Instruments.clockDial(c.clock().calendarTicks(), c.clock().daysInMonth());
            return new Result(clock(c), -1, -1, dial);
        }
        if (kind == Kind.THERMOMETER && c.attached()) {
            final float t = Float.isNaN(c.deviceTemperature()) ? 0f : c.deviceTemperature();
            return new Result(Readout.of("instrument.thermometer.device", ClimateFormat.temperature(t)),
                Instruments.thermometerDevicePower(t));
        }
        if (!c.overworld() || !c.clock().isSet()) {
            return new Result(Readout.of("instrument.unknown"), kind == Kind.THERMOMETER ? 0 : -1);
        }
        final OverworldClimate climate = WorldSettingsLookup.climate(c.worldSeed());
        final ChunkClimate chunk = ChunkClimateSource.climateAt(c.worldSeed(), c.pos().x(), c.pos().z());
        final long ticks = c.clock().calendarTicks();
        final int dim = c.clock().daysInMonth();
        switch (kind) {
            case THERMOMETER -> {
                final float t = climate.instantTemperature(c.pos(), chunk, ticks, dim);
                return new Result(Readout.of("instrument.thermometer.ambient", ClimateFormat.temperature(t)),
                    Instruments.thermometerAmbientPower(t));
            }
            case ANEMOMETER -> {
                final WindField.Wind wind = wind(c, climate, chunk);
                return new Result(Readout.of("instrument.anemometer.speed", String.format("%.0f", wind.kmh())), -1);
            }
            default -> {
                final WindField.Wind wind = wind(c, climate, chunk);
                final int point = Instruments.cardinal16(Instruments.wrappedPositiveAngle(wind.angle()));
                return new Result(Readout.of("instrument.vane.direction", "instrument.vane.point." + point), -1,
                    Instruments.vaneStep(Instruments.vaneAngle(wind)), -1);
            }
        }
    }

    /** TFC {@code getWind} at the capture's block. */
    static WindField.Wind wind(Capture c, OverworldClimate climate, ChunkClimate chunk) {
        final long ticks = c.clock().calendarTicks();
        final boolean raining = precipitating(climate, chunk, ticks, c.clock().daysInMonth());
        return WindField.at(climate.climateSeed(), CalendarTicks.totalDays(ticks), c.pos().y(), c.pos().z(), raining, c.ocean(),
            c.daytime(), c.windScale());
    }

    /** TFC {@code WeatherHelpers.isPrecipitating(getRain(ticks), getInstantRainfall(...))}: the real intensity is above 0. */
    static boolean precipitating(OverworldClimate climate, ChunkClimate chunk, long ticks, int daysInMonth) {
        final float rain = climate.rainIntensity(ticks);
        final float rainfall = climate.instantRainfall(chunk, ticks, daysInMonth);
        return rain - Mth.clampedMap(rainfall, OverworldClimate.MIN_AVERAGE_RAINFALL, OverworldClimate.MAX_AVERAGE_RAINFALL, 1f, 0f) > 0;
    }

    static Readout clock(Capture c) {
        final String mode = switch (c.clockMode()) {
            case HOUR -> "instrument.clock.hour";
            case MONTH -> "instrument.clock.month";
            case TIMER -> "instrument.clock.timer";
        };
        final String when = c.clock().isSet() ? CalendarFormat.dateTime(c.clock().calendarTicks(), c.clock().daysInMonth())
            : CalendarFormat.UNKNOWN;
        return Readout.of(mode, when);
    }

    /** The redstone signal a clock gives in its mode (what TFC's comparator reads), for the ledger and the tests. */
    public static int clockSignal(Capture c) {
        final int timer = c.timer() == null ? 0 : Instruments.clockTimerSignal(c.timer().durationTicks(), c.timer().remainingTicks());
        return Instruments.clockSignal(c.clockMode(), c.clock().calendarTicks(), c.clock().daysInMonth(), timer);
    }

    /**
     * TFC {@code CalendarClockBlock.useWithoutItem}: hour goes to month; month goes to timer when a timer device is behind
     * the clock, else back to hour; timer goes to hour.
     */
    public static ClockMode nextMode(ClockMode mode, boolean timerAvailable) {
        return switch (mode) {
            case MONTH -> timerAvailable ? ClockMode.TIMER : ClockMode.HOUR;
            case TIMER -> ClockMode.HOUR;
            default -> ClockMode.MONTH;
        };
    }
}
