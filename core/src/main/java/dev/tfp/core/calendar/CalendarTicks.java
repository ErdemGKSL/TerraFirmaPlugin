/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ICalendar.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

/**
 * Pure arithmetic on <b>calendar ticks</b>: the in-game clock counted from January 1, 1000 (TFC
 * {@code ICalendar.CALENDAR_TICKS_*} and the static field helpers). A calendar tick is not a player tick:
 * one in-game hour is {@value #CALENDAR_TICKS_IN_HOUR} calendar ticks, one day is {@value #CALENDAR_TICKS_IN_DAY}.
 *
 * <p>A year is {@value #MONTHS_IN_YEAR} months and a month is {@code daysInMonth} days, so the month and
 * year lengths depend on the configured month length. Every function takes {@code daysInMonth} as a
 * parameter; nothing here reads configuration. Values before January 1, 1000 are rejected (TFC does not
 * guard them and produces meaningless fields).
 */
public final class CalendarTicks {
    /** TFC {@code ICalendar.HOURS_IN_DAY}. */
    public static final int HOURS_IN_DAY = 24;
    /** TFC {@code ICalendar.MONTHS_IN_YEAR}. */
    public static final int MONTHS_IN_YEAR = 12;
    /** TFC {@code ICalendar.CALENDAR_TICKS_IN_HOUR}. */
    public static final int CALENDAR_TICKS_IN_HOUR = 1000;
    /** TFC {@code ICalendar.CALENDAR_TICKS_IN_DAY}. */
    public static final int CALENDAR_TICKS_IN_DAY = CALENDAR_TICKS_IN_HOUR * HOURS_IN_DAY;
    /** The first displayed year (TFC {@code getCalendarYear} base). */
    public static final int FIRST_YEAR = 1000;
    /** Server ticks per second (TFC uses 20 in {@code Calendar.resetToDefault}). */
    public static final int PLAYER_TICKS_PER_SECOND = 20;

    private CalendarTicks() {
    }

    /** Calendar ticks in one month of {@code daysInMonth} days. */
    public static long ticksInMonth(int daysInMonth) {
        return (long) CALENDAR_TICKS_IN_DAY * daysInMonth;
    }

    /** Calendar ticks in one year (12 months). */
    public static long ticksInYear(int daysInMonth) {
        return MONTHS_IN_YEAR * ticksInMonth(daysInMonth);
    }

    /** TFC {@code getHourOfDay}: 0-23. */
    public static int hourOfDay(long calendarTicks) {
        checkNonNegative(calendarTicks);
        return (int) ((calendarTicks / CALENDAR_TICKS_IN_HOUR) % HOURS_IN_DAY);
    }

    /**
     * TFC {@code getMinuteOfHour}: 0-59. TFC computes {@code (int) (60f * t / 1000)} in float; the integer
     * form here gives the same value for every input.
     */
    public static int minuteOfHour(long calendarTicks) {
        checkNonNegative(calendarTicks);
        return (int) ((calendarTicks % CALENDAR_TICKS_IN_HOUR) * 60 / CALENDAR_TICKS_IN_HOUR);
    }

    /** TFC {@code getCalendarDayOfMonth}: 1-based, 1 to {@code daysInMonth}. */
    public static int dayOfMonth(long calendarTicks, int daysInMonth) {
        checkNonNegative(calendarTicks);
        return 1 + (int) ((calendarTicks / CALENDAR_TICKS_IN_DAY) % daysInMonth);
    }

    /** TFC {@code getAbsoluteCalendarMonthOfYear}: the month of the display year. */
    public static Month month(long calendarTicks, int daysInMonth) {
        checkNonNegative(calendarTicks);
        return Month.valueOf((int) ((calendarTicks / ticksInMonth(daysInMonth)) % MONTHS_IN_YEAR));
    }

    /** TFC {@code getCalendarYear}: the display year, starting at {@value #FIRST_YEAR}. */
    public static long year(long calendarTicks, int daysInMonth) {
        checkNonNegative(calendarTicks);
        return FIRST_YEAR + calendarTicks / ticksInYear(daysInMonth);
    }

    /** TFC {@code getTotalCalendarDays}: whole days since January 1, 1000. */
    public static long totalDays(long calendarTicks) {
        checkNonNegative(calendarTicks);
        return calendarTicks / CALENDAR_TICKS_IN_DAY;
    }

    /** The weekday of a calendar tick (see {@link Day#ofTotalDays}). */
    public static Day day(long calendarTicks) {
        return Day.ofTotalDays(totalDays(calendarTicks));
    }

    /** TFC {@code getFractionOfDay}: progress through the day in [0, 1). 0 is midnight, 0.5 is noon. */
    public static double fractionOfDay(long calendarTicks) {
        checkNonNegative(calendarTicks);
        return (double) (calendarTicks % CALENDAR_TICKS_IN_DAY) / CALENDAR_TICKS_IN_DAY;
    }

    /** TFC {@code getFractionOfMonth}: progress through the month in [0, 1). */
    public static double fractionOfMonth(long calendarTicks, int daysInMonth) {
        checkNonNegative(calendarTicks);
        long ticks = ticksInMonth(daysInMonth);
        return (double) (calendarTicks % ticks) / ticks;
    }

    /** TFC {@code getFractionOfYear}: progress through the year in [0, 1), where January 1 is 0. */
    public static double fractionOfYear(long calendarTicks, int daysInMonth) {
        checkNonNegative(calendarTicks);
        long ticks = ticksInYear(daysInMonth);
        return (double) (calendarTicks % ticks) / ticks;
    }

    /**
     * The calendar tick at which a date and time starts. The fields of {@code t} map back to the same
     * date and time through this function, and {@code of(...)} returns the first tick of its minute, so
     * {@code of(fields of t) <= t} with equality when {@code t} is the start of its minute. The minute
     * starts on the first calendar tick whose {@link #minuteOfHour} is that minute.
     *
     * @param year       display year, at least {@value #FIRST_YEAR}
     * @param month      the month of the year
     * @param dayOfMonth 1-based day, 1 to {@code daysInMonth}
     * @param hour       0-23
     * @param minute     0-59
     */
    public static long of(long year, Month month, int dayOfMonth, int hour, int minute, int daysInMonth) {
        if (year < FIRST_YEAR) {
            throw new IllegalArgumentException("year < " + FIRST_YEAR + ": " + year);
        }
        if (dayOfMonth < 1 || dayOfMonth > daysInMonth) {
            throw new IllegalArgumentException("dayOfMonth out of 1.." + daysInMonth + ": " + dayOfMonth);
        }
        if (hour < 0 || hour >= HOURS_IN_DAY) {
            throw new IllegalArgumentException("hour out of 0.." + (HOURS_IN_DAY - 1) + ": " + hour);
        }
        if (minute < 0 || minute >= 60) {
            throw new IllegalArgumentException("minute out of 0..59: " + minute);
        }
        long ticks = (year - FIRST_YEAR) * ticksInYear(daysInMonth)
            + month.ordinal() * ticksInMonth(daysInMonth)
            + (long) (dayOfMonth - 1) * CALENDAR_TICKS_IN_DAY
            + (long) hour * CALENDAR_TICKS_IN_HOUR;
        // Smallest t in the hour with minuteOfHour(t) == minute, i.e. t % 1000 * 60 >= minute * 1000.
        return ticks + ceilDiv((long) minute * CALENDAR_TICKS_IN_HOUR, 60);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    static void checkNonNegative(long calendarTicks) {
        if (calendarTicks < 0) {
            throw new IllegalArgumentException("calendar ticks before January 1, 1000: " + calendarTicks);
        }
    }
}
