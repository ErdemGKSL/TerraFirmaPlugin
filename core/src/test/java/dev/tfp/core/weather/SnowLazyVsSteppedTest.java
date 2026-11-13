/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.weather.SnowCatchUp.Climate;
import dev.tfp.core.weather.SnowCatchUp.Plan;
import dev.tfp.core.world.BlockPos;
import java.util.Random;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Lazy versus stepped snow (PAR-CLIM-03, old M2-WTH-04): the counts of {@link SnowCatchUp#plan} chained over a climate
 * sequence against TFC's live branch of {@code WeatherHelpers.onTickChunk} stepped every tick over the same sequence.
 *
 * <p><b>The stepped side</b> is TFC's live branch, replayed per calendar tick with a seeded random in TFC's order of
 * draws: {@code nextInt(80) == 0}, then accumulate when the temperature is below -2 C and it precipitates (the mean
 * rainfall over the last tick), else melt when it is above 2 C and {@code nextInt(3) == 0}. Its expected value per tick is
 * 1/80 accumulations in cold precipitation and 1/240 melts in warmth. Simplification: both branches read the warmer of
 * the site's two columns ({@link Site#temperature}), as the catch-up does; TFC's live branch reads only the sequential
 * placement position for accumulation and only the random climate-check position for melting. The two columns are a few
 * blocks apart in the same chunk, so the difference is far below the tolerances.
 *
 * <p><b>The lazy side</b> is the catch-up a chunk gets each time it is loaded after an absence: the sequence is cut into
 * absences of {@link #ABSENCE} ticks and each gets one {@code plan}. A sample stands for 4 000 ticks: 51 accumulations
 * scaled by TFC's fuzz {@code clampedMap(T, -2, -12, 0.5, 1)} (so at or below -12 C it is 51 against the stepped 50), or 17
 * melts (against the stepped 16.67). The fuzz is TFC's own and makes the catch-up deliberately lighter near the threshold,
 * so the equivalence is checked against the stepped expectation with the same fuzz applied per tick, and against the
 * plain stepped run as a bound: the lazy accumulation is at least half and at most 1.02 times the stepped one (plus the
 * sampling error of a 4 000-tick grid).
 *
 * <p>The climate sequence is the real one: {@link OverworldClimate} for a fixed world seed with TFC's rain segments, over
 * one calendar year, at three sites (polar, temperate with a shoulder season, warm).
 *
 * <p>Numbers at the time of writing (year 2 of the calendar, 24 stepped runs): polar 4 885 lazy accumulations against
 * 5 025 fuzzed-expected and 5 364 stepped; temperate 1 580 / 4 590 (accumulate / melt) lazy against 1 575 / 4 490
 * fuzzed-expected and 2 248 / 4 507 stepped; warm 9 792 lazy melts against 9 622 stepped.
 */
class SnowLazyVsSteppedTest {
    private static final long WORLD_SEED = 1234L;
    private static final int DAYS_IN_MONTH = 8;
    private static final long YEAR = 12L * SnowCatchUp.MONTH_WINDOW;
    /** The sequence starts one year into the calendar (January of the second year has a past to average over). */
    private static final long START = YEAR;
    /** The absence between two loads of the chunk: three samples, short enough that no cap applies. */
    private static final long ABSENCE = 12_000L;
    /** Stepped runs per site (independent seeds). */
    private static final int RUNS = 24;

    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(WORLD_SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);

    /** One site: two columns of a chunk (TFC's random and sequential positions) and the chunk's climate. */
    private record Site(String name, BlockPos a, BlockPos b, ChunkClimate chunk) implements Climate {
        @Override
        public float temperature(long t) {
            return Math.max(CLIMATE.instantTemperature(a, chunk, t, DAYS_IN_MONTH),
                    CLIMATE.instantTemperature(b, chunk, t, DAYS_IN_MONTH));
        }

        @Override
        public float rainIntensity(long t) {
            return CLIMATE.rainIntensity(t);
        }

        float meanRainfall(long from, long to) {
            return SnowCatchUp.averageRainfall(from, to, t -> CLIMATE.instantRainfall(chunk, t, DAYS_IN_MONTH));
        }

        boolean coldAndPrecipitating(long t, float rainfall) {
            float temperature = temperature(t);
            return temperature < -2f && PrecipitationType.isPrecipitating(rainIntensity(t), rainfall);
        }
    }

    private static final Site POLAR = new Site("polar", new BlockPos(3, 90, -2000), new BlockPos(12, 92, -1990),
            new ChunkClimate(-14f, 320f, 0.2f, 0f));
    private static final Site TEMPERATE = new Site("temperate", new BlockPos(3, 70, -1000), new BlockPos(12, 71, -990),
            new ChunkClimate(2f, 280f, -0.3f, 0f));
    private static final Site WARM = new Site("warm", new BlockPos(3, 64, 4000), new BlockPos(12, 64, 4010),
            new ChunkClimate(22f, 250f, 0f, 0f));

    /** Accumulations and melts of one run. */
    private record Counts(double accumulate, double melt) {
        double net() {
            return accumulate - melt;
        }
    }

    /** TFC's live branch every tick of {@code [from, to)}, one seeded random. */
    private static Counts stepped(Site site, long from, long to, long seed) {
        Random random = new Random(seed);
        int accumulate = 0;
        int melt = 0;
        for (long t = from; t < to; t++) {
            if (random.nextInt(80) != 0) {
                continue;
            }
            // The live branch's mean rainfall is over the time since the last tick, one tick
            if (site.coldAndPrecipitating(t, site.meanRainfall(t - 1, t))) {
                accumulate++;
            } else if (site.temperature(t) > 2f && random.nextInt(3) == 0) {
                melt++;
            }
        }
        return new Counts(accumulate, melt);
    }

    /** The expectation of {@link #stepped}, with {@code fuzz} applied to each accumulation (1 = TFC's live branch). */
    private static Counts steppedExpectation(Site site, long from, long to, boolean fuzz) {
        double accumulate = 0;
        double melt = 0;
        for (long t = from; t < to; t += 20) { // 20-tick grid: the climate moves far slower than that
            float temperature = site.temperature(t);
            if (temperature < -2f && PrecipitationType.isPrecipitating(site.rainIntensity(t), site.meanRainfall(t - 1, t))) {
                double weight = fuzz ? SnowCatchUp.clampedMap(temperature, -2f, -12f, 0.5f, 1f) : 1.0;
                accumulate += 20.0 / 80.0 * weight;
            } else if (temperature > 2f) {
                melt += 20.0 / 240.0;
            }
        }
        return new Counts(accumulate, melt);
    }

    /** The catch-ups of a chunk loaded every {@link #ABSENCE} ticks over {@code [from, to)}, uncapped. */
    private static Counts lazy(Site site, long from, long to) {
        double accumulate = 0;
        double melt = 0;
        for (long last = from; last < to; last += ABSENCE) {
            long now = Math.min(to, last + ABSENCE);
            Plan plan = SnowCatchUp.plan(last, now, site, site.meanRainfall(last, now), 0, Integer.MAX_VALUE);
            accumulate += plan.accumulate();
            melt += plan.melt();
        }
        return new Counts(accumulate, melt);
    }

    private static Counts steppedMean(Site site, long from, long to) {
        double accumulate = 0;
        double melt = 0;
        for (int run = 0; run < RUNS; run++) {
            Counts c = stepped(site, from, to, 0x5EED_0000L + run);
            accumulate += c.accumulate();
            melt += c.melt();
        }
        return new Counts(accumulate / RUNS, melt / RUNS);
    }

    @Test
    void theSteppedReplayMatchesItsExpectation() {
        // Guards the stepped side itself: the Monte Carlo mean is the analytic 1/80 and 1/240 within 3 %
        for (Site site : new Site[] {POLAR, TEMPERATE, WARM}) {
            Counts mean = steppedMean(site, START, START + YEAR);
            Counts expected = steppedExpectation(site, START, START + YEAR, false);
            assertThat(mean.accumulate()).as(site.name() + " accumulate").isCloseTo(expected.accumulate(), tolerance(expected.accumulate(), 0.03));
            assertThat(mean.melt()).as(site.name() + " melt").isCloseTo(expected.melt(), tolerance(expected.melt(), 0.03));
        }
    }

    @Test
    void theLazyCatchUpEqualsTheFuzzedSteppedExpectationOverAYear() {
        for (Site site : new Site[] {POLAR, TEMPERATE, WARM}) {
            Counts lazy = lazy(site, START, START + YEAR);
            Counts expected = steppedExpectation(site, START, START + YEAR, true);
            // 51/50 and 17/(50/3) are TFC's rounding up of one sample, the rest is the 4 000-tick grid
            assertThat(lazy.accumulate()).as(site.name() + " accumulate").isCloseTo(expected.accumulate(), tolerance(expected.accumulate(), 0.10));
            assertThat(lazy.melt()).as(site.name() + " melt").isCloseTo(expected.melt(), tolerance(expected.melt(), 0.05));
        }
    }

    @Test
    void theLazyCatchUpIsBoundedByTheSteppedRunOverAYear() {
        for (Site site : new Site[] {POLAR, TEMPERATE, WARM}) {
            Counts lazy = lazy(site, START, START + YEAR);
            Counts stepped = steppedMean(site, START, START + YEAR);
            double slack = 0.10 * Math.max(stepped.accumulate(), 50);
            assertThat(lazy.accumulate()).as(site.name() + " accumulate lower bound")
                    .isGreaterThanOrEqualTo(0.5 * stepped.accumulate() - slack);
            assertThat(lazy.accumulate()).as(site.name() + " accumulate upper bound")
                    .isLessThanOrEqualTo(1.02 * stepped.accumulate() + slack);
            assertThat(lazy.melt()).as(site.name() + " melt").isCloseTo(stepped.melt(), tolerance(stepped.melt(), 0.05));
            assertThat(Math.signum(Math.round(lazy.net()))).as(site.name() + " net sign")
                    .isEqualTo(Math.signum(Math.round(stepped.net())));
        }
    }

    @Test
    void eachMonthHasTheSameSignAndASimilarSize() {
        // Month by month at the shoulder site: snow in winter, melt in summer, both ways agree. Within a month the
        // 4 000-tick grid aliases TFC's 12 000 to 24 000-tick rain windows (one window more or less is about 50 steps),
        // so a month may differ by half; over the year the grid errors cancel (the test above)
        for (int month = 0; month < 12; month++) {
            long from = START + month * SnowCatchUp.MONTH_WINDOW;
            long to = from + SnowCatchUp.MONTH_WINDOW;
            Counts lazy = lazy(TEMPERATE, from, to);
            Counts expected = steppedExpectation(TEMPERATE, from, to, true);
            double scale = Math.max(Math.abs(expected.net()), 60);
            assertThat(lazy.net()).as("month " + month + " net").isCloseTo(expected.net(), Offset.offset(0.5 * scale));
        }
    }

    @Test
    void oneLongAbsenceIsTheCappedSumOfShortOnes() {
        // A chunk away for a month gets at most 64 steps (TFC's snowMaxAccumulationOnUpdate), the short absences more
        long from = START;
        long to = START + SnowCatchUp.MONTH_WINDOW;
        for (Site site : new Site[] {POLAR, WARM}) {
            Plan once = SnowCatchUp.plan(from, to, site, site.meanRainfall(from, to), 0, SnowCatchUp.DEFAULT_MAX_UPDATES);
            Counts chained = lazy(site, from, to);
            assertThat(once.accumulate()).isLessThanOrEqualTo((int) Math.min(chained.accumulate(), SnowCatchUp.DEFAULT_MAX_UPDATES));
            assertThat(once.melt()).isLessThanOrEqualTo(SnowCatchUp.DEFAULT_MAX_UPDATES);
            if (chained.accumulate() >= SnowCatchUp.DEFAULT_MAX_UPDATES) {
                assertThat(once.accumulate()).isEqualTo(SnowCatchUp.DEFAULT_MAX_UPDATES);
            }
            if (chained.melt() >= SnowCatchUp.DEFAULT_MAX_UPDATES) {
                assertThat(once.melt()).isEqualTo(SnowCatchUp.DEFAULT_MAX_UPDATES);
            }
        }
    }

    private static Offset<Double> tolerance(double expected, double fraction) {
        return Offset.offset(Math.max(fraction * Math.abs(expected), 8.0));
    }
}
