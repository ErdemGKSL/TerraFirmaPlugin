/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static dev.tfp.core.weather.PrecipitationType.NONE;
import static dev.tfp.core.weather.PrecipitationType.RAIN;
import static dev.tfp.core.weather.PrecipitationType.SNOW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link PrecipitationType#decide}, from TFC's {@code WeatherHelpers}: the snow threshold is
 * {@code temperature > 0f} (line 103) and precipitation needs {@code rainIntensity - clampedMap(rainfall, 0, 500, 1, 0) > 0}
 * (lines 116 and 121). TFC has no unit tests for these, so the boundaries below are derived from those expressions.
 *
 * <p>Boundary values use the float neighbours of each threshold ({@link Math#nextUp}, {@link Math#nextDown}). Rainfall
 * 250 mm gives a dry-side term of exactly 0.5, so with a rain intensity of 0.5 the rain test is exactly at its boundary.
 */
class PrecipitationTypeTest {
    /** A rain intensity and rainfall that are precipitating at any temperature: real intensity 0.75 - 0.5 = 0.25. */
    private static final float WET_INTENSITY = 0.75f;
    private static final float WET_RAINFALL = 250f;

    @Test
    void snowThresholdIsStrictlyAboveZero() {
        assertThat(PrecipitationType.decide(Math.nextUp(0f), WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(RAIN);
        assertThat(PrecipitationType.decide(0f, WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(SNOW);
        assertThat(PrecipitationType.decide(-0f, WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(SNOW);
        assertThat(PrecipitationType.decide(Math.nextDown(0f), WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(SNOW);
    }

    @Test
    void temperaturesFarFromZeroFallOnTheirSide() {
        assertThat(PrecipitationType.decide(20f, WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(RAIN);
        assertThat(PrecipitationType.decide(-20f, WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(SNOW);
    }

    @Test
    void rainfallThresholdIsStrictAtTheRealIntensityZero() {
        // realRainIntensity = 0.5 - (1 - rainfall / 500): exactly zero at 250 mm, so nothing falls there
        assertThat(PrecipitationType.decide(20f, 250f, 0.5f, true)).isEqualTo(NONE);
        assertThat(PrecipitationType.decide(20f, Math.nextDown(250f), 0.5f, true)).isEqualTo(NONE);
        // Just above 250 mm the dry-side term drops below 0.5, so rain falls at any positive temperature
        assertThat(PrecipitationType.decide(20f, Math.nextUp(250f), 0.5f, true)).isEqualTo(RAIN);
        assertThat(PrecipitationType.decide(-20f, Math.nextUp(250f), 0.5f, true)).isEqualTo(SNOW);
    }

    @Test
    void zeroRainfallNeverPrecipitates() {
        // clampedMap(0, 0, 500, 1, 0) = 1, so the real intensity is at most rainIntensity - 1, which is never above 0
        // for a TFC rain intensity in [0, 1]. Negative rainfall clamps to the same value.
        float[] intensities = {0f, 0.5f, Math.nextDown(1f), 1f};
        for (float intensity : intensities) {
            assertThat(PrecipitationType.decide(-20f, 0f, intensity, true)).isEqualTo(NONE);
            assertThat(PrecipitationType.decide(20f, 0f, intensity, true)).isEqualTo(NONE);
            assertThat(PrecipitationType.decide(20f, -5f, intensity, true)).isEqualTo(NONE);
        }
    }

    @Test
    void noRainCoveringTheTimeMeansNoPrecipitation() {
        // TFC getRain returns -1 when no rain segment covers the time
        assertThat(PrecipitationType.decide(-20f, WET_RAINFALL, -1f, true)).isEqualTo(NONE);
        assertThat(PrecipitationType.decide(20f, WET_RAINFALL, -1f, true)).isEqualTo(NONE);
    }

    @Test
    void positionWithoutSkyNeverPrecipitates() {
        // Vanilla Level.isRainingAt checks canSeeSky before the biome call that TFC replaces
        assertThat(PrecipitationType.decide(20f, WET_RAINFALL, WET_INTENSITY, false)).isEqualTo(NONE);
        assertThat(PrecipitationType.decide(-20f, WET_RAINFALL, WET_INTENSITY, false)).isEqualTo(NONE);
    }

    @Test
    void unknownValuesGiveNone() {
        assertThat(PrecipitationType.decide(Float.NaN, WET_RAINFALL, WET_INTENSITY, true)).isEqualTo(NONE);
        assertThat(PrecipitationType.decide(20f, Float.NaN, WET_INTENSITY, true)).isEqualTo(NONE);
        assertThat(PrecipitationType.decide(20f, WET_RAINFALL, Float.NaN, true)).isEqualTo(NONE);
    }
}
