/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.weather.SnowCatchUp.Climate;
import dev.tfp.core.weather.SnowCatchUp.Plan;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Properties of {@link SnowCatchUp}: zero for zero elapsed time, monotone in the elapsed time, capped, and due only past 4 000. */
class SnowCatchUpPropertyTest {
    private static final long NOW = 2_000_000L;

    /** A constant climate: the monotone properties hold exactly for these. */
    private record Constant(float temperature, float rain) implements Climate {
        @Override
        public float temperature(long calendarTicks) {
            return temperature;
        }

        @Override
        public float rainIntensity(long calendarTicks) {
            return rain;
        }
    }

    /** A seeded piecewise climate: any temperature in [-20, 20] C and rain at any tick. */
    private static Climate randomClimate(Random random) {
        long seed = random.nextLong();
        return new Climate() {
            @Override
            public float temperature(long t) {
                long h = mix(seed ^ (t / 1_000L));
                return -20f + 40f * (h >>> 40) / (float) (1L << 24);
            }

            @Override
            public float rainIntensity(long t) {
                return (mix(seed ^ (t / 4_000L + 77)) & 1) == 0 ? 1f : 0f;
            }
        };
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Test
    void zeroElapsedTimeIsNoStepsForAnyClimate() {
        Random random = new Random(1);
        for (int i = 0; i < 500; i++) {
            Climate c = randomClimate(random);
            long now = random.nextInt(Integer.MAX_VALUE);
            float rainfall = random.nextFloat() * 500f;
            assertThat(SnowCatchUp.plan(now, now, c, rainfall, random.nextInt(300), 64)).isEqualTo(Plan.NONE);
        }
    }

    @Test
    void aClockThatWentBackwardsIsNoStepsEither() {
        Climate cold = new Constant(-10f, 1f);
        assertThat(SnowCatchUp.plan(NOW + 100_000, NOW, cold, 250f, 0, 64)).isEqualTo(Plan.NONE);
    }

    @Test
    void theCatchUpIsDueOnlyPastTheSampleInterval() {
        assertThat(SnowCatchUp.isDue(SnowCatchUp.NEVER, NOW)).isTrue();
        assertThat(SnowCatchUp.isDue(NOW - 4_000, NOW)).isFalse();
        assertThat(SnowCatchUp.isDue(NOW - 4_001, NOW)).isTrue();
        assertThat(SnowCatchUp.isDue(NOW, NOW)).isFalse();
        assertThat(SnowCatchUp.isDue(NOW + 10, NOW)).isFalse();
    }

    @Test
    void accumulationIsMonotoneInTheElapsedTimeForAColdClimate() {
        Climate cold = new Constant(-10f, 1f);
        int previous = 0;
        for (long elapsed = 0; elapsed <= 400_000L; elapsed += 997) {
            int accumulate = SnowCatchUp.plan(NOW - elapsed, NOW, cold, 250f, 0, 1_000_000).accumulate();
            assertThat(accumulate).as("elapsed %d", elapsed).isGreaterThanOrEqualTo(previous);
            previous = accumulate;
        }
    }

    @Test
    void meltIsMonotoneInTheElapsedTimeForAWarmClimate() {
        Climate warm = new Constant(10f, 1f);
        int previous = 0;
        for (long elapsed = 0; elapsed <= 800_000L; elapsed += 997) {
            int melt = SnowCatchUp.plan(NOW - elapsed, NOW, warm, 250f, 0, 1_000_000).melt();
            assertThat(melt).as("elapsed %d", elapsed).isGreaterThanOrEqualTo(previous);
            previous = melt;
        }
    }

    @Test
    void theWindowIsCappedAtOneMonthForAccumulation() {
        // Rain only in the last month's first part (three samples: 3 x 45 = 135, under the 256 cap) and in a band older
        // than the month. Ten months of elapsed time must not reach the older band.
        Climate band = new Climate() {
            @Override
            public float temperature(long t) {
                return -10f;
            }

            @Override
            public float rainIntensity(long t) {
                boolean recent = t >= NOW - SnowCatchUp.MONTH_WINDOW && t <= NOW - 180_000L;
                boolean old = t < NOW - 300_000L;
                return recent || old ? 1f : 0f;
            }
        };
        int oneMonth = SnowCatchUp.plan(NOW - SnowCatchUp.MONTH_WINDOW, NOW, band, 250f, 0, 1_000_000).accumulate();
        assertThat(oneMonth).isEqualTo(135);
        assertThat(SnowCatchUp.plan(NOW - 10 * SnowCatchUp.MONTH_WINDOW, NOW, band, 250f, 0, 1_000_000).accumulate())
                .isEqualTo(oneMonth);
    }

    @Test
    void everyCapHolds() {
        Random random = new Random(7);
        for (int i = 0; i < 2_000; i++) {
            Climate c = randomClimate(random);
            long elapsed = random.nextInt(900_000);
            int existing = random.nextInt(400);
            int max = random.nextInt(100);
            float rainfall = random.nextFloat() * 500f;
            Plan plan = SnowCatchUp.plan(NOW - elapsed, NOW, c, rainfall, existing, max);
            assertThat(plan.accumulate()).isLessThanOrEqualTo(max);
            assertThat(plan.accumulate()).isLessThanOrEqualTo(Math.max(0, SnowCatchUp.MAX_SNOW_PER_CHUNK - existing));
            assertThat(plan.melt()).isLessThanOrEqualTo(max);
            assertThat(plan.accumulate() == 0 || plan.melt() == 0).isTrue();
        }
    }

    @Test
    void aFullChunkGetsNoMoreSnow() {
        Climate cold = new Constant(-10f, 1f);
        assertThat(SnowCatchUp.plan(NOW - 100_000, NOW, cold, 250f, 256, 64)).isEqualTo(Plan.NONE);
        assertThat(SnowCatchUp.plan(NOW - 100_000, NOW, cold, 250f, 300, 64)).isEqualTo(Plan.NONE);
    }

    @Test
    void theMeanRainfallIsTheMeanOfFourSamples() {
        assertThat(SnowCatchUp.averageRainfall(0, 1_000, t -> 120f)).isEqualTo(120f);
        // samples at 0, 333, 666, 1000 of a ramp that equals the tick over 1000
        assertThat(SnowCatchUp.averageRainfall(0, 1_000, t -> t / 1_000f * 400f))
                .isCloseTo((0f + 133.2f + 266.4f + 400f) / 4f, within(1e-3f));
    }

    @Test
    void theAveragingWindowOfANeverUpdatedChunkIsTheLastMonth() {
        assertThat(SnowCatchUp.averagingFrom(SnowCatchUp.NEVER, NOW)).isEqualTo(NOW - SnowCatchUp.MONTH_WINDOW);
        assertThat(SnowCatchUp.averagingFrom(SnowCatchUp.NEVER, 1_000)).isZero();
        assertThat(SnowCatchUp.averagingFrom(500_000, NOW)).isEqualTo(500_000);
    }
}
