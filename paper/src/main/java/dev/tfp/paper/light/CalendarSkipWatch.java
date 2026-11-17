/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * PAR-HEAT-22: notices a forward calendar skip ({@code CalendarService.skip}, {@code /tfp time add}, sleeping) from one
 * global task every {@link #PERIOD_TICKS} server ticks, as charcoal's {@code PitClock} does. Between two observations the
 * calendar moves at most about one tick per server tick; a jump of more than twice the period is a skip, and
 * {@link #onSkip} runs. A calendar that stands still (no players online) or moves back is not a skip. Global thread.
 */
public final class CalendarSkipWatch {
    /** Server ticks between observations. */
    public static final long PERIOD_TICKS = 20;
    /** A calendar move above this between two observations counts as a skip. */
    public static final long SKIP_THRESHOLD = 2 * PERIOD_TICKS;

    private final LongSupplier calendar;
    private final Runnable onSkip;
    private long last = Long.MIN_VALUE;

    /**
     * @param calendar the player-tick calendar the torch counters are stamped with
     * @param onSkip   what to do after a skip
     */
    public CalendarSkipWatch(LongSupplier calendar, Runnable onSkip) {
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.onSkip = Objects.requireNonNull(onSkip, "onSkip");
    }

    /** One observation. Returns whether a skip was seen (and {@link #onSkip} ran). */
    public boolean observe() {
        long now = calendar.getAsLong();
        long before = last;
        last = now;
        if (before == Long.MIN_VALUE || now - before <= SKIP_THRESHOLD) {
            return false;
        }
        onSkip.run();
        return true;
    }
}
