/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters of the lazy snow catch-up (PAR-CLIM-03 server check: the first-load cost of new chunks). Thread-safe: the
 * region threads count the loads and the hop time, the worker pool the plan time. Read by the stats log of
 * {@link SnowService}, which runs only with {@code -Dtfp.snow.stats=true}; the counting itself is always on and costs a
 * few adds per chunk load.
 */
final class SnowCatchUpStats {
    /** The system property that turns the periodic log on. */
    static final String PROPERTY = "tfp.snow.stats";

    /** Chunk loads seen by the catch-up (stamp read). */
    final LongAdder loads = new LongAdder();
    /** Loads whose stamp was due (absent or older than 4 000 calendar ticks): a plan was computed. */
    final LongAdder due = new LongAdder();
    /** Plans with steps. */
    final LongAdder plansWithSteps = new LongAdder();
    /** Accumulation and melt steps planned. */
    final LongAdder accumulateSteps = new LongAdder();
    final LongAdder meltSteps = new LongAdder();
    /** Region-thread time of {@code onLoad} (stamp read and write, the two surface lookups). */
    final LongAdder loadNanos = new LongAdder();
    final LongAccumulator loadMaxNanos = new LongAccumulator(Math::max, 0);
    /** Worker-pool time of the plans (climate walk and mean rainfall). */
    final LongAdder planNanos = new LongAdder();
    final LongAccumulator planMaxNanos = new LongAccumulator(Math::max, 0);
    /** Region-thread time of the catch-up hops (surface lookups and submits, the decisions are timed by the pipeline). */
    final LongAdder hopNanos = new LongAdder();
    final LongAccumulator hopMaxNanos = new LongAccumulator(Math::max, 0);
    final LongAdder hops = new LongAdder();
    /** The loads count at the last log, so a quiet period logs nothing. */
    final AtomicLong loggedLoads = new AtomicLong();

    static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    void load(long nanos) {
        loads.increment();
        loadNanos.add(nanos);
        loadMaxNanos.accumulate(nanos);
    }

    void plan(long nanos, int accumulate, int melt) {
        planNanos.add(nanos);
        planMaxNanos.accumulate(nanos);
        if (accumulate + melt > 0) {
            plansWithSteps.increment();
        }
        accumulateSteps.add(accumulate);
        meltSteps.add(melt);
    }

    void hop(long nanos) {
        hops.increment();
        hopNanos.add(nanos);
        hopMaxNanos.accumulate(nanos);
    }

    /** One log line; {@code null} when no chunk was loaded since the last call. */
    String drain() {
        long l = loads.sum();
        if (l == loggedLoads.getAndSet(l)) {
            return null;
        }
        long d = due.sum();
        long h = hops.sum();
        return "loads=" + l + " due=" + d + " plansWithSteps=" + plansWithSteps.sum()
                + " accumulateSteps=" + accumulateSteps.sum() + " meltSteps=" + meltSteps.sum()
                + " loadAvgUs=" + avgMicros(loadNanos.sum(), l) + " loadMaxUs=" + loadMaxNanos.get() / 1_000
                + " planAvgUs=" + avgMicros(planNanos.sum(), d) + " planMaxUs=" + planMaxNanos.get() / 1_000
                + " hops=" + h + " hopAvgUs=" + avgMicros(hopNanos.sum(), h) + " hopMaxUs=" + hopMaxNanos.get() / 1_000;
    }

    private static long avgMicros(long nanos, long n) {
        return n == 0 ? 0 : nanos / n / 1_000;
    }
}
