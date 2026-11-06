/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

/**
 * A per-viewer spawn rate limit for overlay entities (PAR-INFRA-16, design §3.2): {@value #DEFAULT_BURST} spawns at once,
 * refilled by {@value #DEFAULT_PER_TICK} per server tick, like CraftEngine Premium's entity-culling limiter. Time is the
 * caller's tick counter, so the bucket is pure and testable. Not thread-safe: one viewer's lock guards it.
 */
public final class TokenBucket {
    public static final int DEFAULT_BURST = 1000;
    public static final int DEFAULT_PER_TICK = 25;

    private final int burst;
    private final int perTick;
    private long tokens;
    private long lastTick;
    private boolean started;

    public TokenBucket(int burst, int perTick) {
        if (burst <= 0 || perTick <= 0) {
            throw new IllegalArgumentException("burst and refill must be positive");
        }
        this.burst = burst;
        this.perTick = perTick;
        this.tokens = burst;
    }

    public static TokenBucket defaults() {
        return new TokenBucket(DEFAULT_BURST, DEFAULT_PER_TICK);
    }

    private void refill(long tick) {
        if (!started) {
            started = true;
            lastTick = tick;
            return;
        }
        if (tick > lastTick) {
            tokens = Math.min(burst, tokens + (tick - lastTick) * perTick);
            lastTick = tick;
        }
    }

    /** Takes up to {@code wanted} tokens at {@code tick}; returns how many were granted. */
    public int take(int wanted, long tick) {
        refill(tick);
        int granted = (int) Math.max(0, Math.min(wanted, tokens));
        tokens -= granted;
        return granted;
    }

    /**
     * Charges {@code n} spawns that were sent regardless of the limit (the same-frame fast path, at most a few per
     * block change); the balance may go below zero down to {@code -burst}, which delays later bulk spawns.
     */
    public void charge(int n, long tick) {
        refill(tick);
        tokens = Math.max(-burst, tokens - n);
    }

    /** The tokens available at {@code tick}. */
    public long available(long tick) {
        refill(tick);
        return Math.max(0, tokens);
    }
}
