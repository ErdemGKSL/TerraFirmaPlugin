/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.world.BlockPos;
import org.junit.jupiter.api.Test;

class ClimateReadingTest {
    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(1000L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
    /** Day 0 of year 1000, noon of the first day: calendar ticks of the published clock. */
    private static final long NOON_JANUARY = 12_000L;

    private static ClockSnapshot clock(long sequence, long calendarTicks) {
        return new ClockSnapshot(sequence, 0L, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    @Test
    void unpublishedClockGivesUnknownValues() {
        ClimateReading r = ClimateReading.of(CLIMATE, ChunkClimate.NEUTRAL, new BlockPos(0, 63, 0), ClockSnapshot.UNSET);
        assertThat(r.isKnown()).isFalse();
        assertThat(r.temperature()).isNaN();
        assertThat(r.averageTemperature()).isNaN();
        assertThat(r.rainfall()).isNaN();
        assertThat(r.clockSequence()).isZero();
    }

    /** At sea level the elevation term is zero, so the average is the chunk's sea-level value, and mid-range rain is exact. */
    @Test
    void atSeaLevelTheAverageIsTheNeutralChunkValue() {
        ClimateReading r = ClimateReading.of(CLIMATE, ChunkClimate.NEUTRAL, new BlockPos(0, 63, 0), clock(7, NOON_JANUARY));
        assertThat(r.isKnown()).isTrue();
        assertThat(r.averageTemperature()).isEqualTo(20f);
        assertThat(r.rainfall()).isEqualTo(250f);
        // Monthly and daily swings are bounded (monthly up to 18 C, daily up to about 4 C plus the sun term).
        assertThat(r.temperature()).isCloseTo(20f, within(30f));
        assertThat(r.clockSequence()).isEqualTo(7);
        assertThat(r.calendarTicks()).isEqualTo(NOON_JANUARY);
    }

    /** TFC's lapse rate above sea level: 0.16225 C per block over sea level (y = 100 is 37 blocks up). */
    @Test
    void averageFallsWithElevationAboveSeaLevel() {
        ClimateReading r = ClimateReading.of(CLIMATE, ChunkClimate.NEUTRAL, new BlockPos(0, 100, 0), clock(1, NOON_JANUARY));
        assertThat(r.averageTemperature()).isCloseTo(20f - 37 * 0.16225f, within(0.001f));
    }

    /** Same inputs, same reading, whatever the thread or the order of queries. */
    @Test
    void sameInputsGiveTheSameReading() {
        BlockPos pos = new BlockPos(120, 80, -340);
        ClimateReading a = ClimateReading.of(CLIMATE, ChunkClimate.NEUTRAL, pos, clock(3, 123_456L));
        ClimateReading b = ClimateReading.of(CLIMATE, ChunkClimate.NEUTRAL, pos, clock(3, 123_456L));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void unknownKeepsTheClockForOrdering() {
        ClimateReading u = ClimateReading.unknown(9, 500L);
        assertThat(u.clockSequence()).isEqualTo(9);
        assertThat(u.calendarTicks()).isEqualTo(500L);
        assertThat(u.isKnown()).isFalse();
    }
}
