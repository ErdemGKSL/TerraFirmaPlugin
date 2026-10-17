/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Ports of TFC's {@code SolarCalculatorTest} (src/test/java/net/dries007/tfc/test/util/SolarCalculatorTest.java @ c3b4d9536),
 * with the same assertions and the same temperate scale (20 000 blocks, TFC's default), plus golden day times that were
 * taken from TFC's {@code SolarCalculator.getSunBasedDayTime} compiled unchanged against Minecraft 1.21's {@code Mth}.
 */
class SolarGoldenTest {
    private static final float SCALE = OverworldClimate.DEFAULT_TEMPERATURE_SCALE;

    @Test
    void latitudeMatchesTfcTest() {
        assertThat(Solar.latitude(-50_000, SCALE)).isEqualTo(-TfcMath.HALF_PI); // South Pole
        assertThat(Solar.latitude(-40_000, SCALE)).isEqualTo(-TfcMath.PI / 4f); // South, Temperate
        assertThat(Solar.latitude(-30_000, SCALE)).isZero(); // Equator
        assertThat(Solar.latitude(-20_000, SCALE)).isEqualTo(TfcMath.PI / 4f); // North, Temperate
        assertThat(Solar.latitude(-10_000, SCALE)).isEqualTo(TfcMath.HALF_PI); // North Pole
        assertThat(Solar.latitude(0, SCALE)).isEqualTo(TfcMath.PI / 4f); // North, Temperate
        assertThat(Solar.latitude(10_000, SCALE)).isZero(); // Equator
        assertThat(Solar.latitude(20_000, SCALE)).isEqualTo(-TfcMath.PI / 4f); // South, Temperate
        assertThat(Solar.latitude(30_000, SCALE)).isEqualTo(-TfcMath.HALF_PI); // South Pole
        assertThat(Solar.latitude(40_000, SCALE)).isEqualTo(-TfcMath.PI / 4f); // South, Temperate
        assertThat(Solar.latitude(50_000, SCALE)).isZero(); // Equator
    }

    @Test
    void declinationAtPoleAtSolsticeIsConstant() {
        final float zenith = Solar.sunPosition(-10_000, SCALE, 0f, 0f).zenith();
        for (int h = 0; h < 24; h++) {
            assertThat(Solar.sunPosition(-10_000, SCALE, 0f, h / 24f).zenith()).isCloseTo(zenith, within(0.000001f));
        }
    }

    @Test
    void sunIsAlwaysVisibleAtNoonAtEquator() {
        for (int d = 0; d < 72; d++) {
            assertThat(Solar.sunPosition(10_000, SCALE, d / 72f, 0.5f).zenith()).isLessThan(TfcMath.HALF_PI);
        }
    }

    @Test
    void sunIsNeverVisibleAtMidnightAtNorthHemisphereTemperate() {
        for (int d = 0; d < 72; d++) {
            assertThat(Solar.sunPosition(0, SCALE, d / 72f, 0f).zenith()).isGreaterThan(TfcMath.HALF_PI);
        }
    }

    @Test
    void sunBasedDayTimeNoonAndMidnightAreConstantAroundEquatorAndTemperate() {
        for (int d = 0; d < 72; d++) {
            assertThat(Solar.sunBasedDayTime(10_000, SCALE, d / 72f, 0)).isEqualTo(18_000);
            assertThat(Solar.sunBasedDayTime(10_000, SCALE, d / 72f, 0.5f)).isEqualTo(6_000);
            assertThat(Solar.sunBasedDayTime(0, SCALE, d / 72f, 0)).isEqualTo(18_000);
            assertThat(Solar.sunBasedDayTime(0, SCALE, d / 72f, 0.5f)).isEqualTo(6_000);
        }
    }

    @Test
    void sunBasedDayTimeNoonAndMidnightAreEndlessAroundPoles() {
        for (int h = 0; h < 24; h++) {
            assertThat(Solar.sunBasedDayTime(-10_000, SCALE, 0f, h / 24f)).isGreaterThan(12_000); // north pole, winter
            assertThat(Solar.sunBasedDayTime(-10_000, SCALE, 0.5f, h / 24f)).isLessThan(12_000); // north pole, summer
        }
    }

    /**
     * Golden day times from TFC's {@code getSunBasedDayTime} (run unchanged, see the scratch oracle in the M2-CLIM-01 notes):
     * z, fractionOfYear, fractionOfDay -> day time on the 24 000 scale.
     */
    @Test
    void sunBasedDayTimeGoldenValuesFromTfc() {
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0f, 0f)).isEqualTo(18_000);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0f, 0.05f)).isEqualTo(18_534);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0f, 0.25f)).isEqualTo(1_418);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0f, 0.4f)).isEqualTo(4_711);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0f, 0.75f)).isEqualTo(10_581);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0.5f, 0.25f)).isEqualTo(22_581);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0.5f, 0.4f)).isEqualTo(3_948);
        assertThat(Solar.sunBasedDayTime(-60_000, SCALE, 0.5f, 0.75f)).isEqualTo(13_418);
    }
}
