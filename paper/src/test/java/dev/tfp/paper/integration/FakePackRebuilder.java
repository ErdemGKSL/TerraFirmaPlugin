/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link PackRebuilder} that counts requests and completes them with a preset outcome. */
public final class FakePackRebuilder implements PackRebuilder {
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean outcome = true;
    private volatile Throwable failure;

    /** Later rebuilds complete with {@code ok}. */
    public FakePackRebuilder succeeding(boolean ok) {
        this.outcome = ok;
        this.failure = null;
        return this;
    }

    /** Later rebuilds fail exceptionally. */
    public FakePackRebuilder failing(Throwable t) {
        this.failure = t;
        return this;
    }

    private volatile boolean busy;
    private volatile boolean available = true;

    /** What {@link #available()} reports from now on (false: CraftEngine absent). */
    public FakePackRebuilder available(boolean a) {
        this.available = a;
        return this;
    }

    /** What {@link #busy()} reports from now on. */
    public FakePackRebuilder busy(boolean b) {
        this.busy = b;
        return this;
    }

    private volatile Runnable onRebuild = () -> { };

    /** Runs at every {@link #rebuild()} before it completes (e.g. BetterHud starting a reload during the build). */
    public FakePackRebuilder onRebuild(Runnable r) {
        this.onRebuild = r;
        return this;
    }

    public int requests() {
        return requests.get();
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public boolean busy() {
        return busy;
    }

    @Override
    public CompletableFuture<Boolean> rebuild() {
        requests.incrementAndGet();
        onRebuild.run();
        Throwable f = failure;
        return f != null ? CompletableFuture.failedFuture(f) : CompletableFuture.completedFuture(outcome);
    }
}
