/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs cases one after another with a timeout each and collects their results (M1-TEST-01). Blocking:
 * call it on the self-test thread, never on a tick thread. No Bukkit types, so it is unit-tested directly.
 *
 * <p>Rules: a case with a skip reason is skipped; once a {@link SelfTestCase#gate() gate} case has not
 * passed, later cases that {@link SelfTestCase#needsReady() need readiness} are skipped with the gate's
 * name; a case that throws from {@code run} or completes with a non-{@link AssertionError} is an error; one
 * that does not finish in time fails ("timed out") and its future is cancelled; one that throws
 * {@link SelfTestCase.Skip} is skipped with its message.
 *
 * <p>{@link #runAndReport} is the whole run: cases, then the log cases, then the JUnit report (skipped cases
 * with their reasons), and the process {@link #exitCode exit status} for CI.
 */
public final class CaseRunner {
    private final Consumer<String> progress;

    /** @param progress receives one line per finished case (the server log) */
    public CaseRunner(Consumer<String> progress) {
        this.progress = progress;
    }

    public List<CaseResult> runAll(List<? extends SelfTestCase> cases, SelfTestContext ctx) {
        List<CaseResult> results = new ArrayList<>();
        String failedGate = null;
        for (SelfTestCase c : cases) {
            CaseResult r;
            if (c.skipReason().isPresent()) {
                r = CaseResult.skipped(c.name(), c.skipReason().get());
            } else if (failedGate != null && c.needsReady()) {
                r = CaseResult.skipped(c.name(), "not run: gate case '" + failedGate + "' did not pass");
            } else {
                r = runOne(c, ctx);
                if (c.gate() && !r.passed()) {
                    failedGate = c.name();
                }
            }
            results.add(r);
            progress.accept(r.status() + " " + r.name() + (r.message().isEmpty() ? "" : ": " + r.message()));
        }
        return results;
    }

    /** Exit status: every case passed or was skipped. */
    public static final int EXIT_OK = 0;
    /** At least one case failed or broke. */
    public static final int EXIT_FAILED = 1;
    /** The report could not be written (CI cannot tell what happened). */
    public static final int EXIT_NO_REPORT = 2;

    /**
     * What a run produced.
     *
     * @param results  every case, the log cases last
     * @param exitCode {@link #EXIT_OK}, {@link #EXIT_FAILED} or {@link #EXIT_NO_REPORT}
     * @param report   where the JUnit report was written
     */
    public record Outcome(List<CaseResult> results, int exitCode, Path report) {
        public long failing() {
            return results.stream().filter(CaseRunner::failing).count();
        }
    }

    /**
     * Runs {@code cases}, appends {@code afterCases} (the log cases, read once the cases are done), writes the
     * JUnit report to {@code report} and computes the exit status. A runner crash becomes an error case; a
     * report that cannot be written gives {@link #EXIT_NO_REPORT}.
     */
    public Outcome runAndReport(List<? extends SelfTestCase> cases, SelfTestContext ctx,
            Supplier<List<CaseResult>> afterCases, Path report, Map<String, String> properties, Instant timestamp) {
        List<CaseResult> results = new ArrayList<>();
        try {
            results.addAll(runAll(cases, ctx));
        } catch (Throwable t) {
            results.add(CaseResult.error("self-test runner", t.toString(), stackTrace(t), Duration.ZERO));
        }
        try {
            results.addAll(afterCases.get());
        } catch (Throwable t) {
            results.add(CaseResult.error("self-test log cases", t.toString(), stackTrace(t), Duration.ZERO));
        }
        int code = exitCode(results);
        try {
            JUnitXmlReport.write(report, JUnitXmlReport.toXml(SelfTestSuite.NAME, results, properties, timestamp));
        } catch (IOException | RuntimeException e) {
            progress.accept("cannot write " + report.toAbsolutePath() + ": " + e);
            code = EXIT_NO_REPORT;
        }
        List<CaseResult> copy = List.copyOf(results);
        Outcome o = new Outcome(copy, code, report);
        long skipped = copy.stream().filter(r -> r.status() == CaseResult.Status.SKIPPED).count();
        progress.accept(copy.size() + " case(s), " + o.failing() + " failing, " + skipped + " skipped; exit status "
                + code + "; report: " + report.toAbsolutePath());
        return o;
    }

    /** {@link #EXIT_FAILED} if any case failed or broke, else {@link #EXIT_OK} (skips do not fail a run). */
    public static int exitCode(List<CaseResult> results) {
        return results.stream().anyMatch(CaseRunner::failing) ? EXIT_FAILED : EXIT_OK;
    }

    static boolean failing(CaseResult r) {
        return r.status() == CaseResult.Status.FAILED || r.status() == CaseResult.Status.ERROR;
    }

    CaseResult runOne(SelfTestCase c, SelfTestContext ctx) {
        long t0 = System.nanoTime();
        CompletableFuture<String> f;
        try {
            f = c.run(ctx);
            if (f == null) {
                return CaseResult.error(c.name(), "run() returned null", "", since(t0));
            }
        } catch (Throwable t) {
            return outcome(c.name(), t, t0);
        }
        try {
            String detail = f.get(Math.max(1, c.timeout().toMillis()), TimeUnit.MILLISECONDS);
            return CaseResult.passed(c.name(), detail, since(t0));
        } catch (TimeoutException e) {
            f.cancel(true);
            return CaseResult.failed(c.name(), "timed out after " + seconds(c.timeout()), "", since(t0));
        } catch (ExecutionException e) {
            return outcome(c.name(), e.getCause(), t0);
        } catch (CancellationException e) {
            return CaseResult.error(c.name(), "cancelled", stackTrace(e), since(t0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            f.cancel(true);
            return CaseResult.error(c.name(), "self-test thread interrupted", "", since(t0));
        }
    }

    private static CaseResult outcome(String name, Throwable t, long t0) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof SelfTestCase.Skip) {
            return CaseResult.skipped(name, String.valueOf(t.getMessage()));
        }
        if (t instanceof AssertionError) {
            return CaseResult.failed(name, String.valueOf(t.getMessage()), stackTrace(t), since(t0));
        }
        return CaseResult.error(name, t.getClass().getName() + ": " + t.getMessage(), stackTrace(t), since(t0));
    }

    private static Duration since(long t0) {
        return Duration.ofNanos(System.nanoTime() - t0);
    }

    private static String seconds(Duration d) {
        return d.toMillis() % 1000 == 0 ? d.toSeconds() + " s" : d.toMillis() + " ms";
    }

    static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
