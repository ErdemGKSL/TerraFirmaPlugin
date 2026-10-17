/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link CalendarState}. Expected numbers come from TFC's config defaults (start day 40,
 * month length 8, day length 24 minutes) and TFC's {@code advanceCalendarTick} / {@code setMonthLength}
 * formulas. TFC's pinned checkout has no calendar unit tests to port.
 */
class CalendarStateTest {
    @Test
    void defaultsMatchTfcConfigDefaults() {
        CalendarState s = CalendarState.defaults();
        assertThat(s.daysInMonth()).isEqualTo(8);
        assertThat(s.dayLengthMinutes()).isEqualTo(24);
        assertThat(s.calendarTicks()).isEqualTo(966_000L); // June 1, 1000 at 06:00
        assertThat(s.playerTicks()).isZero();
        assertThat(s.running()).isFalse();
        assertThat(s.month()).isEqualTo(Month.JUNE);
        assertThat(s.season()).isEqualTo(Season.SUMMER);
        assertThat(CalendarTicks.dayOfMonth(s.calendarTicks(), s.daysInMonth())).isEqualTo(1);
    }

    @Test
    void notRunningIsArrested() {
        CalendarState s = CalendarState.defaults();
        assertThat(s.advance(10_000)).isSameAs(s);
    }

    @Test
    void oneInGameDayIsTwentyEightThousandEightHundredPlayerTicks() {
        CalendarState s = CalendarState.defaults().withRunning(true);
        CalendarState day = s.advance(28_800);
        assertThat(day.calendarTicks()).isEqualTo(s.calendarTicks() + 24_000);
        assertThat(day.partialNumerator()).isZero();
        assertThat(day.playerTicks()).isEqualTo(28_800);
    }

    @Test
    void oneInGameHourIsTwelveHundredPlayerTicks() {
        // TFC: at the default day length one in-game hour is one real minute (1200 player ticks).
        CalendarState s = CalendarState.initial(0, 8, 24).withRunning(true);
        assertThat(s.advance(1_200).calendarTicks() - s.calendarTicks()).isEqualTo(1_000);
        assertThat(s.calendarTicksFor(1_200)).isEqualTo(1_000);
        assertThat(s.calendarTicksFor(28_800)).isEqualTo(24_000);
    }

    @Test
    void partialCarryMatchesTfcFloatAccumulation() {
        // dayLength 3 min: 20 / 3 = 6.666... calendar ticks per player tick.
        CalendarState s = CalendarState.initial(0, 8, 3).withRunning(true);
        CalendarState one = s.advance(1);
        assertThat(one.calendarTicks() - s.calendarTicks()).isEqualTo(6);   // floor(6.67)
        assertThat(one.partialNumerator()).isEqualTo(2);                     // 2/3 carried
        CalendarState two = one.advance(1);
        assertThat(two.calendarTicks() - one.calendarTicks()).isEqualTo(7);  // floor(6.67 + 0.67) = 7
        assertThat(two.calendarTicks() - s.calendarTicks()).isEqualTo(13);   // floor(13.33)
        assertThat(two.partialNumerator()).isEqualTo(1);
    }

    @Test
    void lazyAndSteppedAgreeExactly() {
        for (int dayLength : new int[] {24, 3, 7, 1}) {
            CalendarState start = CalendarState.initial(40, 8, dayLength).withRunning(true);
            CalendarState stepped = start;
            for (int i = 0; i < 5_000; i++) {
                stepped = stepped.advance(1);
            }
            assertThat(start.advance(5_000)).as("dayLength %d", dayLength).isEqualTo(stepped);
            assertThat(start.advance(2_000).advance(3_000)).as("dayLength %d split", dayLength).isEqualTo(stepped);
        }
    }

    @Test
    void calendarTickAfterAgreesWithAdvance() {
        CalendarState s = CalendarState.initial(40, 8, 7).withRunning(true).advance(11);
        for (long offset : new long[] {0, 1, 2, 50, 12_345}) {
            assertThat(s.calendarTickAfter(offset)).isEqualTo(s.advance(offset).calendarTicks());
        }
    }

