/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TimeCommand.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

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

    /** {@code "Summer"}. */
    public static String season(long calendarTicks, int daysInMonth) {
        return title(CalendarTicks.month(calendarTicks, daysInMonth).season().name());
    }

    private static String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
