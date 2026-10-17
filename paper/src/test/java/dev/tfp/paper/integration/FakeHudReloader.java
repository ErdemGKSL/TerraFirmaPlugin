/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.concurrent.atomic.AtomicInteger;

/** {@link HudReloader} that counts reload requests; {@link #reloading} sets what {@link #isReloading()} says. */
public final class FakeHudReloader implements HudReloader {
    private final AtomicInteger reloads = new AtomicInteger();
    private volatile boolean reloading;

    public FakeHudReloader reloading(boolean r) {
        this.reloading = r;
        return this;
    }

    public int reloads() {
        return reloads.get();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean isReloading() {
        return reloading;
    }

    @Override
    public boolean reload() {
        reloads.incrementAndGet();
        return true;
    }
}
