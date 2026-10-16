/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import dev.tfp.paper.scheduler.ThreadAssertionException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M1-TEST-01: the run fails on thread-assertion text and on CraftEngine issues for our pack. */
class LogFindingsTest {
    final LogFindings findings = new LogFindings(new ThreadAssertMatcher(), new CraftEngineIssueMatcher("tfp"));

    @Test
    void cleanLogPassesBothLogCases() {
        findings.accept("INFO", "Done (12.3s)! For help, type \"help\"", (Throwable) null);
        findings.accept("WARN", "Found 2 issue(s) in file 'plugins/CraftEngine/resources/default/configuration/x.yml':", "");
        findings.accept("ERROR", "Could not pass event to OtherPlugin", new IllegalStateException("unrelated"));
        assertThat(findings.results()).allMatch(CaseResult::passed);
    }

    @Test
    void ourThreadAssertionInAnExceptionChainFails() {
        RuntimeException wrapped = new RuntimeException("Task tfp-region generated an exception",
                new ThreadAssertionException("not region owner of chunk world 0,0 on tfp-cpu-1"));
        findings.accept("ERROR", "Uncaught exception in region task", wrapped);

        assertThat(findings.threadViolations()).isEqualTo(1);
        CaseResult threads = findings.results().get(0);
        assertThat(threads.status()).isEqualTo(CaseResult.Status.FAILED);
        assertThat(threads.detail()).contains("tfp ThreadAsserts", "not region owner");
        assertThat(findings.results().get(1).passed()).isTrue();
    }

    @Test
    void foliaPaperAndAsyncCatcherChecksAreRecognised() {
        ThreadAssertMatcher m = new ThreadAssertMatcher();
        assertThat(m.match("java.lang.IllegalStateException: Thread failed main thread check: Cannot read world "
                + "asynchronously, context=null, world=world, block_pos=BlockPos{x=0, y=64, z=0}")).isPresent();
        assertThat(m.match("Thread Craft Scheduler Thread - 3 failed main thread check: block remove")).isPresent();
        assertThat(m.match("java.lang.IllegalStateException: Asynchronous chunk load!")).isPresent();
        assertThat(m.match("java.lang.IllegalStateException: blocking/heavy work on tick thread Server thread")).isPresent();
        assertThat(m.match("Region owner changed hands")).isEmpty();
        assertThat(m.match("java.lang.IllegalStateException: Asynchronous")).isEmpty();
    }

    @Test
    void craftEngineIssuesForOurPackFailAndAreCounted() {
        findings.accept("WARN", "Found 3 issue(s) in file 'plugins/CraftEngine/resources/tfp/configuration/blocks/rock.yml':", "");
        findings.accept("WARN", "[CraftEngine] Unknown template 'tfp:visual/granite' in block tfp:rock/raw/granite", "");

        assertThat(findings.craftEngineIssues()).isEqualTo(4);
        CaseResult ce = findings.results().get(1);
        assertThat(ce.status()).isEqualTo(CaseResult.Status.FAILED);
        assertThat(ce.message()).startsWith("4 CraftEngine issue(s)");
        assertThat(ce.detail()).contains("rock.yml", "tfp:visual/granite");
    }

    @Test
    void latestLogScanJoinsStackTraceLinesToTheirEvent() {
        findings.source("logs/latest.log scan");
        findings.scan(List.of(
                "[12:00:01] [Server thread/INFO]: Starting minecraft server version 1.21.11",
                "[12:00:05] [Region Scheduler Thread #2/ERROR]: [TerraFirmaPlugin] Task failed",
                "java.lang.RuntimeException: wrapped",
                "\tat dev.tfp.paper.Foo.bar(Foo.java:1)",
                "Caused by: dev.tfp.paper.scheduler.ThreadAssertionException: not owner of entity 1234 on Netty",
                "[12:00:06] [Server thread/WARN]: [CraftEngine] Found 1 issue(s) in file 'resources/tfp/configuration/items.yml':",
                "[12:00:07] [Server thread/INFO]: Done (6.0s)!"));

        assertThat(findings.threadViolations()).isEqualTo(1);
        assertThat(findings.craftEngineIssues()).isEqualTo(1);
        assertThat(findings.results()).noneMatch(CaseResult::passed)
                .allSatisfy(r -> assertThat(r.message()).contains("logs/latest.log scan"));
    }

    @Test
    void keepsAtMostTheFirstLinesButCountsAll() {
        for (int i = 0; i < LogFindings.KEEP + 5; i++) {
            findings.accept("ERROR", "x", new ThreadAssertionException("expected a tfp worker thread, got main " + i));
        }
        assertThat(findings.threadViolations()).isEqualTo(LogFindings.KEEP + 5);
        assertThat(findings.results().get(0).detail().lines()).hasSize(LogFindings.KEEP);
    }
}
