/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.world.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link OverworldClimate}. TFC has no climate unit tests at the pinned commit, so the expected numbers
 * are of two kinds:
 * <ul>
 *     <li>Hand-derived from TFC's formulas and constants, where the formula is piecewise linear and needs no RNG or
 *     trigonometry (monthly swing, altitude, rainfall triangle, groundwater clamps). The derivation is in each comment.</li>
 *     <li>Samples from TFC's own {@code OverworldClimateModel} methods, compiled unchanged against Minecraft 1.21 and
 *     run in a scratch harness, for values that depend on the daily random offset and the sun.</li>
 * </ul>
 * Default settings: temperature scale 20 000, 8 days per month, sea level 63.
 */
class ClimateGoldenTest {
    private static final int DAYS = 8;
    private static final long TICKS_IN_DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static final long TICKS_IN_MONTH = CalendarTicks.ticksInMonth(DAYS);

    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(0L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);

    @Test
    void climateSeedIsOneLcgStepOfTheWorldSeed() {
        assertThat(OverworldClimate.forWorldSeed(0L, 20_000).climateSeed()).isEqualTo(719283741234L);
        assertThat(OverworldClimate.forWorldSeed(1000L, 20_000).climateSeed()).isEqualTo(-5079323217109949430L);
    }

    @Test
    void monthlySwingFollowsLatitude() {
        // z - scale / 2 = -10000 at z = 0: triangle(-18, 0, 1/80000, -10000) = -18 * (0.5 - 1) = 9
        assertThat(CLIMATE.monthlyTemperature(0, 1f)).isEqualTo(9f);
        // z = 20000: triangle = -18 * (1.5 - 1) = -9, so the southern hemisphere is warm in January
        assertThat(CLIMATE.monthlyTemperature(20_000, 1f)).isEqualTo(-9f);
        // Equator and the north pole, z = 10000 and z = -10000
        assertThat(CLIMATE.monthlyTemperature(10_000, 1f)).isZero();
        assertThat(CLIMATE.monthlyTemperature(-10_000, 1f)).isEqualTo(18f);
        // z = 5000: -18 * (0.75 - 1) = 4.5, and the modifier scales it linearly
        assertThat(CLIMATE.monthlyTemperature(5_000, 1f)).isEqualTo(4.5f);
        assertThat(CLIMATE.monthlyTemperature(5_000, 0.5f)).isEqualTo(2.25f);
    }

    @Test
    void altitudeLapseAboveSeaLevelIsLinearAndCapped() {
        // (163 - 63) * 0.16225 = 16.225, and 10 - 16.225 = -6.225
        assertThat(OverworldClimate.adjustAverageTemperatureByElevation(163, 10f, 63f)).isCloseTo(-6.225f, within(1e-4f));
        // (300 - 63) * 0.16225 = 38.45, capped at 17.822
        assertThat(OverworldClimate.adjustAverageTemperatureByElevation(300, 10f, 63f)).isCloseTo(10f - 17.822f, within(1e-4f));
        // At sea level there is no lapse
        assertThat(OverworldClimate.adjustAverageTemperatureByElevation(63, 10f, 63f)).isEqualTo(10f);
    }

    @Test
    void depthPullsTowardsLavaLevel() {
        // y = -32 is halfway to y = -64, so (15 + 10) / 2 = 12.5, and y = 0 keeps the average
        assertThat(OverworldClimate.adjustAverageTemperatureByElevation(-32, 10f, 63f)).isEqualTo(12.5f);
        assertThat(OverworldClimate.adjustAverageTemperatureByElevation(0, 10f, 63f)).isEqualTo(10f);
        assertThat(OverworldClimate.adjustAverageTemperatureByElevation(-64, 10f, 63f)).isEqualTo(15f);
    }

    @Test
    void belowSeaLevelTheSwingsAreDamped() {
        // At y = 0 the monthly and daily swings vanish, so every instant equals the average
        final ChunkClimate chunk = new ChunkClimate(10f, 0f, 0f, 0f);
        assertThat(CLIMATE.instantTemperature(new BlockPos(0, 0, 0), chunk, 0L, DAYS)).isEqualTo(10f);
        assertThat(CLIMATE.instantTemperature(new BlockPos(0, 0, 0), chunk, 7 * TICKS_IN_MONTH + 5_000L, DAYS)).isEqualTo(10f);
        // At y = -32 (depth influence 0.5) the average is pulled to (15 + 10) / 2
        assertThat(CLIMATE.instantTemperature(new BlockPos(0, -32, 0), chunk, 0L, DAYS)).isEqualTo(12.5f);
    }

    @Test
    void instantRainfallIsAverageWithoutVariance() {
        final ChunkClimate flat = new ChunkClimate(0f, 100f, 0f, 0f);
        assertThat(CLIMATE.instantRainfall(flat, 0L, DAYS)).isEqualTo(100f);
        assertThat(CLIMATE.instantRainfall(flat, 2 * TICKS_IN_MONTH, DAYS)).isEqualTo(100f);
    }

