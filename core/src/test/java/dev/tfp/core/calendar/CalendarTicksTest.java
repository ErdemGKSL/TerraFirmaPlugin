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
 * Golden values for {@link CalendarTicks}. TFC's pinned checkout has no unit tests for its calendar, so the
 * expected numbers are TFC's own constants (ICalendar) and config defaults, evaluated by hand from TFC's formulas.
 * Default month length 8, so a month is 192 000 calendar ticks and a year 2 304 000.
 */
class CalendarTicksTest {
    @Test
    void tfcConstants() {
        assertThat(CalendarTicks.CALENDAR_TICKS_IN_HOUR).isEqualTo(1000);
        assertThat(CalendarTicks.CALENDAR_TICKS_IN_DAY).isEqualTo(24_000);
        assertThat(CalendarTicks.HOURS_IN_DAY).isEqualTo(24);
        assertThat(CalendarTicks.MONTHS_IN_YEAR).isEqualTo(12);
        assertThat(CalendarTicks.FIRST_YEAR).isEqualTo(1000);
        assertThat(CalendarTicks.ticksInMonth(8)).isEqualTo(192_000L);
        assertThat(CalendarTicks.ticksInYear(8)).isEqualTo(2_304_000L);
        assertThat(CalendarTicks.ticksInMonth(30)).isEqualTo(720_000L);
    }

    @Test
    void epochIsJanuaryFirstOfYearOneThousand() {
        assertThat(CalendarTicks.year(0, 8)).isEqualTo(1000);
        assertThat(CalendarTicks.month(0, 8)).isEqualTo(Month.JANUARY);
        assertThat(CalendarTicks.dayOfMonth(0, 8)).isEqualTo(1);
        assertThat(CalendarTicks.hourOfDay(0)).isZero();
        assertThat(CalendarTicks.minuteOfHour(0)).isZero();
        assertThat(CalendarTicks.day(0)).isEqualTo(Day.MONDAY);
    }

    @Test
    void monthBoundaryWithDefaultMonthLength() {
        // Last tick of January (day 8, 23:59:59.x) and the first tick of February.
        assertThat(CalendarTicks.month(191_999, 8)).isEqualTo(Month.JANUARY);
        assertThat(CalendarTicks.dayOfMonth(191_999, 8)).isEqualTo(8);
        assertThat(CalendarTicks.hourOfDay(191_999)).isEqualTo(23);
        assertThat(CalendarTicks.minuteOfHour(191_999)).isEqualTo(59);

        assertThat(CalendarTicks.month(192_000, 8)).isEqualTo(Month.FEBRUARY);
        assertThat(CalendarTicks.dayOfMonth(192_000, 8)).isEqualTo(1);
        assertThat(CalendarTicks.hourOfDay(192_000)).isZero();
    }

    @Test
    void yearBoundaryWithDefaultMonthLength() {
        assertThat(CalendarTicks.year(2_303_999, 8)).isEqualTo(1000);
        assertThat(CalendarTicks.month(2_303_999, 8)).isEqualTo(Month.DECEMBER);
        assertThat(CalendarTicks.dayOfMonth(2_303_999, 8)).isEqualTo(8);

        assertThat(CalendarTicks.year(2_304_000, 8)).isEqualTo(1001);
        assertThat(CalendarTicks.month(2_304_000, 8)).isEqualTo(Month.JANUARY);
        assertThat(CalendarTicks.dayOfMonth(2_304_000, 8)).isEqualTo(1);
    }

    @Test
    void daysInMonthConfigChangesMonthAndYearBoundaries() {
        // With 30-day months the same tick is still in January, and February starts at 720 000.
        assertThat(CalendarTicks.month(192_000, 30)).isEqualTo(Month.JANUARY);
        assertThat(CalendarTicks.dayOfMonth(192_000, 30)).isEqualTo(9);
        assertThat(CalendarTicks.month(720_000, 30)).isEqualTo(Month.FEBRUARY);
        assertThat(CalendarTicks.dayOfMonth(720_000, 30)).isEqualTo(1);
        // A year is 12 * 720 000 = 8 640 000 ticks with 30-day months.
        assertThat(CalendarTicks.year(8_639_999, 30)).isEqualTo(1000);
        assertThat(CalendarTicks.year(8_640_000, 30)).isEqualTo(1001);
    }

    @Test
    void minuteIsFloorOfSixtyPerThousandTicks() {
        assertThat(CalendarTicks.minuteOfHour(16)).isZero();    // 0.96 minutes
        assertThat(CalendarTicks.minuteOfHour(17)).isEqualTo(1); // 1.02 minutes
        assertThat(CalendarTicks.minuteOfHour(999)).isEqualTo(59);
        assertThat(CalendarTicks.hourOfDay(1000)).isEqualTo(1);
    }

    @Test
    void weekdayCountsWholeDaysFromTheEpoch() {
        assertThat(CalendarTicks.day(23_999)).isEqualTo(Day.MONDAY);     // still day 0
        assertThat(CalendarTicks.day(24_000)).isEqualTo(Day.TUESDAY);    // day 1
        assertThat(CalendarTicks.day(6 * 24_000L)).isEqualTo(Day.SUNDAY); // day 6
        assertThat(CalendarTicks.day(7 * 24_000L)).isEqualTo(Day.MONDAY); // day 7 wraps
        assertThat(Day.valueOf(-1)).isEqualTo(Day.MONDAY);
        assertThat(Day.valueOf(7)).isEqualTo(Day.SUNDAY);
    }

