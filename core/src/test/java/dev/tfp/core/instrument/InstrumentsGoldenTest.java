/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ThermometerBlockEntity.java @ c3b4d9536 (and the other three instrument
 * block entities, WeatherHelpers, OverworldClimateModel.getWind). Golden values are derived by hand from the formulas
 * named in each test (TFC has no data file for them).
 */
package dev.tfp.core.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.instrument.Instruments.ClockMode;
import org.junit.jupiter.api.Test;

class InstrumentsGoldenTest {
    private static final float EPS = 1e-5f;

    /** ThermometerBlockEntity.updatePower: floor(clampedMap(t, -40, 40, 0, 15)). */
    @Test
    void ambientPower() {
        assertThat(Instruments.thermometerAmbientPower(-40f)).isZero();
        assertThat(Instruments.thermometerAmbientPower(-100f)).isZero();
        assertThat(Instruments.thermometerAmbientPower(-1f)).isEqualTo(7); // 39/80 * 15 = 7.31
        assertThat(Instruments.thermometerAmbientPower(0f)).isEqualTo(7); // 7.5
        assertThat(Instruments.thermometerAmbientPower(1f)).isEqualTo(7); // 7.69
        assertThat(Instruments.thermometerAmbientPower(20f)).isEqualTo(11); // 11.25
        assertThat(Instruments.thermometerAmbientPower(39.9f)).isEqualTo(14);
        assertThat(Instruments.thermometerAmbientPower(40f)).isEqualTo(15);
        assertThat(Instruments.thermometerAmbientPower(300f)).isEqualTo(15);
    }

    /** The attached branch: clampedMap(t, 0, Heat.maxVisibleTemperature() = 1600, 0, 15). */
    @Test
    void devicePower() {
        assertThat(Instruments.thermometerDevicePower(0f)).isZero();
        assertThat(Instruments.thermometerDevicePower(106f)).isZero(); // 0.99
        assertThat(Instruments.thermometerDevicePower(107f)).isEqualTo(1);
        assertThat(Instruments.thermometerDevicePower(800f)).isEqualTo(7);
        assertThat(Instruments.thermometerDevicePower(1600f)).isEqualTo(15);
        assertThat(Instruments.thermometerDevicePower(2500f)).isEqualTo(15);
    }

    /** AnemometerBlockEntity: windToVisualSpeed and getRedstoneSignal. */
    @Test
    void anemometer() {
        assertThat(Instruments.anemometerVisualSpeed(0f)).isZero();
        assertThat(Instruments.anemometerVisualSpeed(0.25f)).isCloseTo(0.5f, within(EPS));
        assertThat(Instruments.anemometerVisualSpeed(0.5f)).isEqualTo(1f);
        assertThat(Instruments.anemometerVisualSpeed(0.9f)).isEqualTo(1f);
        assertThat(Instruments.anemometerSignal(0.0624f)).isZero();
        assertThat(Instruments.anemometerSignal(0.0625f)).isEqualTo(1);
        assertThat(Instruments.anemometerSignal(0.5f)).isEqualTo(8);
        assertThat(Instruments.anemometerSignal(0.99f)).isEqualTo(15);
        assertThat(Instruments.anemometerSignal(1f)).isEqualTo(15); // floor(16) clamped
    }

    /** WeatherHelpers.granularCardinalIntFromAngle: sixteen points 22.5 degrees apart, 11.25 either side. */
    @Test
    void cardinalPoints() {
        for (int i = 0; i < 16; i++) {
            assertThat(Instruments.cardinal16((float) Math.toRadians(22.5 * i))).as("point %d", i).isEqualTo(i);
        }
        assertThat(Instruments.cardinal16((float) Math.toRadians(10))).isZero();
        assertThat(Instruments.cardinal16((float) Math.toRadians(350))).isZero();
        assertThat(Instruments.cardinal16((float) Math.toRadians(340))).isEqualTo(15);
        assertThat(Instruments.cardinal16(0f)).isZero();
    }

    /** wrappedPositiveAngle: negative angles gain 2 pi, then a quarter turn so that north is 0. */
    @Test
    void wrappedAngle() {
        final float pi = (float) Math.PI;
        assertThat(Instruments.wrappedPositiveAngle(0f)).isCloseTo(pi / 2, within(EPS));
        assertThat(Instruments.wrappedPositiveAngle(pi / 2)).isCloseTo(pi, within(EPS));
        assertThat(Instruments.wrappedPositiveAngle(-pi / 2)).isCloseTo(2 * pi, within(EPS));
        // 3 pi / 2 + pi / 2 = 2 pi + 0.0 wraps only above 2 pi
        assertThat(Instruments.wrappedPositiveAngle(pi)).isCloseTo(3 * pi / 2, within(EPS));
    }

    /** VaneBlockEntity.getRedstoneSignal: granular(wrapped(angle)). Wind angle 0 is east (4), pi / 2 south (8). */
    @Test
    void vaneSignal() {
        final float pi = (float) Math.PI;
        assertThat(Instruments.vaneSignal(0f)).isEqualTo(4);
        assertThat(Instruments.vaneSignal(pi / 2)).isEqualTo(8);
        assertThat(Instruments.vaneSignal(pi)).isEqualTo(12);
        assertThat(Instruments.vaneSignal(-pi / 2)).isZero();
    }

    /** CalendarClockBlockEntity: hour % 12, the month index, ceil(remaining * 15 / duration). */
    @Test
    void clockSignals() {
        assertThat(Instruments.clockHourSignal(13_500L)).isEqualTo(1);
        assertThat(Instruments.clockHourSignal(23_999L)).isEqualTo(11);
        assertThat(Instruments.clockHourSignal(12_000L)).isZero();
        assertThat(Instruments.clockMonthSignal(0L, 8)).isZero();
        assertThat(Instruments.clockMonthSignal(3L * 8 * 24_000, 8)).isEqualTo(3);
        assertThat(Instruments.clockMonthSignal(11L * 8 * 24_000 + 5, 8)).isEqualTo(11);
        assertThat(Instruments.clockTimerSignal(100, 40)).isEqualTo(6);
        assertThat(Instruments.clockTimerSignal(100, 1)).isEqualTo(1);
        assertThat(Instruments.clockTimerSignal(100, 100)).isEqualTo(15);
        assertThat(Instruments.clockTimerSignal(0, 10)).isZero();
        assertThat(Instruments.clockTimerSignal(100, 0)).isZero();
        assertThat(Instruments.clockSignal(ClockMode.TIMER, 0L, 8, 9)).isEqualTo(9);
        assertThat(Instruments.clockSignal(ClockMode.HOUR, 13_500L, 8, 9)).isEqualTo(1);
    }

    /** clientTick: minute TWO_PI * fractionOfHour, hour TWO_PI * fractionOfDay * 2, month TWO_PI * fractionOfYear. */
    @Test
    void clockAngles() {
        final float twoPi = (float) (Math.PI * 2);
        assertThat(Instruments.clockMinuteAngle(13_500L)).isCloseTo(twoPi / 2, within(EPS));
        assertThat(Instruments.clockHourAngle(13_500L, 8)).isCloseTo(twoPi * 0.5625f * 2, within(EPS));
        assertThat(Instruments.clockHourAngle(12_000L, 8)).isCloseTo(twoPi, within(EPS)); // noon: the 12 hour dial is full
        assertThat(Instruments.clockMonthAngle(0L, 8)).isZero();
        assertThat(Instruments.clockMonthAngle(6L * 8 * 24_000, 8)).isCloseTo(twoPi / 2, within(EPS));
    }
}
