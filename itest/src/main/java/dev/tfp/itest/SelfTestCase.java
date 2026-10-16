/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * One in-server self-test case (M1-TEST-01). {@link #run} is called on the self-test thread (never a tick
 * thread) and must do its world work through our {@code Scheduler}, so the harness respects Folia's rules.
 *
 * <p>Outcome: the future completes normally (pass; the value is a one-line detail for the report, may be
 * null), with an {@link AssertionError} (failure) or with anything else (error). A case that does not finish
 * within {@link #timeout()} fails.
 */
public interface SelfTestCase {
    /** Stable, human-readable name: the JUnit test case name. */
    String name();

    Duration timeout();

    CompletableFuture<String> run(SelfTestContext ctx);

    /** Present: the case is reported as skipped with this reason and not run. */
    default Optional<String> skipReason() {
        return Optional.empty();
    }

    /** A gate case: if it does not pass, later cases with {@link #needsReady()} are skipped. */
    default boolean gate() {
        return false;
    }

    /** Needs the ready gate (and every earlier gate case) to have passed. */
    default boolean needsReady() {
        return true;
    }

    /** A case that is always skipped, with the reason in the report. */
    static SelfTestCase skipped(String name, String reason) {
        return new SelfTestCase() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Duration timeout() {
                return Duration.ZERO;
            }

            @Override
            public CompletableFuture<String> run(SelfTestContext ctx) {
                throw new UnsupportedOperationException("skipped case " + name + " must not run");
            }

            @Override
            public Optional<String> skipReason() {
                return Optional.of(reason);
            }
        };
    }
}