    @Test
    void fractionsAreProgressThroughTheUnit() {
        assertThat(CalendarTicks.fractionOfDay(12_000)).isEqualTo(0.5);
        assertThat(CalendarTicks.fractionOfMonth(96_000, 8)).isEqualTo(0.5);   // halfway through a month
        assertThat(CalendarTicks.fractionOfMonth(192_000 + 48_000, 8)).isEqualTo(0.25);
        assertThat(CalendarTicks.fractionOfYear(1_152_000, 8)).isEqualTo(0.5); // halfway through a year
    }

    @Test
    void dateRoundTripsThroughOf() {
        // Start of June 1, 1000 at 06:00 (TFC's default start): 40 days + 6 hours.
        long start = CalendarTicks.of(1000, Month.JUNE, 1, 6, 0, 8);
        assertThat(start).isEqualTo(40 * 24_000L + 6_000L).isEqualTo(966_000L);

        // Every (month, day, hour, minute) round trips for a sample of ticks in year 1000 and 1001.
        for (long t : new long[] {0, 1, 17, 999, 1_000, 191_999, 192_000, 500_123, 2_303_999, 2_304_000, 9_999_123}) {
            long fromFields = CalendarTicks.of(
                CalendarTicks.year(t, 8), CalendarTicks.month(t, 8), CalendarTicks.dayOfMonth(t, 8),
                CalendarTicks.hourOfDay(t), CalendarTicks.minuteOfHour(t), 8);
            // Lands on the first tick of the same minute: never after t, and within one minute of it.
            assertThat(fromFields).isLessThanOrEqualTo(t);
            assertThat(t - fromFields).isLessThan(18);
            assertThat(CalendarTicks.minuteOfHour(fromFields)).isEqualTo(CalendarTicks.minuteOfHour(t));
            assertThat(CalendarTicks.dayOfMonth(fromFields, 8)).isEqualTo(CalendarTicks.dayOfMonth(t, 8));
            assertThat(CalendarTicks.month(fromFields, 8)).isEqualTo(CalendarTicks.month(t, 8));
        }
    }

    @Test
    void minuteStartsAreExactRoundTrips() {
        for (int minute = 0; minute < 60; minute++) {
            long t = CalendarTicks.of(1000, Month.MARCH, 3, 14, minute, 8);
            assertThat(CalendarTicks.minuteOfHour(t)).isEqualTo(minute);
            assertThat(CalendarTicks.hourOfDay(t)).isEqualTo(14);
            assertThat(CalendarTicks.dayOfMonth(t, 8)).isEqualTo(3);
            if (minute > 0) {
                assertThat(CalendarTicks.minuteOfHour(t - 1)).isEqualTo(minute - 1);
            }
        }
    }

    @Test
    void rejectsInvalidFieldsAndNegativeTicks() {
        assertThatThrownBy(() -> CalendarTicks.of(999, Month.JANUARY, 1, 0, 0, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarTicks.of(1000, Month.JANUARY, 9, 0, 0, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarTicks.of(1000, Month.JANUARY, 1, 24, 0, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarTicks.of(1000, Month.JANUARY, 1, 0, 60, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarTicks.hourOfDay(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monthEnumMatchesTfcSeasonsAndTemperatures() {
        assertThat(Month.JANUARY.season()).isEqualTo(Season.WINTER);
        assertThat(Month.FEBRUARY.season()).isEqualTo(Season.WINTER);
        assertThat(Month.MARCH.season()).isEqualTo(Season.SPRING);
        assertThat(Month.MAY.season()).isEqualTo(Season.SPRING);
        assertThat(Month.JUNE.season()).isEqualTo(Season.SUMMER);
        assertThat(Month.AUGUST.season()).isEqualTo(Season.SUMMER);
        assertThat(Month.SEPTEMBER.season()).isEqualTo(Season.FALL);
        assertThat(Month.NOVEMBER.season()).isEqualTo(Season.FALL);
        assertThat(Month.DECEMBER.season()).isEqualTo(Season.WINTER);

        assertThat(Month.JULY.temperatureModifier()).isEqualTo(1f);
        assertThat(Month.JANUARY.temperatureModifier()).isEqualTo(-1f);
        assertThat(Month.JUNE.temperatureModifier()).isEqualTo(0.866f);

        assertThat(Month.valueOf(12)).isEqualTo(Month.JANUARY);
        assertThat(Month.valueOf(-3)).isEqualTo(Month.JANUARY);
        assertThat(Month.DECEMBER.next()).isEqualTo(Month.JANUARY);
        assertThat(Month.JANUARY.opposite()).isEqualTo(Month.JULY);
        assertThat(Month.DECEMBER.opposite()).isEqualTo(Month.JUNE);
        assertThat(Month.SEPTEMBER.opposite()).isEqualTo(Month.MARCH);
    }

    @Test
    void seasonCyclesAndOpposes() {
        assertThat(Season.WINTER.next()).isEqualTo(Season.SPRING);
        assertThat(Season.SPRING.previous()).isEqualTo(Season.WINTER);
        assertThat(Season.SUMMER.opposite()).isEqualTo(Season.WINTER);
        assertThat(Season.FALL.opposite()).isEqualTo(Season.SPRING);
    }
}
