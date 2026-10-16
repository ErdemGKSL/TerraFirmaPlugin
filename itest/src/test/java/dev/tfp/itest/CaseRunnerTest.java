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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void aSkipThrownAtRunTimeIsASkipWithItsReason() {
        List<CaseResult> r = runner.runAll(List.of(
                of("sync skip", SHORT, c -> {
                    throw new SelfTestCase.Skip("BetterHud is not installed");
                }),
                of("async skip", SHORT, c -> CompletableFuture.supplyAsync(() -> {
                    throw new SelfTestCase.Skip("no pack this run");
                }))), ctx);
        assertThat(r).extracting(CaseResult::status).containsExactly(Status.SKIPPED, Status.SKIPPED);
        assertThat(r).extracting(CaseResult::message).containsExactly("BetterHud is not installed", "no pack this run");
        assertThat(CaseRunner.exitCode(r)).isZero();
    }

    /** M1-TEST-01: the whole run with fake cases: report with skip reasons, and the exit status. */
    @Test
    void runAndReportWritesTheReportWithSkipsAndAFailingExitStatus(@TempDir Path dir) throws Exception {
        Path report = dir.resolve("selftest-report.xml");
        CaseRunner.Outcome o = runner.runAndReport(List.of(
                of("passes", SHORT, c -> CompletableFuture.completedFuture("fine")),
                SelfTestCase.skipped("declared skip", "adapter not verified"),
                of("runtime skip", SHORT, c -> {
                    throw new SelfTestCase.Skip("CraftEngine is not installed");
                }),
                of("fails", SHORT, c -> CompletableFuture.failedFuture(new AssertionError("expected 1, got 2")))),
                ctx, () -> List.of(CaseResult.passed("log case", "none", Duration.ZERO)), report,
                Map.of("server", "fake"), Instant.parse("2026-10-16T12:00:00Z"));

        assertThat(o.exitCode()).isEqualTo(CaseRunner.EXIT_FAILED);
        assertThat(o.failing()).isEqualTo(1);
        assertThat(o.results()).extracting(CaseResult::name).containsExactly("passes", "declared skip", "runtime skip",
                "fails", "log case");
        String xml = Files.readString(report);
        assertThat(xml).contains("tests=\"5\" failures=\"1\" errors=\"0\" skipped=\"2\"",
                "<skipped message=\"adapter not verified\"/>", "<skipped message=\"CraftEngine is not installed\"/>",
                "<failure message=\"expected 1, got 2\"", "<property name=\"server\" value=\"fake\"/>");
        assertThat(progress.getLast()).contains("5 case(s), 1 failing, 2 skipped; exit status 1");
    }

    @Test
    void runAndReportExitsZeroWhenNothingFailsAndTwoWithoutAReport(@TempDir Path dir) throws Exception {
        List<SelfTestCase> fine = List.of(of("passes", SHORT, c -> CompletableFuture.completedFuture(null)),
                SelfTestCase.skipped("skipped", "why"));
        CaseRunner.Outcome ok = runner.runAndReport(fine, ctx, List::of, dir.resolve("r.xml"), Map.of(), Instant.now());
        assertThat(ok.exitCode()).isEqualTo(CaseRunner.EXIT_OK);
        assertThat(dir.resolve("r.xml")).exists();

        // log cases that throw are an error, not a crash of the run
        CaseRunner.Outcome broken = runner.runAndReport(fine, ctx, () -> {
            throw new IllegalStateException("log gone");
        }, dir.resolve("b.xml"), Map.of(), Instant.now());
        assertThat(broken.exitCode()).isEqualTo(CaseRunner.EXIT_FAILED);
        assertThat(broken.results().getLast().status()).isEqualTo(Status.ERROR);

        // the report path is a directory: nothing can be written
        Files.createDirectories(dir.resolve("taken.xml"));
        Files.writeString(dir.resolve("taken.xml").resolve("x"), "x");
        CaseRunner.Outcome lost = runner.runAndReport(fine, ctx, List::of, dir.resolve("taken.xml"), Map.of(), Instant.now());
        assertThat(lost.exitCode()).isEqualTo(CaseRunner.EXIT_NO_REPORT);
    }

    @Test
    void exitCodeCountsFailuresAndErrorsOnly() {
        assertThat(CaseRunner.exitCode(List.of())).isZero();
        assertThat(CaseRunner.exitCode(List.of(CaseResult.skipped("s", "r"), CaseResult.passed("p", "", Duration.ZERO))))
                .isZero();
        assertThat(CaseRunner.exitCode(List.of(CaseResult.error("e", "boom", "", Duration.ZERO)))).isEqualTo(1);
        assertThat(CaseRunner.exitCode(List.of(CaseResult.failed("f", "no", "", Duration.ZERO)))).isEqualTo(1);
    }

    @Test
    void aPassingGateLetsTheRestRun() {
        List<CaseResult> r = runner.runAll(List.of(
                of("gate", SHORT, true, false, c -> CompletableFuture.completedFuture("open")),
                of("dependent", SHORT, c -> CompletableFuture.completedFuture("ran"))), ctx);
        assertThat(r).allMatch(CaseResult::passed);
    }
}
