/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.ready.ReadyGate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Small cases and a context for runner tests. */
final class TestCases {
    private TestCases() {
    }

    static SelfTestCase of(String name, Duration timeout, Function<SelfTestContext, CompletableFuture<String>> body) {
        return of(name, timeout, false, true, body);
    }

    static SelfTestCase of(String name, Duration timeout, boolean gate, boolean needsReady,
            Function<SelfTestContext, CompletableFuture<String>> body) {
        return new SelfTestCase() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Duration timeout() {
                return timeout;
            }

            @Override
            public CompletableFuture<String> run(SelfTestContext ctx) {
                return body.apply(ctx);
            }

            @Override
            public boolean gate() {
                return gate;
            }

            @Override
            public boolean needsReady() {
                return needsReady;
            }
        };
    }

    /** A context without a server: {@code tfp()} fails like a missing main plugin. */
    static SelfTestContext context(ReadyGate gate, long jvmStart) {
        return new SelfTestContext() {
            @Override
            public TfpPlugin tfp() {
                throw new AssertionError("TerraFirmaPlugin is not installed");
            }

            @Override
            public ReadyGate readyGate() {
                return gate;
            }

            @Override
            public long jvmStartMillis() {
                return jvmStart;
            }
        };
    }
}
