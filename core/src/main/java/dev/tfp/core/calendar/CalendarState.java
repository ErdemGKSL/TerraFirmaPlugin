/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/Calendar.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;

/**
 * The calendar as pure, immutable state: TFC's {@code Calendar} fields plus the advance rule, without
 * threads, scheduling or persistence (those are the Paper glue, M2-CAL-01 follow-up).
 *
 * <p><b>Rate.</b> TFC runs the calendar at {@code 20 / dayLengthMinutes} calendar ticks per player tick
 * (default 24 minutes per in-game day, so 28 800 player ticks = 24 000 calendar ticks). TFC keeps the
 * fractional carry in a {@code float}. Here the carry is an exact integer: {@link #partialNumerator} counts
 * 1/{@code dayLengthMinutes} of a calendar tick. Advancing by {@code n} player ticks is then one integer
 * step, so {@code advance(a).advance(b)} equals {@code advance(a + b)} exactly (lazy and stepped evaluation
 * agree; ARCHITECTURE.md §4.1).
 *
 * @param playerTicks      TFC {@code getTicks()}: player ticks since the world was created, while running
 * @param calendarTicks    TFC {@code getCalendarTicks()}: the in-game date/time, from January 1, 1000
 * @param partialNumerator fractional calendar progress, in units of 1/{@code dayLengthMinutes} calendar ticks,
 *                         always in {@code [0, dayLengthMinutes)}
 * @param dayLengthMinutes the configured in-game day length in minutes (TFC {@code defaultCalendarDayLength}, default 24)
 * @param daysInMonth     the configured month length in days (TFC {@code defaultMonthLength}, default 8)
 * @param running         false while the calendar is arrested (TFC: no players online and the time-stop option on)
 */
