/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static dev.tfp.itest.TestCases.context;
import static dev.tfp.itest.TestCases.of;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.itest.CaseResult.Status;
import dev.tfp.paper.ready.ReadyGate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** M1-TEST-01: the runner's rules (outcomes, timeouts, skips, gate). */
class CaseRunnerTest {
    static final Duration SHORT = Duration.ofMillis(200);
    final List<String> progress = new ArrayList<>();
    final CaseRunner runner = new CaseRunner(progress::add);
    final SelfTestContext ctx = context(new ReadyGate(), System.currentTimeMillis());

    @Test
    void passFailureErrorAndSynchronousThrowAreClassified() {
        List<CaseResult> r = runner.runAll(List.of(
                of("pass", SHORT, c -> CompletableFuture.completedFuture("all good")),
                of("async assertion", SHORT, c -> CompletableFuture.supplyAsync(() -> {
                    throw new AssertionError("expected 3, got 4");
                })),
                of("async exception", SHORT, c -> CompletableFuture.failedFuture(new IllegalStateException("boom"))),
                of("sync assertion", SHORT, c -> {
                    throw new AssertionError("no world");
                }),
                of("needs the main plugin", SHORT, c -> CompletableFuture.completedFuture(c.tfp().getName())),
                of("null future", SHORT, c -> null)), ctx);

        assertThat(r).extracting(CaseResult::status).containsExactly(Status.PASSED, Status.FAILED, Status.ERROR,
                Status.FAILED, Status.FAILED, Status.ERROR);
        assertThat(r.get(0).message()).isEqualTo("all good");
        assertThat(r.get(1).message()).isEqualTo("expected 3, got 4");
        assertThat(r.get(2).message()).isEqualTo("java.lang.IllegalStateException: boom");
        assertThat(r.get(2).detail()).contains("IllegalStateException", "at ");
        assertThat(r.get(4).message()).contains("not installed");
        assertThat(progress).hasSize(6).first().asString().startsWith("PASSED pass");
    }

    @Test
    void aCaseThatNeverFinishesTimesOutIsCancelledAndTheRunGoesOn() {
        CompletableFuture<String> never = new CompletableFuture<>();
        long t0 = System.nanoTime();
        List<CaseResult> r = runner.runAll(List.of(
                of("hangs", SHORT, c -> never),
                of("after", SHORT, c -> CompletableFuture.completedFuture(null))), ctx);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(r.get(0).status()).isEqualTo(Status.FAILED);
        assertThat(r.get(0).message()).isEqualTo("timed out after 200 ms");
        assertThat(r.get(0).time()).isGreaterThanOrEqualTo(SHORT);
        assertThat(never).isCancelled();
        assertThat(r.get(1).status()).isEqualTo(Status.PASSED);
        assertThat(ms).isLessThan(5_000);
    }

    @Test
    void skipReasonsAndAFailedGateSkipLaterCasesThatNeedReadiness() {
        AtomicBoolean ranDependent = new AtomicBoolean();
        List<CaseResult> r = runner.runAll(List.of(
                SelfTestCase.skipped("pending", "adapter not verified"),
                of("gate", SHORT, true, false, c -> CompletableFuture.failedFuture(new AssertionError("not ready"))),
                of("dependent", SHORT, c -> {
                    ranDependent.set(true);
                    return CompletableFuture.completedFuture(null);
                }),
                of("independent", SHORT, false, false, c -> CompletableFuture.completedFuture("ran"))), ctx);

        assertThat(r).extracting(CaseResult::status).containsExactly(Status.SKIPPED, Status.FAILED, Status.SKIPPED,
                Status.PASSED);
        assertThat(r.get(0).message()).isEqualTo("adapter not verified");
        assertThat(r.get(2).message()).contains("gate case 'gate' did not pass");
        assertThat(ranDependent).isFalse();
    }

    @Test
    void aPassingGateLetsTheRestRun() {
        List<CaseResult> r = runner.runAll(List.of(
                of("gate", SHORT, true, false, c -> CompletableFuture.completedFuture("open")),
                of("dependent", SHORT, c -> CompletableFuture.completedFuture("ran"))), ctx);
        assertThat(r).allMatch(CaseResult::passed);
    }
}
