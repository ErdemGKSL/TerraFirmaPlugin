/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.clock;

/**
 * One immutable reading of the global clock, published as a whole by {@link GlobalClockHolder}. Every
 * time-based rule (food decay, heat, growth, devices; ARCHITECTURE.md §4.1) takes one snapshot per
 * operation and evaluates against it, so the fields it reads always belong together.
 *
 * <p>The calendar (M2-CAL-01) fills the values; until then the holder carries {@link #UNSET}. The
 * field set mirrors TFC's {@code ICalendar}: player ticks (advance only while players are online) and
 * calendar ticks (the in-game date), plus the configured month length.
 *
 * @param sequence      publication counter, assigned by the holder (0 = never published)
 * @param playerTicks   TFC {@code getTicks()}: ticks that pass while players are online
 * @param calendarTicks TFC {@code getCalendarTicks()}: the in-game date/time in ticks
 * @param daysInMonth   the configured month length (TFC default 8)
 * @param running       false while the calendar is arrested (no players online, TFC "doDaylightCycle")
 */
public record ClockSnapshot(long sequence, long playerTicks, long calendarTicks, int daysInMonth, boolean running) {
    /** TFC's default month length ({@code TFCConfig.SERVER.defaultMonthLength}). */
    public static final int DEFAULT_DAYS_IN_MONTH = 8;

    /** Before the calendar published anything. */
    public static final ClockSnapshot UNSET = new ClockSnapshot(0L, 0L, 0L, DEFAULT_DAYS_IN_MONTH, false);

    public ClockSnapshot {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence < 0: " + sequence);
        }
        if (daysInMonth < 1) {
            throw new IllegalArgumentException("daysInMonth < 1: " + daysInMonth);
        }
    }

    /** True once the calendar has published a reading. */
    public boolean isSet() {
        return sequence > 0;
    }
}
