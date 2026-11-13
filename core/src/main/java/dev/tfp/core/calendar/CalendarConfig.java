/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/CommonConfig.java @ c3b4d9536 (calendar section),
 * src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (enableTimeStopWhenServerEmpty)
 */
package dev.tfp.core.calendar;

import dev.tfp.core.clock.ClockSnapshot;
import java.util.List;
import java.util.Map;

/**
 * TFC's calendar configuration (PAR-CLIM-01), under TFC's key names. The first three only shape a <em>new</em> calendar
 * (TFC {@code resetToDefault}); an existing world keeps its stored month and day length, as in TFC.
 *
 * @param defaultMonthLength            days in a month for new worlds (TFC default 8, at least 1)
 * @param defaultCalendarStartDay       days after January 1, 1000 a new world starts at (TFC default 40 = June 1). TFC
 *                                      accepts -1, which gives negative calendar ticks; we refuse it (ledger divergence)
 * @param defaultCalendarDayLength      minutes of real time per in-game day for new worlds (TFC default 24, at least 1)
 * @param enableTimeStopWhenServerEmpty stop the calendar, and the sun, while no player is online (TFC default true)
 */
public record CalendarConfig(int defaultMonthLength, int defaultCalendarStartDay, int defaultCalendarDayLength,
                             boolean enableTimeStopWhenServerEmpty) {
    public static final String MONTH_LENGTH_KEY = "defaultMonthLength";
    public static final String START_DAY_KEY = "defaultCalendarStartDay";
    public static final String DAY_LENGTH_KEY = "defaultCalendarDayLength";
    public static final String TIME_STOP_KEY = "enableTimeStopWhenServerEmpty";

    /** TFC's defaults. */
    public static final CalendarConfig DEFAULTS = new CalendarConfig(ClockSnapshot.DEFAULT_DAYS_IN_MONTH,
            CalendarState.DEFAULT_START_DAY, CalendarState.DEFAULT_DAY_LENGTH_MINUTES, true);

    public CalendarConfig {
        if (defaultMonthLength < 1) {
            throw new IllegalArgumentException(MONTH_LENGTH_KEY + " < 1: " + defaultMonthLength);
        }
        if (defaultCalendarStartDay < 0) {
            throw new IllegalArgumentException(START_DAY_KEY + " < 0: " + defaultCalendarStartDay);
        }
        if (defaultCalendarDayLength < 1) {
            throw new IllegalArgumentException(DAY_LENGTH_KEY + " < 1: " + defaultCalendarDayLength);
        }
    }

    /** The calendar a new world starts with (TFC {@code Calendar.resetToDefault}). */
    public CalendarState initialState() {
        return CalendarState.initial(defaultCalendarStartDay, defaultMonthLength, defaultCalendarDayLength);
    }

    /**
     * Reads the four keys from a parsed YAML section. A missing key takes TFC's default; a wrong or out-of-range value
     * takes the default too and adds a line to {@code warnings}.
     */
    public static CalendarConfig parse(Map<String, ?> section, List<String> warnings) {
        return new CalendarConfig(
                intKey(section, MONTH_LENGTH_KEY, DEFAULTS.defaultMonthLength, 1, warnings),
                intKey(section, START_DAY_KEY, DEFAULTS.defaultCalendarStartDay, 0, warnings),
                intKey(section, DAY_LENGTH_KEY, DEFAULTS.defaultCalendarDayLength, 1, warnings),
                boolKey(section, TIME_STOP_KEY, DEFAULTS.enableTimeStopWhenServerEmpty, warnings));
    }

    private static int intKey(Map<String, ?> section, String key, int fallback, int min, List<String> warnings) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                && n.longValue() >= min && n.longValue() <= Integer.MAX_VALUE) {
            return n.intValue();
        }
        warnings.add(key + ": expected a whole number from " + min + ", got '" + raw + "'; using " + fallback);
        return fallback;
    }

    private static boolean boolKey(Map<String, ?> section, String key, boolean fallback, List<String> warnings) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        warnings.add(key + ": expected true or false, got '" + raw + "'; using " + fallback);
        return fallback;
    }
}
