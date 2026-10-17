/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ICalendar.java @ c3b4d9536
 */
package dev.tfp.core.climate;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;

/**
 * The position of a calendar tick inside the day, month and year, as the climate model reads it. The fractions are
 * {@code float}, computed as TFC does, so the sun and temperature inputs match TFC's exactly.
 *
 * @param totalDays      whole days since January 1, 1000
 * @param month          the month of the display year
 * @param fractionOfDay  progress through the day in [0, 1), 0 is midnight and 0.5 is noon
 * @param fractionOfMonth progress through the month in [0, 1)
 * @param fractionOfYear progress through the year in [0, 1), January 1 is 0
 */
public record CalendarPhase(long totalDays, Month month, float fractionOfDay, float fractionOfMonth, float fractionOfYear) {
    /** Computes the phase for {@code calendarTicks} with {@code daysInMonth} days per month (TFC's config, default 8). */
    public static CalendarPhase of(long calendarTicks, int daysInMonth) {
        if (daysInMonth <= 0) {
            throw new IllegalArgumentException("daysInMonth must be positive: " + daysInMonth);
        }
        final long day = CalendarTicks.CALENDAR_TICKS_IN_DAY;
        final long ticksInMonth = CalendarTicks.ticksInMonth(daysInMonth);
        final long ticksInYear = CalendarTicks.ticksInYear(daysInMonth);
        return new CalendarPhase(
            CalendarTicks.totalDays(calendarTicks),
            CalendarTicks.month(calendarTicks, daysInMonth),
            (float) (calendarTicks % day) / day,
            (float) (calendarTicks % ticksInMonth) / ticksInMonth,
            (float) (calendarTicks % ticksInYear) / ticksInYear
        );
    }

    /** The season of this phase's month. */
    public Season season() {
        return month.season();
    }
}
