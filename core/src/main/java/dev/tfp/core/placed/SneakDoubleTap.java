/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.placed;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The fallback placement gesture ({@link Gesture#SNEAK_DOUBLE_TAP}): two sneak starts within {@link #WINDOW_MS} arm the
 * player; the next right-click on a top face within {@link #ARMED_MS} places at once and disarms. One per player, used on
 * that player's thread only.
 */
public final class SneakDoubleTap {
    /** Longest time between the two sneak starts. */
    public static final long WINDOW_MS = 400;
    /** How long the armed state lasts. */
    public static final long ARMED_MS = 3000;

    private final LongSupplier millis;
    private long lastStart = Long.MIN_VALUE / 2;
    private long armedAt = Long.MIN_VALUE / 2;
    private boolean armed;

    public SneakDoubleTap(LongSupplier millis) {
        this.millis = Objects.requireNonNull(millis, "millis");
    }

    /** A sneak start; returns whether it armed the player. */
    public boolean sneakStarted() {
        long now = millis.getAsLong();
        if (now - lastStart <= WINDOW_MS) {
            armed = true;
            armedAt = now;
            lastStart = Long.MIN_VALUE / 2; // a third tap starts over
            return true;
        }
        lastStart = now;
        return false;
    }

    /** Whether the player is armed now. */
    public boolean armed() {
        return armed && millis.getAsLong() - armedAt <= ARMED_MS;
    }

    /** Uses the armed state (one placement); returns whether it was armed. */
    public boolean consume() {
        boolean was = armed();
        armed = false;
        return was;
    }
}
