/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TimeCommand.java @ c3b4d9536
 * (season names: src/main/resources/assets/tfc/lang/en_us.json, src/main/java/net/dries007/tfc/util/calendar/Month.java
 * and ICalendar.java @ c3b4d9536)
 */
package dev.tfp.core.calendar;

import java.util.List;

/**
 * Plain English text for the calendar, for the HUD placeholders and the {@code /tfp time} replies. Presentation only:
 * TFC's translated names are not ported (ledger: calendar row), so the strings are English on every client.
 */
public final class CalendarFormat {
    /** Shown while the calendar has not been published yet. */
    public static final String UNKNOWN = "-";

    private CalendarFormat() {
    }

    /** {@code "June 1, 1000"}. */
    public static String date(long calendarTicks, int daysInMonth) {
        return title(CalendarTicks.month(calendarTicks, daysInMonth).name()) + " "
            + CalendarTicks.dayOfMonth(calendarTicks, daysInMonth) + ", " + CalendarTicks.year(calendarTicks, daysInMonth);
    }

    /** {@code "June 1, 1000 06:30"}. */
    public static String dateTime(long calendarTicks, int daysInMonth) {
        return date(calendarTicks, daysInMonth) + String.format(" %02d:%02d",
            CalendarTicks.hourOfDay(calendarTicks), CalendarTicks.minuteOfHour(calendarTicks));
    }

    /**
     * TFC's 12 season names, {@code Month.Style.SEASON} ({@code tfc.enum.season.<month>} in TFC's en_us.json), indexed by
     * {@link Month#ordinal()} of the hemisphere-adjusted month.
     */
    public static final List<String> SEASON_NAMES = List.of("Winter", "Late Winter", "Early Spring", "Spring",
        "Late Spring", "Early Summer", "Summer", "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter");

    /**
     * The season TFC shows ({@code CalendarScreen}: {@code getHemispheralCalendarMonthOfYear(north)} in
     * {@code Month.Style.SEASON}): the month's season name, of the opposite month in the southern hemisphere.
     * {@code "Early Autumn"} for September in the north, {@code "Early Spring"} in the south.
     */
    public static String seasonName(long calendarTicks, int daysInMonth, boolean northernHemisphere) {
        return seasonName(CalendarTicks.month(calendarTicks, daysInMonth), northernHemisphere);
    }

    /** {@link #seasonName(long, int, boolean)} of a calendar month. */
    public static String seasonName(Month month, boolean northernHemisphere) {
        Month m = northernHemisphere ? month : month.opposite();
        return SEASON_NAMES.get(m.ordinal());
    }

    /**
     * The 4-value {@link Season} of the (northern) month in title case, {@code "Summer"}. TFC has no lang entry for its
     * {@code Season} enum and never shows it; the HUD uses {@link #seasonName(long, int, boolean)}.
     */
    public static String season(long calendarTicks, int daysInMonth) {
        return title(CalendarTicks.month(calendarTicks, daysInMonth).season().name());
    }

    private static String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
