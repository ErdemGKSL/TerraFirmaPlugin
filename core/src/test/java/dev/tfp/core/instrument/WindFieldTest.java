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

import dev.tfp.core.instrument.WindField.Wind;
import dev.tfp.core.power.WindSpeed;
import org.junit.jupiter.api.Test;

class WindFieldTest {
    private static final long SEED = 123456789L;
    private static final int SCALE = WindField.DEFAULT_OCEAN_WIND_SCALE;

    /** getWind line 311: no wind below sea level minus six. */
    @Test
    void noWindBelowSeaLevel() {
        assertThat(WindField.at(SEED, 100, 56, 0, false, false, true, SCALE)).isEqualTo(Wind.NONE);
        assertThat(WindField.at(SEED, 100, 57, 0, false, false, true, SCALE).length()).isGreaterThan(0f);
    }

    /** The result is a pure function of its inputs, and the magnitude stays in [0, 1]. */
    @Test
    void deterministicAndBounded() {
        for (long day = 0; day < 300; day++) {
            Wind a = WindField.at(SEED, day, 80, 10, day % 3 == 0, false, true, SCALE);
            Wind b = WindField.at(SEED, day, 80, 10, day % 3 == 0, false, true, SCALE);
            assertThat(a).isEqualTo(b);
            assertThat(a.length()).isBetween(0f, 1.0001f);
        }
    }

    /** The draw order: the gaussian first, the uniform re-draw only past 0.23, then the free angle (lines 327-372). */
    @Test
    void replaysTheDrawOrder() {
        for (long day = 0; day < 200; day++) {
            WindRandom r = WindRandom.seeded(SEED, day, WindField.SALT);
            float gaussian = (float) r.nextGaussian();
            boolean redraw = (gaussian * 0.3f + 0.45f) * 0.25f > 0.23f;
            float uniform = redraw ? r.nextFloat() : 0f;
            float intensity = WindSpeed.intensity(70f, false, false, gaussian, uniform);
            Wind w = WindField.at(SEED, day, 70, 0, false, false, true, SCALE);
            assertThat(w.length()).isCloseTo(intensity, within(1e-5f));
        }
    }

    /** Line 339: 128 blocks above sea level add 0.25 (below the clamp). */
    @Test
    void altitudeAddsToTheSpeed() {
        int checked = 0;
        for (long day = 0; day < 300 && checked < 20; day++) {
            float low = WindField.at(SEED, day, 63, 0, false, false, true, SCALE).length();
            float high = WindField.at(SEED, day, 191, 0, false, false, true, SCALE).length();
            if (low > 0.01f && low + 0.25f < 1f) { // not clamped at 0 or 1
                assertThat(high - low).isCloseTo(0.25f, within(1e-5f));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(20);
    }

    /** Lines 344-367: day odd band pi/4, day even band 7pi/4, night odd 5pi/4, night even 3pi/4, each within 0.1 rad. */
    @Test
    void predictableBands() {
        final float pi = (float) Math.PI;
        for (long day = 0; day < 40; day++) {
            assertAngle(WindField.at(SEED, day, 70, 6000, false, true, true, SCALE), pi / 4);
            assertAngle(WindField.at(SEED, day, 70, -6000, false, true, true, SCALE), pi / 4);
            assertAngle(WindField.at(SEED, day, 70, 100, false, true, true, SCALE), -pi / 4);
            assertAngle(WindField.at(SEED, day, 70, 6000, false, true, false, SCALE), -3 * pi / 4);
            assertAngle(WindField.at(SEED, day, 70, 100, false, true, false, SCALE), 3 * pi / 4);
            assertThat(WindField.at(SEED, day, 70, 100, false, true, true, SCALE).length()).isGreaterThanOrEqualTo(0.08f);
        }
    }

    private static void assertAngle(Wind w, float expected) {
        float diff = Math.abs(w.angle() - expected);
        assertThat(diff).isLessThanOrEqualTo(WindField.OCEAN_JITTER + 1e-3f);
    }

    /** The polar gaussian is standard normal: mean 0, deviation 1 over many draws. */
    @Test
    void gaussianIsStandardNormal() {
        WindRandom r = WindRandom.seeded(SEED, 7, WindField.SALT);
        int n = 40_000;
        double sum = 0;
        double sq = 0;
        for (int i = 0; i < n; i++) {
            double g = r.nextGaussian();
            sum += g;
            sq += g * g;
        }
        assertThat(sum / n).isCloseTo(0.0, within(0.03));
        assertThat(Math.sqrt(sq / n)).isCloseTo(1.0, within(0.03));
    }

    /** nextFloat is 24 bits scaled by 2^-24: in [0, 1) and one step apart values differ by 2^-24. */
    @Test
    void floatRange() {
        WindRandom r = WindRandom.seeded(SEED, 1, 5);
        for (int i = 0; i < 1000; i++) {
            assertThat(r.nextFloat()).isGreaterThanOrEqualTo(0f).isLessThan(1f);
        }
    }

    @Test
    void kmh() {
        assertThat(new Wind(0.3f, 0.4f).kmh()).isCloseTo(0.5f * 115, within(1e-3f));
    }
}
