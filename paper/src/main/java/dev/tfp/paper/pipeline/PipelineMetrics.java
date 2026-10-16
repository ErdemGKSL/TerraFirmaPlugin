/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Counters for one pipeline + queue. Cheap, thread-safe, read by /tfp debug and self-tests. */
public final class PipelineMetrics {
    public final LongAdder submits = new LongAdder();
    public final LongAdder coalesced = new LongAdder();
    public final LongAdder reruns = new LongAdder();
    public final LongAdder decisions = new LongAdder();
    public final LongAdder noops = new LongAdder();
    public final LongAdder decideErrors = new LongAdder();
    public final LongAdder enqueued = new LongAdder();
    public final LongAdder queueCoalesced = new LongAdder();
    public final LongAdder applied = new LongAdder();
    public final LongAdder stale = new LongAdder();
    public final LongAdder superseded = new LongAdder();
    public final LongAdder unloaded = new LongAdder();
    public final LongAdder budgetDeferrals = new LongAdder();
    public final LongAdder threadViolations = new LongAdder();
    public final LongAdder remoteCaptures = new LongAdder();
    private final LongAdder latencyCount = new LongAdder();
    private final LongAdder latencySum = new LongAdder();
    private final AtomicLong latencyMax = new AtomicLong();

    void latency(long nanos) {
        latencyCount.increment();
        latencySum.add(nanos);
        latencyMax.accumulateAndGet(nanos, Math::max);
    }

    public long latencyMaxNanos() {
        return latencyMax.get();
    }

    public long latencyAvgNanos() {
        long n = latencyCount.sum();
        return n == 0 ? 0 : latencySum.sum() / n;
    }

    @Override
    public String toString() {
        return "submits=" + submits + " coalesced=" + coalesced + " reruns=" + reruns + " decisions=" + decisions
                + " noops=" + noops + " decideErrors=" + decideErrors + " enqueued=" + enqueued
                + " queueCoalesced=" + queueCoalesced + " applied=" + applied + " stale=" + stale
                + " superseded=" + superseded + " unloaded=" + unloaded + " budgetDeferrals=" + budgetDeferrals
                + " threadViolations=" + threadViolations + " remoteCaptures=" + remoteCaptures
                + " latencyAvgMs=" + latencyAvgNanos() / 1_000_000.0 + " latencyMaxMs=" + latencyMax.get() / 1_000_000.0;
    }
}
