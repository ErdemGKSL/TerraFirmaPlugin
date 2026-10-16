/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.ready.ReadyGate;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * M1-BOOT-04: the ready gate (CraftEngine reloaded ∧ BetterModel models ready ∧ registries loaded) opens
 * within {@code limit} of the server JVM's start. The gate case: everything after it needs readiness.
 */
public final class ReadyCase implements SelfTestCase {
    private final Duration limit;
    private final LongSupplier clock;

    public ReadyCase(Duration limit) {
        this(limit, System::currentTimeMillis);
    }

    ReadyCase(Duration limit, LongSupplier clock) {
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "ready gate opens within " + limit.toSeconds() + " s of startup (M1-BOOT-04)";
    }

    /** The case computes its own deadline from the JVM start; this only bounds it. */
    @Override
    public Duration timeout() {
        return limit.plusSeconds(5);
    }

    @Override
    public boolean gate() {
        return true;
    }

    @Override
    public boolean needsReady() {
        return false;
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        ReadyGate gate = ctx.readyGate();
        long start = ctx.jvmStartMillis();
        long remaining = Math.max(0, start + limit.toMillis() - clock.getAsLong());
        return gate.ready().orTimeout(Math.max(1, remaining), TimeUnit.MILLISECONDS).handle((v, err) -> {
            Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
            if (cause instanceof TimeoutException) {
                throw new AssertionError("not ready " + limit.toSeconds() + " s after startup: " + gate.describe());
            }
            if (cause != null) {
                throw new AssertionError("the ready gate failed: " + cause.getMessage(), cause);
            }
            return String.format(Locale.ROOT, "%s, %.1f s after JVM start", gate.describe(),
                    (clock.getAsLong() - start) / 1000.0);
        });
    }
}
