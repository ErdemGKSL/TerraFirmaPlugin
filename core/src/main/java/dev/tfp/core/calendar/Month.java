/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/Month.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

/**
 * The twelve calendar months, in calendar order (January = 0). The temperature modifiers are the
 * Northern Hemisphere values from TFC; the Southern Hemisphere uses {@link #opposite()} in the climate model.
 * The display names and translation keys of TFC are presentation and are not ported here.
 */
public enum Month {
    // Temperature modifiers are defined for the Northern Hemisphere
    // Southern Hemisphere temperatures are calculated in the climate model
    JANUARY(-1f, Season.WINTER),
    FEBRUARY(-0.866f, Season.WINTER),
    MARCH(-0.5f, Season.SPRING),
    APRIL(0f, Season.SPRING),
    MAY(0.5f, Season.SPRING),
    JUNE(0.866f, Season.SUMMER),
    JULY(1f, Season.SUMMER),
    AUGUST(0.866f, Season.SUMMER),
    SEPTEMBER(0.5f, Season.FALL),
    OCTOBER(0f, Season.FALL),
    NOVEMBER(-0.5f, Season.FALL),
    DECEMBER(-0.866f, Season.WINTER);

    private static final Month[] VALUES = values();

    private final float temperatureModifier;
    private final Season season;

    Month(float temperatureModifier, Season season) {
        this.temperatureModifier = temperatureModifier;
        this.season = season;
    }

    /**
     * TFC {@code Month.valueOf(int)}: out-of-range ids fall back to January rather than throwing.
     */
    public static Month valueOf(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : JANUARY;
    }

    public float temperatureModifier() {
        return temperatureModifier;
    }

    public Season season() {
        return season;
    }

    /** The following month; December wraps to January. */
    public Month next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** The month six months later, which is the Southern Hemisphere counterpart. */
    public Month opposite() {
        return VALUES[(ordinal() + 6) % VALUES.length];
    }
}