    @Test
    void withDaysInMonthKeepsMonthAndTimeOfDay() {
        // June 5, 06:00 with 8-day months: ticks = 5 * 192 000 + 4 * 24 000 + 6 000.
        CalendarState s = new CalendarState(0, 1_062_000L, 0, 24, 8, false);
        assertThat(s.month()).isEqualTo(Month.JUNE);
        assertThat(CalendarTicks.dayOfMonth(s.calendarTicks(), 8)).isEqualTo(5);

        CalendarState longer = s.withDaysInMonth(16);
        assertThat(longer.daysInMonth()).isEqualTo(16);
        assertThat(longer.month()).isEqualTo(Month.JUNE);
        assertThat(CalendarTicks.hourOfDay(longer.calendarTicks())).isEqualTo(14);
    }

    @Test
    void withDaysInMonthFollowsTfcFormulaExactly() {
        // TFC setMonthLength: fraction of month = 102 000 / 192 000 = 0.53125; floor(0.53125 * 16) = 8.
        // New tick = 5 * 384 000 + (8 * 1000) + 6 000 = 1 934 000. The day index is multiplied by one HOUR,
        // not one DAY: this is TFC's own behaviour, kept on purpose (ledger note), so the result is day 1 at 14:00.
        CalendarState s = new CalendarState(0, 1_062_000L, 0, 24, 8, false);
        CalendarState longer = s.withDaysInMonth(16);
        assertThat(longer.calendarTicks()).isEqualTo(1_934_000L);
        assertThat(CalendarTicks.dayOfMonth(longer.calendarTicks(), 16)).isEqualTo(1);
        assertThat(CalendarTicks.hourOfDay(longer.calendarTicks())).isEqualTo(14);
    }

    @Test
    void withDaysInMonthAtMonthStartStaysAtMonthStart() {
        CalendarState s = new CalendarState(0, 192_000L * 3, 0, 24, 8, false); // April 1
        CalendarState shorter = s.withDaysInMonth(4);
        assertThat(shorter.month()).isEqualTo(Month.APRIL);
        assertThat(shorter.calendarTicks()).isEqualTo(3 * 4 * 24_000L);
    }

    @Test
    void daysInMonthConfigChangesTheDateOfTheSameTick() {
        long t = 300_000L;
        assertThat(CalendarTicks.month(t, 8)).isEqualTo(Month.FEBRUARY);
        assertThat(CalendarTicks.month(t, 30)).isEqualTo(Month.JANUARY);
        assertThat(CalendarTicks.dayOfMonth(t, 8)).isEqualTo(1 + (int) ((t / 24_000) % 8));
        assertThat(CalendarTicks.dayOfMonth(t, 30)).isEqualTo(1 + (int) ((t / 24_000) % 30));
    }

    @Test
    void publishesAnImmutableSnapshot() {
        GlobalClockHolder holder = new GlobalClockHolder();
        CalendarState s = CalendarState.defaults().withRunning(true).advance(28_800);
        ClockSnapshot snap = s.publishTo(holder);
        assertThat(snap.sequence()).isEqualTo(1);
        assertThat(snap.playerTicks()).isEqualTo(28_800);
        assertThat(snap.calendarTicks()).isEqualTo(s.calendarTicks());
        assertThat(snap.daysInMonth()).isEqualTo(8);
        assertThat(snap.running()).isTrue();
        assertThat(holder.get()).isEqualTo(snap);
    }

    @Test
    void rejectsInvalidState() {
        assertThatThrownBy(() -> new CalendarState(-1, 0, 0, 24, 8, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarState(0, -1, 0, 24, 8, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarState(0, 0, 24, 24, 8, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarState(0, 0, 0, 24, 0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarState.initial(-1, 8, 24)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarState.defaults().advance(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarState.defaults().withDaysInMonth(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
