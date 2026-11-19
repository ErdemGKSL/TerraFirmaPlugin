/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tooltip/BlockEntityTooltips.java @ c3b4d9536 (the four instrument tooltips).
 */
package dev.tfp.paper.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.instrument.Instruments;
import dev.tfp.core.instrument.Instruments.ClockMode;
import dev.tfp.core.instrument.WindField;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.instrument.InstrumentReadout.Capture;
import dev.tfp.paper.instrument.InstrumentReadout.Kind;
import dev.tfp.paper.instrument.InstrumentReadout.Result;
import org.junit.jupiter.api.Test;

class InstrumentReadoutTest {
    private static final long SEED = 4242L;
    private static final ClockSnapshot CLOCK = new ClockSnapshot(1, 0L, 40L * 24_000 + 13_500L, 8, true);

    private static Capture capture(boolean overworld, boolean attached, float device, ClockMode mode,
            InstrumentSources.Timer timer) {
        return new Capture(SEED, overworld, new BlockPos(100, 80, -300), false, true, CLOCK, 5000, attached, device, mode, timer);
    }

    @Test
    void otherDimensionsHaveNoReading() {
        Result r = InstrumentReadout.compute(capture(false, false, Float.NaN, ClockMode.HOUR, null), Kind.THERMOMETER);
        assertThat(r.readout().key()).isEqualTo("instrument.unknown");
        assertThat(r.power()).isZero();
        assertThat(InstrumentReadout.compute(capture(false, false, Float.NaN, ClockMode.HOUR, null), Kind.VANE).power())
            .isEqualTo(-1);
    }

    @Test
    void unsetClockHasNoReading() {
        Capture c = new Capture(SEED, true, new BlockPos(0, 70, 0), false, true, ClockSnapshot.UNSET, 5000, false, Float.NaN,
            ClockMode.HOUR, null);
        assertThat(InstrumentReadout.compute(c, Kind.ANEMOMETER).readout().key()).isEqualTo("instrument.unknown");
    }

    @Test
    void attachedThermometerShowsTheDeviceHeat() {
        Result hot = InstrumentReadout.compute(capture(true, true, 1600f, ClockMode.HOUR, null), Kind.THERMOMETER);
        assertThat(hot.readout().key()).isEqualTo("instrument.thermometer.device");
        assertThat(hot.power()).isEqualTo(15);
        Result none = InstrumentReadout.compute(capture(true, true, Float.NaN, ClockMode.HOUR, null), Kind.THERMOMETER);
        assertThat(none.power()).isZero();
    }

    @Test
    void ambientThermometerFollowsTheClimate() {
        Capture c = capture(true, false, Float.NaN, ClockMode.HOUR, null);
        OverworldClimate climate = OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate chunk = ChunkClimateSource.climateAt(SEED, 100, -300);
        float t = climate.instantTemperature(c.pos(), chunk, CLOCK.calendarTicks(), 8);
        Result r = InstrumentReadout.compute(c, Kind.THERMOMETER);
        assertThat(r.readout().key()).isEqualTo("instrument.thermometer.ambient");
        assertThat(r.power()).isEqualTo(Instruments.thermometerAmbientPower(t)).isBetween(0, 15);
    }

    @Test
    void windInstrumentsReadTheSameWind() {
        Capture c = capture(true, false, Float.NaN, ClockMode.HOUR, null);
        OverworldClimate climate = OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate chunk = ChunkClimateSource.climateAt(SEED, 100, -300);
        WindField.Wind wind = InstrumentReadout.wind(c, climate, chunk);
        Result anemometer = InstrumentReadout.compute(c, Kind.ANEMOMETER);
        assertThat(anemometer.readout().args()).containsExactly(String.format("%.0f", wind.kmh()));
        Result vane = InstrumentReadout.compute(c, Kind.VANE);
        int point = Instruments.cardinal16(Instruments.wrappedPositiveAngle(wind.angle()));
        assertThat(vane.readout().args()).containsExactly("instrument.vane.point." + point);
    }

    @Test
    void clockNamesItsModeAndTheDate() {
        Result r = InstrumentReadout.compute(capture(true, false, Float.NaN, ClockMode.MONTH, null), Kind.CALENDAR_CLOCK);
        assertThat(r.readout().key()).isEqualTo("instrument.clock.month");
        assertThat(r.readout().args()).hasSize(1);
        assertThat(r.readout().args().get(0)).endsWith("13:30");
        assertThat(InstrumentReadout.compute(capture(true, false, Float.NaN, ClockMode.TIMER, null), Kind.CALENDAR_CLOCK)
            .readout().key()).isEqualTo("instrument.clock.timer");
    }

    @Test
    void clockSignalByMode() {
        assertThat(InstrumentReadout.clockSignal(capture(true, false, Float.NaN, ClockMode.HOUR, null))).isEqualTo(1);
        assertThat(InstrumentReadout.clockSignal(capture(true, false, Float.NaN, ClockMode.MONTH, null)))
            .isEqualTo(CalendarTicks.month(CLOCK.calendarTicks(), 8).ordinal());
        assertThat(InstrumentReadout.clockSignal(capture(true, false, Float.NaN, ClockMode.TIMER,
            new InstrumentSources.Timer(100, 40)))).isEqualTo(6);
        assertThat(InstrumentReadout.clockSignal(capture(true, false, Float.NaN, ClockMode.TIMER, null))).isZero();
    }

    @Test
    void modeCycle() {
        assertThat(InstrumentReadout.nextMode(ClockMode.HOUR, true)).isEqualTo(ClockMode.MONTH);
        assertThat(InstrumentReadout.nextMode(ClockMode.MONTH, true)).isEqualTo(ClockMode.TIMER);
        assertThat(InstrumentReadout.nextMode(ClockMode.MONTH, false)).isEqualTo(ClockMode.HOUR);
        assertThat(InstrumentReadout.nextMode(ClockMode.TIMER, true)).isEqualTo(ClockMode.HOUR);
    }
}
