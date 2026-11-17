/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.calendar;

import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;

/**
 * The persistent form of {@link CalendarState} (world PDC {@code tfp:calendar}, M2-CAL-01). The running flag
 * is not stored: it is recomputed from the online players on every advance, so a loaded state starts
 * arrested. Version 1 fields: {@code playerTicks}, {@code calendarTicks}, {@code partial},
 * {@code dayLength}, {@code daysInMonth}.
 *
 * <p>Version 2 (PAR-CLIM-13): the same fields; {@code dayLength} may now be 0 (TFC's disabled calendar tick rate,
 * {@link CalendarState#DISABLED_DAY_LENGTH}). The version moved so that an older plugin keeps a disabled calendar
 * untouched (newer data) instead of discarding it as unreadable. Version 1 data needs no change.
 */
public final class CalendarCodec {
    /** The current layout version. */
    public static final int VERSION = 2;

    /** The codec. Immutable and thread-safe. */
    public static final VersionedCodec<CalendarState> CODEC = VersionedCodec.<CalendarState>builder("calendar", VERSION)
            .migrate(1, data -> { }) // v1 -> v2: same layout, dayLength 0 newly allowed
            .build((s, out) -> out.putLong("playerTicks", s.playerTicks())
                            .putLong("calendarTicks", s.calendarTicks())
                            .putLong("partial", s.partialNumerator())
                            .putInt("dayLength", s.dayLengthMinutes())
                            .putInt("daysInMonth", s.daysInMonth()),
                    in -> {
                        try {
                            return new CalendarState(in.requireLong("playerTicks"), in.requireLong("calendarTicks"),
                                    in.requireLong("partial"), in.requireInt("dayLength"), in.requireInt("daysInMonth"), false);
                        } catch (IllegalArgumentException e) {
                            throw new PersistException("bad calendar values: " + e.getMessage(), e);
                        }
                    });

    private CalendarCodec() {
    }
}
