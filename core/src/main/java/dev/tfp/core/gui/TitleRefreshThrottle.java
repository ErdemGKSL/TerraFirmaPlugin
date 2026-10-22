/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import java.util.Objects;

/**
 * PAR-INFRA-03 {@code fw-title-refresh}: rate limit for title changes. Each title change re-sends the whole screen
 * ({@code OpenScreen}), so a viewer gets at most one per {@link #intervalMillis}; changes in between are coalesced into
 * the latest value, sent when the interval ends. A value equal to the one on screen is never sent.
 *
 * <p>Not thread-safe: one instance per viewer, used on that viewer's thread (the player's entity scheduler).
 *
 * @param <T> the title value
 */
public final class TitleRefreshThrottle<T> {
    /** 4 refreshes per second. */
    public static final long DEFAULT_INTERVAL_MILLIS = 250;

    /** What the caller must do after {@link #submit}. */
    public sealed interface Decision<T> {
    }

    /** Send {@code value} now. */
    public record SendNow<T>(T value) implements Decision<T> {
    }

    /** Call {@link #flush} after {@code delayMillis}. */
    public record Schedule<T>(long delayMillis) implements Decision<T> {
    }

    /** Nothing to do (unchanged, or a flush is already scheduled and will send the latest value). */
    public record Idle<T>() implements Decision<T> {
    }

    private final long intervalMillis;
    private T shown;
    private T pending;
    private boolean hasShown;
    private long lastSent = Long.MIN_VALUE;
    private boolean flushScheduled;

    public TitleRefreshThrottle(long intervalMillis) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("interval " + intervalMillis);
        }
        this.intervalMillis = intervalMillis;
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    /** Records the value the screen was opened with (sent by opening, not by a refresh). */
    public void opened(T value, long now) {
        shown = value;
        hasShown = true;
        pending = null;
        lastSent = now;
    }

    /** A new wanted value at time {@code now} (milliseconds). */
    public Decision<T> submit(T value, long now) {
        Objects.requireNonNull(value);
        if (flushScheduled) {
            pending = value;
            return new Idle<>();
        }
        if (hasShown && value.equals(shown)) {
            pending = null;
            return new Idle<>();
        }
        long since = now - lastSent;
        if (lastSent == Long.MIN_VALUE || since >= intervalMillis) {
            send(value, now);
            return new SendNow<>(value);
        }
        pending = value;
        flushScheduled = true;
        return new Schedule<>(intervalMillis - since);
    }

    /** The scheduled flush ran: the value to send now, or null when the latest value is already on screen. */
    public T flush(long now) {
        flushScheduled = false;
        T v = pending;
        pending = null;
        if (v == null || (hasShown && v.equals(shown))) {
            return null;
        }
        send(v, now);
        return v;
    }

    /** True while a flush is scheduled. */
    public boolean flushScheduled() {
        return flushScheduled;
    }

    private void send(T value, long now) {
        shown = value;
        hasShown = true;
        lastSent = now;
    }
}
