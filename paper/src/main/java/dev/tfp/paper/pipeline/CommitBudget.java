/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Per-thread time budget per tick window. On Folia a region thread ticks one region at a time, and on
 * Paper there is one tick thread, so "per thread per 50 ms window" approximates "per region per tick"
 * without needing a tick counter. The budget is checked before each change, so a window can overshoot
 * by at most one change.
 */
public final class CommitBudget {
    static final long WINDOW_NANOS = 50_000_000L;

    private final long budgetNanos;
    private final LongSupplier clock;
    private final ThreadLocal<long[]> window = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE, 0});
    private final AtomicLong maxWindowNanos = new AtomicLong();
    private final AtomicLong windowsOverBudget = new AtomicLong();

    public CommitBudget(long budgetNanos, LongSupplier clock) {
        if (budgetNanos <= 0) {
            throw new IllegalArgumentException("budget must be positive");
        }
        this.budgetNanos = budgetNanos;
        this.clock = clock;
    }

    /** @return true if this thread may apply another change in the current window */
    public boolean tryStart() {
        long[] w = window.get();
        long now = clock.getAsLong();
        if (w[0] == Long.MIN_VALUE || now - w[0] >= WINDOW_NANOS) {
            if (w[1] > budgetNanos) {
                windowsOverBudget.incrementAndGet();
            }
            w[0] = now;
            w[1] = 0;
        }
        return w[1] < budgetNanos;
    }

    public void charge(long nanos) {
        long[] w = window.get();
        w[1] += nanos;
        maxWindowNanos.accumulateAndGet(w[1], Math::max);
    }

    public long now() {
        return clock.getAsLong();
    }

    public long budgetNanos() {
        return budgetNanos;
    }

    public long maxWindowNanos() {
        return maxWindowNanos.get();
    }

    public long windowsOverBudget() {
        return windowsOverBudget.get();
    }
}
