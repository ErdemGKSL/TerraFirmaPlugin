/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/Day.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

/**
 * The day of the week. The week is seven whole calendar days counted from January 1, 1000, so the
 * weekday of a day is its total day count modulo 7 (TFC {@code Day.getDayName}). TFC's birthday
 * table and its translated names are presentation and are not ported.
 */
public enum Day {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    private static final Day[] VALUES = values();

    /**
     * TFC {@code Day.valueOf(int)}: negative ids clamp to MONDAY and ids past the end clamp to SUNDAY.
     */
    public static Day valueOf(int i) {
        return i < 0 ? MONDAY : i >= VALUES.length ? SUNDAY : VALUES[i];
    }

    /** The weekday of a total day count (days since January 1, 1000), as TFC computes it. */
    public static Day ofTotalDays(long totalDays) {
        return valueOf((int) (totalDays % 7));
    }
}
