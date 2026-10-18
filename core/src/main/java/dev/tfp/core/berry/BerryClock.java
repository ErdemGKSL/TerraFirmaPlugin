/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/Calendar.java @ c3b4d9536 (getCalendarTickFromOffset, calendarTickRate of a new
 *       world),
 *       src/main/java/net/dries007/tfc/util/calendar/ICalendar.java @ c3b4d9536 (getMonthOfYear, getHemispheralCalendarMonthOfYear)
 */
package dev.tfp.core.berry;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;

/**
 * The calendar as seen from one player tick: the calendar tick at {@code playerTick}, the days in a month and the hemisphere. TFC
 * maps a player-tick offset to a calendar tick with {@code calendarTickRate} (calendar ticks per player tick), truncated to a whole
 * calendar tick; that mapping is {@link #calendarAt}. The partial-tick term of TFC is taken as zero.
 *
 * <p>A clock is anchored at one player tick. Ask {@link #at} for the clock anchored at another tick, the same way TFC evaluates the
 * simulated ticks of a catch-up relative to the current tick.
 */
public record BerryClock(long playerTick, long calendarTick, int daysInMonth, boolean northern) {
    /** TFC {@code calendarTickRate} of a new world: {@code 20f / defaultCalendarDayLength} with the default 24 minutes (float32). */
    public static final double CALENDAR_TICK_RATE = 20f / 24f;

    public BerryClock {
        if (calendarTick < 0) {
            throw new IllegalArgumentException("calendarTick must be >= 0: " + calendarTick);
        }
        if (daysInMonth < 1) {
            throw new IllegalArgumentException("daysInMonth must be >= 1: " + daysInMonth);
        }
    }

    /** TFC {@code getCalendarTickFromOffset}: the calendar tick at {@code tick}, which may be before or after the anchor. */
    public long calendarAt(long tick) {
        return calendarTick + (long) (CALENDAR_TICK_RATE * (tick - playerTick));
    }

    /** The same clock, anchored at {@code tick}. */
    public BerryClock at(long tick) {
        return new BerryClock(tick, calendarAt(tick), daysInMonth, northern);
    }

    /**
     * TFC {@code getHemispheralCalendarMonthOfYear} for a calendar tick: the month of the calendar, opposite in the southern hemisphere.
     * Uses Java's long division and remainder as TFC does, so a tick before the calendar's zero falls back to January.
     */
    public Month hemisphericMonth(long calendarTickAt) {
        long ticksInMonth = (long) CalendarTicks.CALENDAR_TICKS_IN_DAY * daysInMonth;
        Month month = Month.valueOf((int) ((calendarTickAt / ticksInMonth) % CalendarTicks.MONTHS_IN_YEAR));
        return northern ? month : month.opposite();
    }
}
