/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate.fog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.climate.SeasonalColour;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.climate.SeasonalBiomeRemap;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-30: the foggy variant pick of the seasonal remap. */
class FogRemapTest {
    private static final long SEED = 4242L;

    private static ClockSnapshot at(long day, int hour) {
        return new ClockSnapshot(1, 0L, day * 24_000L + hour * 1_000L, 8, true);
    }

    private static int nonFreezingBand() {
        for (int b = 0; b < SeasonalColour.BAND_COUNT; b++) {
            if (!SeasonalColour.isFreezing(b)) {
                return b;
            }
        }
        throw new AssertionError();
    }

    @Test
    void noFogKeepsTheSeasonalVariant() {
        int v = SeasonalBiomeRemap.variantIndex(3, nonFreezingBand());
        assertThat(FogRemap.apply(v, 0)).isEqualTo(v);
    }

    @Test
    void aFreezingBandKeepsItsVariantInFog() {
        int freezing = -1;
        for (int b = 0; b < SeasonalColour.BAND_COUNT; b++) {
            if (SeasonalColour.isFreezing(b)) {
                freezing = b;
            }
        }
        int v = SeasonalBiomeRemap.variantIndex(3, freezing);
        assertThat(FogRemap.apply(v, 2)).isEqualTo(v);
    }

    @Test
    void foggyIndicesFollowTheSeasonalOnesAndCoverEveryBiomeAndLevelOnce() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int biome = 0; biome < dev.tfp.core.worldgen.biome.TfcBiomes.COUNT; biome++) {
            for (int level = 1; level <= FogForecast.LEVELS; level++) {
                int idx = FogRemap.apply(SeasonalBiomeRemap.variantIndex(biome, nonFreezingBand()), level);
                assertThat(idx).isBetween(FogRemap.FIRST_FOGGY_INDEX, FogRemap.FIRST_FOGGY_INDEX + FogRemap.FOGGY_COUNT - 1);
                assertThat(seen.add(idx)).isTrue();
            }
        }
        assertThat(seen).hasSize(FogRemap.FOGGY_COUNT);
        assertThat(FogRemap.idOf(FogRemap.apply(SeasonalBiomeRemap.variantIndex(0, nonFreezingBand()), 2)))
            .isEqualTo("tfp:fog/" + SeasonalBiomeRemap.biomePath(0) + "/l2");
        assertThatThrownBy(() -> FogRemap.idOf(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRemapPicksTheFoggyVariantAtSevenAmOnAFoggyDayAndTheBaseAtOnePm() {
        int[] p = null;
        for (int x = -20_000; x <= 20_000 && p == null; x += 1_000) {
            for (int z = -20_000; z <= 20_000 && p == null; z += 1_000) {
                if (FogForecast.at(SEED, x, z, at(100, 7), 15, true).level() > 0) {
                    p = new int[] {x, z};
                }
            }
        }
        assertThat(p).isNotNull();
        int v = SeasonalBiomeRemap.variantIndex(5, nonFreezingBand());
        int morning = FogForecast.at(SEED, p[0], p[1], at(100, 7), 15, true).level();
        int afternoon = FogForecast.at(SEED, p[0], p[1], at(100, 13), 15, true).level();
        assertThat(FogRemap.apply(v, morning)).isGreaterThanOrEqualTo(FogRemap.FIRST_FOGGY_INDEX);
        assertThat(FogRemap.apply(v, afternoon)).isEqualTo(v);
    }
}