public record CalendarState(long playerTicks, long calendarTicks, long partialNumerator, int dayLengthMinutes,
                            int daysInMonth, boolean running) {
    /** TFC {@code defaultCalendarDayLength}. */
    public static final int DEFAULT_DAY_LENGTH_MINUTES = 24;
    /** TFC {@code defaultCalendarStartDay}: days after January 1, 1000 (40 = June 1 with 8-day months). */
    public static final int DEFAULT_START_DAY = 5 * ClockSnapshot.DEFAULT_DAYS_IN_MONTH;

    public CalendarState {
        if (playerTicks < 0) {
            throw new IllegalArgumentException("playerTicks < 0: " + playerTicks);
        }
        CalendarTicks.checkNonNegative(calendarTicks);
        if (dayLengthMinutes < 1) {
            throw new IllegalArgumentException("dayLengthMinutes < 1: " + dayLengthMinutes);
        }
        if (daysInMonth < 1) {
            throw new IllegalArgumentException("daysInMonth < 1: " + daysInMonth);
        }
        if (partialNumerator < 0 || partialNumerator >= dayLengthMinutes) {
            throw new IllegalArgumentException("partialNumerator out of [0, " + dayLengthMinutes + "): " + partialNumerator);
        }
    }

    /**
     * TFC {@code Calendar.resetToDefault()} with explicit configuration: start at {@code startDay} days after
     * January 1, 1000 plus 6 in-game hours, at player tick 0, not running.
     */
    public static CalendarState initial(int startDay, int daysInMonth, int dayLengthMinutes) {
        if (startDay < 0) {
            // TFC accepts -1 in its config and then produces negative calendar ticks; we refuse them.
            throw new IllegalArgumentException("startDay < 0: " + startDay);
        }
        long calendarTicks = (long) startDay * CalendarTicks.CALENDAR_TICKS_IN_DAY + 6L * CalendarTicks.CALENDAR_TICKS_IN_HOUR;
        return new CalendarState(0L, calendarTicks, 0L, dayLengthMinutes, daysInMonth, false);
    }

    /** {@link #initial} with TFC's default configuration. */
    public static CalendarState defaults() {
        return initial(DEFAULT_START_DAY, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, DEFAULT_DAY_LENGTH_MINUTES);
    }

    /** The same state with the running flag set. */
    public CalendarState withRunning(boolean running) {
        return new CalendarState(playerTicks, calendarTicks, partialNumerator, dayLengthMinutes, daysInMonth, running);
    }

    /**
     * Advances by {@code ticks} player ticks, the pure form of TFC {@code advanceCalendarTick} applied
     * {@code ticks} times. While not running the state is returned unchanged (TFC increments neither counter
     * when no players are online), so a caller may pass the elapsed ticks without checking the flag.
     *
     * @param ticks player ticks elapsed, at least 0
     */
    public CalendarState advance(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks < 0: " + ticks);
        }
        if (!running || ticks == 0) {
            return this;
        }
        long total = Math.addExact(partialNumerator, Math.multiplyExact((long) CalendarTicks.PLAYER_TICKS_PER_SECOND, ticks));
        return new CalendarState(
            Math.addExact(playerTicks, ticks),
            Math.addExact(calendarTicks, total / dayLengthMinutes),
            total % dayLengthMinutes,
            dayLengthMinutes,
            daysInMonth,
            true
        );
    }

    /**
     * The calendar tick that is {@code offsetTicks} player ticks from now (TFC {@code getCalendarTickFromOffset}),
     * whether or not the calendar is running. Uses the same arithmetic as {@link #advance}, so
     * {@code calendarTickAfter(n) == advance(n).calendarTicks()} when running.
     */
    public long calendarTickAfter(long offsetTicks) {
        if (offsetTicks < 0) {
            throw new IllegalArgumentException("offsetTicks < 0: " + offsetTicks);
        }
        long total = Math.addExact(partialNumerator, Math.multiplyExact((long) CalendarTicks.PLAYER_TICKS_PER_SECOND, offsetTicks));
        return Math.addExact(calendarTicks, total / dayLengthMinutes);
    }

    /**
     * TFC {@code getFixedCalendarTicksFromTick}: calendar ticks that {@code playerTick} player ticks represent,
     * independent of the current time (no partial carry). For time-independent durations such as config values.
     */
    public long calendarTicksFor(long playerTick) {
        if (playerTick < 0) {
            throw new IllegalArgumentException("playerTick < 0: " + playerTick);
        }
        return Math.multiplyExact((long) CalendarTicks.PLAYER_TICKS_PER_SECOND, playerTick) / dayLengthMinutes;
    }

    /**
     * TFC {@code Calendar.setMonthLength}: changes the month length and keeps the month, the time of day and
     * the position within the month. The new calendar tick is recomputed as
     * {@code months * newMonthTicks + floor(fractionOfMonth * newDays) * 1000 + timeOfDay}. The month index
     * and the in-day time are preserved; the day index is multiplied by one <em>hour</em> (1000 ticks), not one
     * day, exactly as TFC does (see the ledger note). The fraction is computed exactly in integers, not in float.
     */
    public CalendarState withDaysInMonth(int newDaysInMonth) {
        if (newDaysInMonth < 1) {
            throw new IllegalArgumentException("daysInMonth < 1: " + newDaysInMonth);
        }
        long oldMonthTicks = CalendarTicks.ticksInMonth(daysInMonth);
        long baseMonths = calendarTicks / oldMonthTicks;
        long withinMonth = calendarTicks % oldMonthTicks;
        long baseDayTime = calendarTicks % CalendarTicks.CALENDAR_TICKS_IN_DAY;
        long dayIndex = Math.multiplyExact(withinMonth, (long) newDaysInMonth) / oldMonthTicks; // floor(fractionOfMonth * newDaysInMonth)
        long next = baseMonths * CalendarTicks.ticksInMonth(newDaysInMonth)
            + dayIndex * CalendarTicks.CALENDAR_TICKS_IN_HOUR
            + baseDayTime;
        return new CalendarState(playerTicks, next, partialNumerator, dayLengthMinutes, newDaysInMonth, running);
    }

    /** Publishes this state to the global clock as the next snapshot (sequence assigned by the holder). */
    public ClockSnapshot publishTo(GlobalClockHolder holder) {
        return holder.publish(playerTicks, calendarTicks, daysInMonth, running);
    }

    /** The in-game month of this state. */
    public Month month() {
        return CalendarTicks.month(calendarTicks, daysInMonth);
    }

    /** The in-game season of this state. */
    public Season season() {
        return month().season();
    }
}
