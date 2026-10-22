/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/TimeDeltaTooltipStyle.java @ c3b4d9536
 * (with getTimeDelta and getTimeAndDate of src/main/java/net/dries007/tfc/util/calendar/ICalendar.java)
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.Locale;

/**
 * TFC's {@code TimeDeltaTooltipStyle} (the {@code timeDeltaTooltipStyle} client option) and the two calendar texts
 * tooltips use: a duration ({@link #timeDelta}, TFC {@code ICalendar.getTimeDelta}) and an exact moment
 * ({@link #timeAndDate}, TFC {@code ICalendar.getTimeAndDate}). One server-wide choice (NOT-PARITY NP-CLIENT-04);
 * TFC's default is {@link #DAYS_MONTHS_YEARS}.
 */
public enum TimeDeltaStyle {
    /** Longer than a year: years, months and days. */
    DAYS_MONTHS_YEARS,
    /** Longer than a month: months and days, never years. */
    DAYS_MONTHS,
    /** Longer than a day: days only. */
    DAYS;

    /**
     * TFC {@code ICalendar.getTimeDelta(calendarTick, daysInMonth)}: a length of time with a precision that depends on
     * it. Under an hour {@code 00:mm}; under a day {@code h:mm}; then days, months and days, or years, months and days
     * as this style allows. A negative length reads as 0.
     */
    public String timeDelta(long calendarTicks, int daysInMonth, String locale, TooltipText text) {
        long t = Math.max(0L, calendarTicks);
        long hours = t / CalendarTicks.CALENDAR_TICKS_IN_HOUR;
        String minutes = String.format(Locale.ROOT, "%02d", CalendarTicks.minuteOfHour(t));
        if (hours < 1) {
            return text.get(locale, "tooltip.time_delta.hours_minutes", "00", minutes);
        }
        long days = CalendarTicks.totalDays(t);
        if (days < 1) {
            return text.get(locale, "tooltip.time_delta.hours_minutes", Long.toString(hours), minutes);
        }
        long months = t / CalendarTicks.ticksInMonth(daysInMonth);
        if (months < 1 || this == DAYS) {
            return text.get(locale, "tooltip.time_delta.days", Long.toString(days));
        }
        long years = CalendarTicks.year(t, daysInMonth) - CalendarTicks.FIRST_YEAR;
        if (years < 1 || this == DAYS_MONTHS) {
            return text.get(locale, "tooltip.time_delta.months_days", Long.toString(months),
                    Long.toString(days % daysInMonth));
        }
        return text.get(locale, "tooltip.time_delta.years_months_days", Long.toString(years),
                Long.toString(months % CalendarTicks.MONTHS_IN_YEAR), Long.toString(days % daysInMonth));
    }

    /**
     * TFC {@code ICalendar.getTimeAndDate(calendarTick, daysInMonth)}: {@code "h:mm Month d, yyyy"} with the month name
     * translated. A negative tick reads as the first moment of the calendar.
     */
    public static String timeAndDate(long calendarTicks, int daysInMonth, String locale, TooltipText text) {
        long t = Math.max(0L, calendarTicks);
        String time = String.format(Locale.ROOT, "%d:%02d", CalendarTicks.hourOfDay(t), CalendarTicks.minuteOfHour(t));
        String month = text.get(locale, "tooltip.month." + CalendarTicks.month(t, daysInMonth).name().toLowerCase(Locale.ROOT));
        String year = String.format(Locale.ROOT, "%04d", CalendarTicks.year(t, daysInMonth));
        return text.get(locale, "tooltip.calendar.time_and_date", time, month,
                Integer.toString(CalendarTicks.dayOfMonth(t, daysInMonth)), year);
    }
}