    @Test
    void instantRainfallTriangleByHand() {
        // triangle(amp, 100, 1, f + 0.75) with amp = variance * 100. Quarter points of the year (Jan 1, Apr 1, Jul 1):
        // f = 0 -> value 0.75 -> -1; f = 0.25 -> value 1.0 -> 0; f = 0.5 -> value 1.25 -> +1
        final long apr1 = 3 * TICKS_IN_MONTH;
        final long jul1 = 6 * TICKS_IN_MONTH;
        // Positive variance: dry January, wet July
        final ChunkClimate wetSummer = new ChunkClimate(0f, 100f, 0.5f, 0f);
        assertThat(CLIMATE.instantRainfall(wetSummer, 0L, DAYS)).isEqualTo(50f);
        assertThat(CLIMATE.instantRainfall(wetSummer, apr1, DAYS)).isEqualTo(100f);
        assertThat(CLIMATE.instantRainfall(wetSummer, jul1, DAYS)).isEqualTo(150f);
        // Negative variance: wet January, dry July
        final ChunkClimate drySummer = new ChunkClimate(0f, 100f, -0.5f, 0f);
        assertThat(CLIMATE.instantRainfall(drySummer, 0L, DAYS)).isEqualTo(150f);
        assertThat(CLIMATE.instantRainfall(drySummer, jul1, DAYS)).isEqualTo(50f);
    }

    @Test
    void groundwaterClampsMatchTfc() {
        // Instant: base + rainfall clamped to [0, 1000]; average: base + average rainfall clamped to [0, 500]
        assertThat(CLIMATE.instantGroundwater(new ChunkClimate(0f, 100f, 0f, 900f), 0L, DAYS)).isEqualTo(1000f);
        assertThat(CLIMATE.instantGroundwater(new ChunkClimate(0f, 100f, 0f, 10f), 0L, DAYS)).isEqualTo(110f);
        assertThat(CLIMATE.averageGroundwater(new ChunkClimate(0f, 300f, 0f, 300f))).isEqualTo(500f);
        assertThat(CLIMATE.averageGroundwater(new ChunkClimate(0f, 20f, 0f, 10f))).isEqualTo(30f);
    }

    @Test
    void averageTemperatureUsesSeaLevelAverageAndLapse() {
        assertThat(CLIMATE.averageTemperature(new BlockPos(0, 163, 0), new ChunkClimate(10f, 0f, 0f, 0f))).isCloseTo(-6.225f, within(1e-4f));
        assertThat(CLIMATE.averageTemperature(new BlockPos(0, 63, 0), new ChunkClimate(10f, 0f, 0f, 0f))).isEqualTo(10f);
    }

    /**
     * Samples from TFC's {@code OverworldClimateModel.getInstantTemperature} (verbatim methods, Minecraft 1.21 Mth and
     * Xoroshiro, world seeds 0 / 12345 / -987654321, scale 20 000, 8 days per month).
     */
    @Test
    void instantTemperatureGoldenValuesFromTfc() {
        assertTemp(0L, -30_000, -40, 0L, -5f, 7.5f);
        assertTemp(0L, -10_000, 63, 0L, -5f, -23.527676f);
        assertTemp(0L, -10_000, 63, 9_999_999L, 25f, 34.856136f);
        assertTemp(12_345L, 0, 100, 500_000L, 12.5f, 3.7120643f);
        assertTemp(12_345L, 0, 100, 1_234_567L, 25f, 28.039413f);
        assertTemp(12_345L, 20_000, 100, 0L, -5f, -2.270831f);
        assertTemp(12_345L, 5_000, 30, 50_000L, 25f, 22.93192f);
        assertTemp(-987_654_321L, -10_000, -40, 9_999_999L, -5f, 7.5f);
        assertTemp(-987_654_321L, 33_333, 200, 1_234_567L, 12.5f, -19.780212f);
    }

    private static void assertTemp(long worldSeed, int z, int y, long ticks, float avg, float expected) {
        final OverworldClimate climate = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        assertThat(climate.instantTemperature(new BlockPos(0, y, z), new ChunkClimate(avg, 0f, 0f, 0f), ticks, DAYS))
            .as("world %d z %d y %d t %d avg %s", worldSeed, z, y, ticks, avg)
            .isEqualTo(expected);
    }

    @Test
    void rainfallGoldenValuesFromTfc() {
        final OverworldClimate climate = OverworldClimate.forWorldSeed(0L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        assertThat(climate.instantRainfall(new ChunkClimate(0f, 100f, -0.5f, 0f), 50_000L, DAYS)).isEqualTo(145.65973f);
        assertThat(climate.instantRainfall(new ChunkClimate(0f, 100f, -0.5f, 0f), 9_999_999L, DAYS)).isEqualTo(81.944534f);
        assertThat(climate.instantRainfall(new ChunkClimate(0f, 450f, 1f, 0f), 1_234_567L, DAYS)).isEqualTo(835.4944f);
    }

    @Test
    void summerIsWarmerThanWinterInTheNorth() {
        // z = 0 is northern temperate: the monthly swing is +9 in July and -9 in January, and the daily swing is at most 3.9
        assertThat(meanOfMonthAt(0, Month.JULY) - meanOfMonthAt(0, Month.JANUARY)).isGreaterThan(10f);
        // z = 20000 is southern temperate: the seasons are reversed
        assertThat(meanOfMonthAt(20_000, Month.JANUARY) - meanOfMonthAt(20_000, Month.JULY)).isGreaterThan(10f);
    }

    private static float meanOfMonthAt(int z, Month month) {
        final ChunkClimate chunk = new ChunkClimate(10f, 0f, 0f, 0f);
        final long monthStart = month.ordinal() * TICKS_IN_MONTH;
        float sum = 0f;
        for (int day = 0; day < DAYS; day++) {
            sum += CLIMATE.instantTemperature(new BlockPos(0, 100, z), chunk, monthStart + day * TICKS_IN_DAY + 12_000L, DAYS);
        }
        return sum / DAYS;
    }
}
