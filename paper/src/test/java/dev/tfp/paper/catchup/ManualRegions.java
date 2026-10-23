/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One "region" run by the test thread: {@link #run} queues for this tick, {@link #runNextTick} for the next;
 * {@link #tick} advances the fake nanosecond clock by one tick (50 ms) and runs what became due.
 */
final class ManualRegions implements RegionExecutor {
    static final long TICK = 50_000_000L;

    final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final Deque<Runnable> now = new ArrayDeque<>();
    private final List<Runnable> next = new ArrayList<>();
    int ticks;

    @Override
    public void run(int chunkX, int chunkZ, Runnable task) {
        now.add(task);
    }

    @Override
    public void runNextTick(int chunkX, int chunkZ, Runnable task) {
        next.add(task);
    }

    @Override
    public boolean isOwner(int chunkX, int chunkZ) {
        return true;
    }

    /** Runs everything due this tick (including what those tasks queue for this tick). */
    void runAll() {
        Runnable r;
        while ((r = now.poll()) != null) {
            r.run();
        }
    }

    /** Next tick: advances the clock and runs the deferred work. */
    void tick() {
        ticks++;
        clock.addAndGet(TICK);
        now.addAll(next);
        next.clear();
        runAll();
    }

    boolean idle() {
        return now.isEmpty() && next.isEmpty();
    }

    /** Ticks until nothing is queued (at most {@code max} ticks); returns the ticks run. */
    int settle(int max) {
        runAll();
        int start = ticks;
        while (!idle() && ticks - start < max) {
            tick();
        }
        return ticks - start;
    }
}
