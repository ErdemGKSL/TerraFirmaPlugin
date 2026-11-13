/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * PAR-CLIM-01: TFC's sleep window, the ticks slept and the transaction arithmetic of {@code skipForwardBy}. Golden rows
 * come from TFC's expressions at c3b4d9536 ({@code CalendarEventHandler.onPlayersFinishedSleeping} and
 * {@code onPlayerStartSleeping}, {@code ServerCalendar.skipForwardBy} with {@code calendarTickRate = 20f / dayLength})
 * evaluated in a scratch program outside the repository.
 */
class SleepSkipTest {
    @ParameterizedTest(name = "calendar tick {0}: slept {1}, can sleep {2}")
    @CsvSource({
        "0, 5000, true",
        "1, 4999, true",
        "4999, 0, true", // TFC's float: 0.99 of a tick short of 5 am, truncated to 0
        "5000, 24000, true", // exactly 5 am: the next 5 am is a day later
        "5001, 23999, false",
        "6000, 23000, false",
        "12000, 17000, false",
        "19999, 9001, false",
        "20000, 9000, true",
        "20001, 8999, true",
        "23999, 5001, true",
        "1101500, 7500, true",
        "966000, 23000, false",
        "24022345, 6655, true",
        "2962947210, 1789, true",
    })
    void goldenAgainstTfc(long calendarTicks, int slept, boolean canSleep) {
        assertThat(SleepSkip.calendarTicksSlept(calendarTicks)).isEqualTo(slept);
        assertThat(SleepSkip.canSleepAt(calendarTicks)).isEqualTo(canSleep);
    }

    /** TFC {@code skipForwardBy}: {@code playerTicks += calendarTicks / (20f / dayLength)}; ours is the exact floor. */
    @ParameterizedTest(name = "player {0} + calendar {1} at day length {2}: player {3}")
    @CsvSource({
        "0, 9000, 24, 10800",
        "0, 8000, 24, 9600",
        "0, 7001, 24, 8401",
        "0, 1, 24, 1",
        "1000, 9000, 24, 11800",
        "1000, 7001, 24, 9401",
        "28800, 9000, 24, 39600",
        "28800, 7001, 24, 37201",
        "28800, 1, 24, 28801",
        "5, 9000, 20, 9005",
        "0, 7001, 30, 10501",
    })
    void skipForwardGoldenAgainstTfc(long playerTicks, long calendarSkip, int dayLength, long expectedPlayer) {
        CalendarState s = new CalendarState(playerTicks, 1_000_000L, 0, dayLength, 8, true);
        CalendarState next = s.skipForward(calendarSkip);
        assertThat(next.playerTicks()).isEqualTo(expectedPlayer);
        assertThat(next.calendarTicks()).isEqualTo(1_000_000L + calendarSkip);
    }

    @Test
    void sleepingFromAnyNightTimeWakesAtFiveAm() {
        for (long t = 20_000; t < 24_000 + 4_999; t += 37) {
            long start = 24_000L * 50 + t;
            long woke = start + SleepSkip.calendarTicksSlept(start);
            assertThat(CalendarTicks.hourOfDay(woke)).as("from %d", start).isIn(4, 5);
            assertThat(woke % 24_000).isBetween(4_999L, 5_000L);
        }
    }

    @Test
    void vanillaDayTimeIsTfcOffset() {
        assertThat(SleepSkip.vanillaDayTime(0)).isEqualTo(18_000L); // calendar midnight = vanilla midnight
        assertThat(SleepSkip.vanillaDayTime(6_000) % 24_000).isZero(); // 6 am = vanilla sunrise tick 0
        assertThat(SleepSkip.vanillaDayTime(12_000) % 24_000).isEqualTo(6_000L); // noon
    }

    @Test
    void vanillaTimeCommandsNeverMoveTheCalendarBack() {
        assertThat(SleepSkip.calendarSkipForVanilla(0)).isZero();
        assertThat(SleepSkip.calendarSkipForVanilla(1_000)).isEqualTo(1_000L); // /time add 1000
        assertThat(SleepSkip.calendarSkipForVanilla(-1_000)).isEqualTo(23_000L); // /time set an hour earlier
        assertThat(SleepSkip.calendarSkipForVanilla(-48_000)).isZero(); // same time of day, two days back
    }
}
