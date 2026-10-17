/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Properties of {@link OverworldClimate#rainIntensity} (TFC {@code OverworldClimateModel.getRain}). TFC has no numeric
 * tests for it at the pinned commit, so these check the documented contract rather than exact values: the output is
 * -1 or within [0, 1], a segment's rain is one window of 12,000 to 24,000 ticks per 66,000, and the value is
 * deterministic for a tick.
 */
class RainIntensityTest {
    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(20_260_101L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
    /** One TFC year at the default 8 days per month: 12 months x 8 days x 24,000 ticks. */
    private static final long YEAR_TICKS = 12L * 8 * 24_000;

    @Test
    void outputIsMinusOneOrWithinUnitRange() {
        for (long tick = 0; tick < 2 * YEAR_TICKS; tick += 37) {
            float value = CLIMATE.rainIntensity(tick);
            assertThat(value == -1f || (value >= 0f && value <= 1f)).as("tick %d: %s", tick, value).isTrue();
        }
    }

    @Test
    void rainFractionMatchesTheWindowLengths() {
        // Each 66,000-tick segment rains for 12,000 to 24,000 ticks (mean 18,000): about 27% of the time.
        int samples = 0;
        int raining = 0;
        for (long tick = 0; tick < YEAR_TICKS; tick += 100) {
            samples++;
            if (CLIMATE.rainIntensity(tick) >= 0f) {
                raining++;
            }
        }
        double fraction = raining / (double) samples;
        assertThat(fraction).isBetween(0.20, 0.35);
    }

    @Test
    void sameTickGivesSameValue() {
        for (long tick = 0; tick < 200_000; tick += 997) {
            assertThat(CLIMATE.rainIntensity(tick)).isEqualTo(CLIMATE.rainIntensity(tick));
        }
    }
}
