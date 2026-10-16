/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.clock;

/**
 * The global clock (ARCHITECTURE.md §5, "Global state"): a single {@code volatile} reference to an
 * immutable {@link ClockSnapshot}.
 *
 * <ul>
 *   <li><b>Readers</b> (any thread: region, worker, Netty, BetterHud's timer) call {@link #get()} once per
 *       operation. One volatile read, no lock, no allocation. Because the snapshot is an immutable record
 *       published through a volatile write, a reader sees all of its fields from the same publication
 *       (no tearing between {@code playerTicks} and {@code calendarTicks}).</li>
 *   <li><b>Writers</b>: the calendar, on the global region scheduler (M2-CAL-01). Writes are rare (once
 *       per tick at most), so {@link #publish} is {@code synchronized} to keep {@code sequence} strictly
 *       increasing even if a second writer appears (a {@code /time set} handler, a test).</li>
 * </ul>
 * The calendar may move backwards ({@code /time set}); the holder does not forbid that. {@code sequence}
 * is the only monotonic field.
 */
public final class GlobalClockHolder {
    private volatile ClockSnapshot current = ClockSnapshot.UNSET;

    /** The current reading. Take it once per operation. */
    public ClockSnapshot get() {
        return current;
    }

    /**
     * Publishes a new reading with {@code sequence} = previous + 1.
     *
     * @return the published snapshot
     */
    public synchronized ClockSnapshot publish(long playerTicks, long calendarTicks, int daysInMonth, boolean running) {
        ClockSnapshot next = new ClockSnapshot(current.sequence() + 1, playerTicks, calendarTicks, daysInMonth, running);
        current = next;
        return next;
    }
}
