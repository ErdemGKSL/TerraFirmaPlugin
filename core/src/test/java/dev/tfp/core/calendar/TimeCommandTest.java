/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link TimeCommand} and {@link CalendarFormat}, from TFC's {@code TimeCommand} and the TFC config
 * defaults (start day 40, month length 8, day length 24 minutes). The default state is June 1, 1000 at 06:00
 * (calendar tick 966 000), so the time of day is 6 000 ticks.
 */
class TimeCommandTest {
    static final CalendarState DEFAULTS = CalendarState.defaults();

    @Test
    void defaultStateIsJuneFirstAtSixAm() {
        assertThat(DEFAULTS.calendarTicks()).isEqualTo(966_000L);
        assertThat(CalendarFormat.dateTime(DEFAULTS.calendarTicks(), DEFAULTS.daysInMonth())).isEqualTo("June 1, 1000 06:00");
        assertThat(CalendarFormat.season(DEFAULTS.calendarTicks(), DEFAULTS.daysInMonth())).isEqualTo("Summer");
    }

    /** TFC {@code setTimeFromDayTime}: a target still ahead today stays today. */
    @Test
    void setForwardStaysToday() {
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.NOON_TICKS_OF_DAY).ticksFrom(DEFAULTS)).isEqualTo(6_000);
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.NIGHT_TICKS_OF_DAY).ticksFrom(DEFAULTS)).isEqualTo(13_200);
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.DAY_TICKS_OF_DAY).ticksFrom(DEFAULTS)).isEqualTo(1_200);
    }

    /** TFC {@code setTimeFromDayTime}: a target at or before now goes to tomorrow (TFC tests with {@code >}). */
    @Test
    void setBackWrapsToTomorrow() {
        // 06:00 -> midnight: 18 000 ticks to the next midnight
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.MIDNIGHT_TICKS_OF_DAY).ticksFrom(DEFAULTS)).isEqualTo(18_000);
        // at 08:00, the 07:12 target is already past today: it is tomorrow's 07:12
        CalendarState eight = CalendarState.initial(40, 8, 24).skipForward(2_000);
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.DAY_TICKS_OF_DAY).ticksFrom(eight)).isEqualTo(24_000 + 7_200 - 8_000);
    }

    @Test
    void settingTheCurrentTimeOfDayAdvancesADay() {
        CalendarState midnight = CalendarState.initial(40, 8, 24).skipForward(18_000); // 06:00 + 18 000 = midnight
        assertThat(midnight.calendarTicks() % CalendarTicks.CALENDAR_TICKS_IN_DAY).isZero();
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.MIDNIGHT_TICKS_OF_DAY).ticksFrom(midnight)).isEqualTo(24_000);
        // exactly 06:00 with a 06:00 target: a full day, as TFC's strict > makes it
        assertThat(TimeCommand.ticksToTimeOfDay(6_000, 6_000)).isEqualTo(24_000);
    }

    @Test
    void hourMapsToItsBoundaryAndTwentyFourIsMidnight() {
        assertThat(TimeCommand.hourTicksOfDay(0)).isZero();
        assertThat(TimeCommand.hourTicksOfDay(13)).isEqualTo(13_000);
        assertThat(TimeCommand.hourTicksOfDay(23)).isEqualTo(23_000);
        assertThat(TimeCommand.hourTicksOfDay(24)).isZero();
        assertThatThrownBy(() -> TimeCommand.hourTicksOfDay(25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeCommand.hourTicksOfDay(-1)).isInstanceOf(IllegalArgumentException.class);
        // set hour 13 from 06:00 is 7 hours ahead
        assertThat(TimeCommand.setTimeOfDay(TimeCommand.hourTicksOfDay(13)).ticksFrom(DEFAULTS)).isEqualTo(7_000);
    }

    /** TFC {@code add}: amount times the unit, with the running month length. */
    @Test
    void addUsesTfcUnitsAndTheRunningMonthLength() {
        assertThat(TimeCommand.add(TimeCommand.Unit.TICKS, 1).ticksFrom(DEFAULTS)).isEqualTo(1);
        assertThat(TimeCommand.add(TimeCommand.Unit.HOURS, 5).ticksFrom(DEFAULTS)).isEqualTo(5_000);
        assertThat(TimeCommand.add(TimeCommand.Unit.DAYS, 3).ticksFrom(DEFAULTS)).isEqualTo(72_000);
        assertThat(TimeCommand.add(TimeCommand.Unit.MONTHS, 2).ticksFrom(DEFAULTS)).isEqualTo(384_000);
        assertThat(TimeCommand.add(TimeCommand.Unit.YEARS, 1).ticksFrom(DEFAULTS)).isEqualTo(2_304_000);
        // 12 months of 8 days, 24 000 ticks each
        CalendarState nineDays = CalendarState.initial(40, 9, 24);
        assertThat(TimeCommand.add(TimeCommand.Unit.YEARS, 1).ticksFrom(nineDays)).isEqualTo(12L * 9 * 24_000);
    }

    @Test
    void addRejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> TimeCommand.add(TimeCommand.Unit.DAYS, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeCommand.amountTicks(TimeCommand.Unit.HOURS, 0, 8))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** TFC {@code skipForwardBy}: calendar ticks add, player ticks add {@code calendarTicks / (20 / dayLength)}. */
    @Test
    void skipForwardMatchesTfcAndRunsWhileNotRunning() {
        assertThat(DEFAULTS.running()).isFalse();
        CalendarState day = DEFAULTS.skipForward(24_000);
        assertThat(day.calendarTicks()).isEqualTo(990_000L);
        assertThat(day.playerTicks()).isEqualTo(28_800L); // one in-game day of 24 minutes = 28 800 player ticks
        assertThat(day.running()).isFalse();
        assertThat(DEFAULTS.skipForward(0)).isEqualTo(DEFAULTS);
        assertThatThrownBy(() -> DEFAULTS.skipForward(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipForwardKeepsTheFractionalCarry() {
        CalendarState running = DEFAULTS.withRunning(true).advance(7); // 7 player ticks = 35/24 calendar ticks
        CalendarState skipped = running.skipForward(1_000);
        assertThat(skipped.partialNumerator()).isEqualTo(running.partialNumerator());
        assertThat(skipped.calendarTicks()).isEqualTo(running.calendarTicks() + 1_000);
        // lazy and stepped agree on the calendar after a skip
        assertThat(skipped.advance(20).calendarTicks()).isEqualTo(running.advance(20).calendarTicks() + 1_000);
    }

    @Test
    void formatsDatesAcrossMonthsAndYears() {
        assertThat(CalendarFormat.date(2_112_000, 8)).isEqualTo("December 1, 1000");
        assertThat(CalendarFormat.season(2_112_000, 8)).isEqualTo("Winter");
        assertThat(CalendarFormat.date(2_304_000, 8)).isEqualTo("January 1, 1001");
        assertThat(CalendarFormat.dateTime(966_500, 8)).isEqualTo("June 1, 1000 06:30");
        assertThat(CalendarFormat.UNKNOWN).isEqualTo("-");
    }
}
