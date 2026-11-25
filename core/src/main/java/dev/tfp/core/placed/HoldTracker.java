/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.placed;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The hold-right-click gesture that replaces TFC's placement key (NP-CLIENT-01, owner decision in PAR-POT-01). While the
 * use key is held on a block, the vanilla client resends its use-on-block packet every 4 ticks; each resulting event is
 * passed here with a {@code key} naming what is held where (block, face, hand, hotbar slot, item fingerprint).
 *
 * <ul>
 *   <li>One event per server tick counts ({@code UseItemOn} and {@code UseItem} of one client tick arrive in the same
 *       tick).</li>
 *   <li>A gap of more than {@link #MAX_GAP_MS} since the last event, or any other key, ends the hold; the next event starts
 *       a new one.</li>
 *   <li>A hold completes once at least {@link #MIN_EVENTS} counted events span at least {@link #HOLD_MS}, and at most once
 *       per hold: later events report {@link Phase#DONE}, whatever their key, until the key is released (the cursor
 *       then rests on the item just placed).</li>
 *   <li>{@link #COOLDOWN_MS} after a completion no other hold can complete (anti-spam).</li>
 * </ul>
 * Not thread-safe: one tracker per player, used only on that player's thread (its entity scheduler). Times come from the
 * injected millisecond clock.
 *
 * @param <K> the hold key; compared with {@code equals}
 */
public final class HoldTracker<K> {
    /** Longest gap between two events of one hold (about 6 ticks, lag tolerant). */
    public static final long MAX_GAP_MS = 300;
    /** How long the key must be held. */
    public static final long HOLD_MS = 1000;
    /** Fewest counted events of a completed hold. */
    public static final int MIN_EVENTS = 5;
    /** No completion within this time after the last one. */
    public static final long COOLDOWN_MS = 250;

    /** What one event means. */
    public enum Phase {
        /** The first event of a new hold. */
        STARTED,
        /** A later event; not complete yet. */
        PROGRESS,
        /** This event completed the hold: place now. */
        COMPLETE,
        /** The hold completed earlier; nothing more happens until it is released. */
        DONE
    }

    /** One event's outcome; {@code progress} runs from 0 to 1. */
    public record Step(Phase phase, double progress) {}

    /** A hold that ended without another event: its key, whether it completed and how many events it counted. */
    public record Released<K>(K key, boolean completed, int events) {}

    private final LongSupplier millis;
    private K key;
    private long start;
    private long lastSeen;
    private long lastTick;
    private int events;
    private boolean completed;
    private long cooldownUntil = Long.MIN_VALUE;

    public HoldTracker(LongSupplier millis) {
        this.millis = Objects.requireNonNull(millis, "millis");
    }

    /**
     * One use event.
     *
     * @param key  what is held where
     * @param tick the server tick of the event (two events of one tick count once)
     */
    public Step event(K key, long tick) {
        Objects.requireNonNull(key, "key");
        long now = millis.getAsLong();
        if (this.key != null && completed && now - lastSeen <= MAX_GAP_MS && !this.key.equals(key)) {
            // one placement per hold: the cursor now rests on the placed item, still held, so nothing until a release
            this.key = key;
            lastSeen = now;
            lastTick = tick;
            return new Step(Phase.DONE, 1);
        }
        if (this.key == null || !this.key.equals(key) || now - lastSeen > MAX_GAP_MS) {
            this.key = key;
            start = now;
            lastSeen = now;
            lastTick = tick;
            events = 1;
            completed = false;
            return new Step(Phase.STARTED, 0);
        }
        lastSeen = now;
        if (tick != lastTick) {
            lastTick = tick;
            events++;
        }
        if (completed) {
            return new Step(Phase.DONE, 1);
        }
        double progress = Math.min(1.0, (now - start) / (double) HOLD_MS);
        if (events >= MIN_EVENTS && now - start >= HOLD_MS && now >= cooldownUntil) {
            completed = true;
            cooldownUntil = now + COOLDOWN_MS;
            return new Step(Phase.COMPLETE, 1);
        }
        return new Step(Phase.PROGRESS, progress);
    }

    /** The key of the current hold, empty when there is none. */
    public Optional<K> key() {
        return Optional.ofNullable(key);
    }

    /** Whether the current hold has completed (it waits for a release). */
    public boolean completed() {
        return key != null && completed;
    }

    /** Whether a hold is in progress (or completed and not yet released). */
    public boolean active() {
        return key != null;
    }

    /** Whether the current hold has had no event for more than {@link #MAX_GAP_MS}: the key was released. */
    public boolean expired() {
        return key != null && millis.getAsLong() - lastSeen > MAX_GAP_MS;
    }

    /** Ends the current hold if it {@link #expired}; returns it. */
    public Optional<Released<K>> releaseIfExpired() {
        return expired() ? release() : Optional.empty();
    }

    /** Ends the current hold now (quit, death, world change, slot change); returns it, empty when there was none. */
    public Optional<Released<K>> release() {
        if (key == null) {
            return Optional.empty();
        }
        Released<K> r = new Released<>(key, completed, events);
        key = null;
        events = 0;
        completed = false;
        return Optional.of(r);
    }

    /** Whether a completion is allowed now (the cooldown after the last one has passed). */
    public boolean cooledDown() {
        return millis.getAsLong() >= cooldownUntil;
    }

    /** Starts the cooldown now (a placement by another gesture). */
    public void placedNow() {
        cooldownUntil = millis.getAsLong() + COOLDOWN_MS;
    }
}
