/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.clock.ClockSnapshot;
import org.junit.jupiter.api.Test;

/** Golden values of TFC {@code Climate.fromVanilla} = (t - 0.15) / 0.0217 and the biome-based rainfall rule. */
class BiomeBasedClimateGoldenTest {
    @Test
    void fromVanillaMatchesTfcFormula() {
        // plains 0.8, desert 2.0, snowy plains 0.0, nether_wastes 2.0, the_end 0.5, frozen_peaks -0.7, old taiga 0.25 (vanilla values)
        float[] vanilla = {0.8f, 2.0f, 0.0f, 0.5f, -0.7f, 0.25f, 0.15f};
        float[] tfc = {29.953918f, 85.25346f, -6.9124427f, 16.129032f, -39.170506f, 4.6082954f, 0f};
        for (int i = 0; i < vanilla.length; i++) {
            assertThat(BiomeBasedClimate.fromVanilla(vanilla[i])).isCloseTo(tfc[i], within(1e-3f));
        }
        assertThat(BiomeBasedClimate.fromVanilla(0.15f - 30 * 0.0217f)).isCloseTo(-30f, within(1e-3f));
    }

    @Test
    void rainfallIsFlat300OrZero() {
        assertThat(BiomeBasedClimate.averageRainfall(true)).isEqualTo(300f);
        assertThat(BiomeBasedClimate.averageRainfall(false)).isEqualTo(0f);
    }

    @Test
    void readingHasNoVariationAndIsUnknownWithoutClock() {
        ClockSnapshot clock = new ClockSnapshot(7, 1000L, 123_456L, 8, true);
        ClimateReading r = BiomeBasedClimate.reading(2.0f, false, clock);
        assertThat(r.isKnown()).isTrue();
        assertThat(r.temperature()).isEqualTo(r.averageTemperature());
        assertThat(r.rainfall()).isZero();
        assertThat(r.clockSequence()).isEqualTo(7);
        assertThat(BiomeBasedClimate.reading(2.0f, true, ClockSnapshot.UNSET).isKnown()).isFalse();
    }

    @Test
    void selectionFollowsTfc() {
        assertThat(ClimateModelKind.select(true, true)).isEqualTo(ClimateModelKind.OVERWORLD);
        assertThat(ClimateModelKind.select(true, false)).isEqualTo(ClimateModelKind.BIOME_BASED);
        assertThat(ClimateModelKind.select(false, true)).isEqualTo(ClimateModelKind.BIOME_BASED);
        assertThat(ClimateModelKind.select(false, false)).isEqualTo(ClimateModelKind.BIOME_BASED);
    }
}
