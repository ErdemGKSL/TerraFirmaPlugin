/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Runs cases one after another with a timeout each and collects their results (M1-TEST-01). Blocking:
 * call it on the self-test thread, never on a tick thread. No Bukkit types, so it is unit-tested directly.
 *
 * <p>Rules: a case with a skip reason is skipped; once a {@link SelfTestCase#gate() gate} case has not
 * passed, later cases that {@link SelfTestCase#needsReady() need readiness} are skipped with the gate's
 * name; a case that throws from {@code run} or completes with a non-{@link AssertionError} is an error; one
 * that does not finish in time fails ("timed out") and its future is cancelled.
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
