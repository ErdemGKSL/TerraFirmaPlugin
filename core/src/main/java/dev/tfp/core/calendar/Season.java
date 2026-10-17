/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/Season.java @ c3b4d9536
 */
package dev.tfp.core.calendar;

/** The four seasons, in calendar order from spring. Each month belongs to exactly one season ({@link Month#season()}). */
public enum Season {
    SPRING,
    SUMMER,
    FALL,
    WINTER;

    private static final Season[] VALUES = values();

    public Season next() {
        return VALUES[(ordinal() + 1) & 0b11];
    }

    public Season previous() {
        return VALUES[(ordinal() - 1) & 0b11];
    }

    public Season opposite() {
        return VALUES[(ordinal() + 2) & 0b11];
    }
}
