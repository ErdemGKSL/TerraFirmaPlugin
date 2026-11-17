/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java @ c3b4d9536 (setCalendarTickRate)
 * Also: src/main/java/net/dries007/tfc/common/commands/TimeCommand.java @ c3b4d9536 (setDayLength)
 */
package dev.tfp.core.calendar;

/**
 * PAR-CLIM-13: TFC's calendar tick rate, the number of calendar ticks per player tick. TFC stores it as a float,
 * {@code 20 / minutes} for a day of {@code minutes} real minutes, and {@code 0} when the day length is disabled. Here the
 * day length in minutes is stored instead ({@link CalendarState#dayLengthMinutes()}, {@link CalendarState#DISABLED_DAY_LENGTH}
 * for rate 0), so the advance is exact integer arithmetic; {@link #tfcRate} gives TFC's float for comparison.
 */
public final class CalendarRate {
    /** TFC {@code dayLength vanilla}: vanilla's 20-minute day. */
    public static final int VANILLA_MINUTES = 20;
    /** TFC {@code dayLength default}: 24 minutes. */
    public static final int DEFAULT_MINUTES = CalendarState.DEFAULT_DAY_LENGTH_MINUTES;
    /** TFC {@code dayLength realtime}: 24 * 60 minutes. */
    public static final int REALTIME_MINUTES = 24 * 60;
    /** TFC {@code dayLength disabled}: rate 0. */
    public static final int DISABLED = CalendarState.DISABLED_DAY_LENGTH;

    private CalendarRate() {
    }

    /**
     * TFC {@code TimeCommand.setDayLength}: {@code calendarTickRate = minutes == -1 ? 0 : 20f / minutes}, with
     * {@link #DISABLED} for TFC's -1.
     */
    public static float tfcRate(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes < 0: " + minutes);
        }
        return minutes == DISABLED ? 0f : 20f / minutes;
    }

    /** The rate of a state, in TFC's float form. */
    public static float tfcRate(CalendarState state) {
        return tfcRate(state.dayLengthMinutes());
    }

    /** TFC's {@code setCalendarTickRate} on a state: the day length changes, nothing else moves. */
    public static CalendarState apply(CalendarState state, int minutes) {
        return state.withDayLength(minutes);
    }
}
