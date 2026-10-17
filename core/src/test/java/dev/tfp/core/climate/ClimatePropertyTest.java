/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.world.BlockPos;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Property checks for {@link OverworldClimate}: bounds, continuity, determinism and thread safety. Each property is
 * checked over a fixed grid of positions, worlds and times, so the test is reproducible.
 */
class ClimatePropertyTest {
    private static final int DAYS = 8;
    private static final long TICKS_IN_YEAR = CalendarTicks.ticksInYear(DAYS);
    private static final int[] ZS = {-30_000, -10_000, 0, 5_000, 10_000, 20_000, 33_333, 50_000};
    private static final int[] YS = {-64, -10, 0, 30, 63, 100, 250};
    private static final long[] WORLDS = {0L, 12_345L, -987_654_321L, Long.MAX_VALUE};

    @Test
    void dailyOffsetIsBounded() {
        // (r1 - r2 + 0.3 * h) * 3 with r1, r2 in [0, 1) and h in [-1, 1]: the offset is within (-3.9, 3.9)
        for (long world : WORLDS) {
            final OverworldClimate c = OverworldClimate.forWorldSeed(world, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
            for (int z : ZS) {
                for (int day = 0; day < 1_000; day++) {
                    final float daily = c.dailyTemperature(day * CalendarTicks.CALENDAR_TICKS_IN_DAY + 5_000L, DAYS, z);
                    assertThat(daily).isBetween(-4f, 4f);
                }
            }
        }
    }

    @Test
    void rainfallStaysWithinVarianceBandAndIsNonNegative() {
        // triangle(v * avg, avg, 1, .) is within avg +- |v| * avg, so for |v| <= 1 the rainfall is in [0, 2 avg]
        for (float variance : new float[]{-1f, -0.5f, 0f, 0.25f, 1f}) {
            for (float average : new float[]{0f, 100f, 500f}) {
                final ChunkClimate chunk = new ChunkClimate(0f, average, variance, 0f);
                for (long t = 0; t < TICKS_IN_YEAR; t += 1_777) {
                    final float rain = OverworldClimate.forWorldSeed(0L, 20_000).instantRainfall(chunk, t, DAYS);
                    assertThat(rain).isGreaterThanOrEqualTo(average * (1 - Math.abs(variance)) - 1e-3f);
                    assertThat(rain).isLessThanOrEqualTo(average * (1 + Math.abs(variance)) + 1e-3f);
                    assertThat(rain).isGreaterThanOrEqualTo(0f);
                }
            }
        }
    }

    @Test
    void rainfallIsPeriodicAndContinuousOverTheYear() {
        // The slope of the triangle is 4 * |v * avg| per year, so one tick changes it by at most 4 * 500 / 2_304_000 < 1e-3
        final ChunkClimate chunk = new ChunkClimate(0f, 500f, 1f, 0f);
        final OverworldClimate c = OverworldClimate.forWorldSeed(0L, 20_000);
        float prev = c.instantRainfall(chunk, 0L, DAYS);
        float maxStep = 0f;
        for (long t = 1; t < TICKS_IN_YEAR; t++) {
            final float cur = c.instantRainfall(chunk, t, DAYS);
            maxStep = Math.max(maxStep, Math.abs(cur - prev));
            prev = cur;
        }
        assertThat(maxStep).isLessThan(1e-3f);
        assertThat(c.instantRainfall(chunk, TICKS_IN_YEAR, DAYS)).isEqualTo(c.instantRainfall(chunk, 0L, DAYS));
    }

    @Test
    void temperatureIsContinuousWithinADayAwayFromThePoles() {
        // Measured largest step per calendar tick is 4.6e-4 degrees; the bound leaves a factor of two of margin.
        // Day boundaries are excluded: the daily random offset changes there. Polar noon is excluded too, see
        // polarNoonTimeSkipIsKeptFromTfc.
        final OverworldClimate c = OverworldClimate.forWorldSeed(12_345L, 20_000);
        final ChunkClimate chunk = new ChunkClimate(12.5f, 100f, 0.3f, 0f);
        for (int z : new int[]{-10_000, 0, 5_000, 10_000, 20_000}) {
            for (int y : new int[]{63, 100}) {
                final long dayStart = 37L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
                float prev = c.instantTemperature(new BlockPos(0, y, z), chunk, dayStart, DAYS);
                float maxStep = 0f;
                for (long t = dayStart + 1; t < dayStart + CalendarTicks.CALENDAR_TICKS_IN_DAY; t++) {
                    final float cur = c.instantTemperature(new BlockPos(0, y, z), chunk, t, DAYS);
                    maxStep = Math.max(maxStep, Math.abs(cur - prev));
                    prev = cur;
                }
                assertThat(maxStep).as("z %d y %d", z, y).isLessThan(1e-3f);
            }
        }
    }

    /**
     * TFC's {@code SolarCalculator.getSunBasedDayTime} has a documented "time skip": where the sun stays below the horizon at
     * noon (polar season), the day time jumps from just under 24 000 to just over 12 000 at noon. It is kept, so the daily
     * temperature has the same step as TFC's there (day 37, z = 33 333: 23 077 -> 12 922 at fraction of day 0.5).
     */
    @Test
    void polarNoonTimeSkipIsKeptFromTfc() {
        final long dayStart = 37L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
        final long halfHourBeforeNoon = dayStart + 11_999L;
        final long noon = dayStart + 12_000L;
        final int before = Solar.sunBasedDayTime(33_333, 20_000f, CalendarPhase.of(halfHourBeforeNoon, DAYS).fractionOfYear(),
            CalendarPhase.of(halfHourBeforeNoon, DAYS).fractionOfDay());
        final int at = Solar.sunBasedDayTime(33_333, 20_000f, CalendarPhase.of(noon, DAYS).fractionOfYear(),
            CalendarPhase.of(noon, DAYS).fractionOfDay());
        assertThat(before).isEqualTo(23_077);
        assertThat(at).isEqualTo(12_922);
    }

    @Test
    void groundwaterIsWithinItsClamps() {
        final OverworldClimate c = OverworldClimate.forWorldSeed(0L, 20_000);
        for (float base : new float[]{0f, 250f, 1_500f}) {
            for (float average : new float[]{0f, 300f, 600f}) {
                final ChunkClimate chunk = new ChunkClimate(0f, average, 0.5f, base);
                assertThat(c.averageGroundwater(chunk)).isBetween(0f, OverworldClimate.MAX_AVERAGE_RAINFALL);
                for (long t = 0; t < TICKS_IN_YEAR; t += 50_000) {
                    assertThat(c.instantGroundwater(chunk, t, DAYS)).isBetween(0f, OverworldClimate.MAX_INSTANT_GROUNDWATER);
                }
            }
        }
    }

    @Test
    void sameInputsGiveSameOutputsAcrossThreads() {
        // The model holds only its seed, so queries from any thread give the same bits as a sequential run
        final OverworldClimate c = OverworldClimate.forWorldSeed(12_345L, 20_000);
        final ChunkClimate chunk = new ChunkClimate(12.5f, 200f, -0.3f, 10f);
        final List<Float> sequential = IntStream.range(0, 4_000)
            .mapToObj(i -> c.instantTemperature(new BlockPos(i, 80 + i % 40, -5_000 + i * 7), chunk, i * 9_973L, DAYS))
            .toList();
        final List<Float> parallel = IntStream.range(0, 4_000).parallel()
            .mapToObj(i -> c.instantTemperature(new BlockPos(i, 80 + i % 40, -5_000 + i * 7), chunk, i * 9_973L, DAYS))
            .toList();
        assertThat(parallel).containsExactlyElementsOf(sequential);
    }
}
