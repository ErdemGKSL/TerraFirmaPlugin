/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import org.junit.jupiter.api.Test;

/**
 * Golden values of {@link SeaIce} from TFC at the pinned commit (c3b4d9536). The oracle (the same scratch harness as
 * {@link SnowRuleGoldenTest}) ran TFC's {@code BiomeNoise.seaIceNoise(Seed.of(seed).forkStable().next())} on TFC's own
 * {@code Cellular2D}, {@code OpenSimplex2D}, {@code FastNoiseLite} and {@code Seed} with Minecraft 1.21.11's {@code Mth} and
 * {@code XoroshiroRandomSource}, {@code OverworldClimateModel.getAverageMonthlyTemperature(z, 63, avg, 1, true)} with its
 * {@code calculateMonthlyTemperature}, {@code adjustTemperatureByElevation}, {@code SolarCalculator.getInNorthernHemisphere}
 * and the default temperature scale 20,000, the early exit of {@code frozenOceanExtension} (warmest month above 2) and
 * {@code placeSeaIce}'s decision. It hashed, per seed, x, z and average temperature, the warmest month's float bits and the
 * decision, and per column the noise's double bits (FNV-1a 64 over the lines).
 */
class SeaIceTest {
    private static final long[] SEEDS = {0L, 42L, -7L, 123456789L};
    private static final float[] AVERAGES = {-30f, -20f, -15f, -12f, -10f, -8f, -5f, 0f};
    /** From the oracle. */
    private static final long HASH = 0x90f8b668400e3ddfL;
    private static final int TOTAL = 90_944;
    private static final int ICE = 58_012;
    private static final int BETWEEN = 7_192;

    @Test
    void matchesTfcOverAGridOfSeedsColumnsAndTemperatures() {
        OverworldClimate climate = OverworldClimate.forWorldSeed(0L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        long h = SnowScenario.FNV_START;
        int ice = 0;
        int total = 0;
        int between = 0;
        for (long seed : SEEDS) {
            SeaIce sea = SeaIce.forLevelSeed(seed);
            for (int x = -3000; x <= 3000; x += 211) {
                for (int z = -30000; z <= 30000; z += 613) {
                    for (float avg : AVERAGES) {
                        float max = SeaIce.warmestMonth(climate, new ChunkClimate(avg, 250f, 0f, 0f), z);
                        boolean place = sea.placesIce(x, z, max);
                        total++;
                        if (place) {
                            ice++;
                        }
                        if (max >= -0.5f && max <= 1.5f) {
                            between++;
                        }
                        h = SnowScenario.fnv(h, seed + "," + x + "," + z + "," + avg + "=" + Float.floatToIntBits(max) + ":" + place);
                    }
                    h = SnowScenario.fnv(h, "n" + Double.doubleToLongBits(sea.pattern(x, z)));
                }
            }
        }
        assertThat(total).isEqualTo(TOTAL);
        assertThat(ice).isEqualTo(ICE);
        assertThat(between).isEqualTo(BETWEEN);
        assertThat(h).isEqualTo(HASH);
    }

    @Test
    void noiseAndWarmestMonthValuesFromTheOracle() {
        SeaIce sea = SeaIce.forLevelSeed(42L);
        assertThat(sea.pattern(0, 0)).isEqualTo(0.6992111551486854);
        assertThat(sea.pattern(100, -250)).isEqualTo(-0.014129020860390102);
        assertThat(sea.pattern(-1234, 5678)).isEqualTo(0.31604489310929973);
        assertThat(sea.pattern(31, 17)).isEqualTo(0.29160806846025633);
        OverworldClimate climate = OverworldClimate.forWorldSeed(0L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate cold = new ChunkClimate(-10f, 250f, 0f, 0f);
        int[] zs = {-20000, -10000, -5000, 0, 5000, 10000, 15000, 20000};
        float[] expected = {-1f, 8f, 3.5f, -1f, -5.5f, -10f, -5.5f, -1f};
        for (int i = 0; i < zs.length; i++) {
            assertThat(SeaIce.warmestMonth(climate, cold, zs[i])).as("z=%d", zs[i]).isEqualTo(expected[i]);
        }
    }

    @Test
    void thresholdsAreTfcs() {
        // Solid below -0.5, none above 1.5 (the noise is not sampled outside the band), patterned between
        assertThat(SeaIce.placeIce(Math.nextDown(-0.5f), () -> { throw new AssertionError(); })).isTrue();
        assertThat(SeaIce.placeIce(Math.nextUp(1.5f), () -> { throw new AssertionError(); })).isFalse();
        assertThat(SeaIce.placeIce(1.5f, () -> 0.3)).isFalse();
        assertThat(SeaIce.placeIce(1.5f, () -> Math.nextUp(0.3))).isTrue();
        assertThat(SeaIce.placeIce(-0.5f, () -> 0.03)).isFalse();
        assertThat(SeaIce.placeIce(-0.5f, () -> 0.05)).isTrue();
        // frozenOceanExtension: nothing at all above 2
        assertThat(SeaIce.forLevelSeed(1L).placesIce(0, 0, Math.nextUp(2f))).isFalse();
    }
}
