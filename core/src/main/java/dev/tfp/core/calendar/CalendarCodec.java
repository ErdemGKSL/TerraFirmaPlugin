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
 */
public final class CalendarCodec {
    /** The current layout version. */
    public static final int VERSION = 1;

    /** The codec. Immutable and thread-safe. */
    public static final VersionedCodec<CalendarState> CODEC = VersionedCodec.<CalendarState>builder("calendar", VERSION)
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
