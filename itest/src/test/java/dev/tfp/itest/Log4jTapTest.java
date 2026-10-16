/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import dev.tfp.paper.scheduler.ThreadAssertionException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * M1-TEST-01: the appender sees events of every logger below the root (as CraftEngine's SLF4J logger and the
 * JUL-bridged plugin loggers are on Paper) and stops seeing them once detached. Uses a private context, not
 * the global one.
 */
class Log4jTapTest {
    final LoggerContext ctx = new LoggerContext("tfp-selftest-test");
    final LogFindings findings = new LogFindings(new ThreadAssertMatcher(), new CraftEngineIssueMatcher("tfp"));

    @AfterEach
    void stop() {
        ctx.stop();
    }

    @Test
    void rootAppenderFeedsFindingsUntilDetached() {
        ctx.start();
        // the default configuration logs ERROR only; Paper's root logs INFO and up
        ctx.getConfiguration().getRootLogger().setLevel(Level.INFO);
        ctx.updateLoggers();
        Log4jTap tap = Log4jTap.attach(ctx, findings);
        Logger craftEngine = ctx.getLogger("CraftEngine");
        Logger tfp = ctx.getLogger("TerraFirmaPlugin");

        craftEngine.warn("Found 2 issue(s) in file 'plugins/CraftEngine/resources/tfp/configuration/ores.yml':");
        tfp.error("Region task failed", new ThreadAssertionException("not region owner of chunk world 1,1 on Netty"));
        tfp.info("Registries loaded: 1200 entries");

        assertThat(findings.craftEngineIssues()).isEqualTo(2);
        assertThat(findings.threadViolations()).isEqualTo(1);

        tap.detach(ctx);
        craftEngine.warn("Found 5 issue(s) in file 'plugins/CraftEngine/resources/tfp/configuration/ores.yml':");
        assertThat(findings.craftEngineIssues()).isEqualTo(2);
        assertThat(tap.isStopped()).isTrue();
    }
}
