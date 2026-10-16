/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * A Log4j appender on the server's root logger that feeds every event to {@link LogFindings} (the open
 * part of M1-INT-02). Why Log4j: on Paper, CraftEngine logs through an SLF4J logger backed by Log4j
 * ({@code Slf4jPluginLogger}), and plugin {@code java.util.logging} loggers are bridged to Log4j too, so
 * a JUL handler would miss CraftEngine's lines while a root Log4j appender sees all of them.
 * {@code log4j-core} is provided by the server (2.25.2 on 1.21.11, 2.26.0 on 26.3); we compile against it
 * only. Events arrive on any thread (Paper's root uses an async appender): {@link #append} is cheap and
 * thread-safe.
 */
final class Log4jTap extends AbstractAppender {
    static final String NAME = "TfpSelfTestTap";

    private final LogFindings findings;

    private Log4jTap(LogFindings findings) {
        super(NAME, null, null, true, Property.EMPTY_ARRAY);
        this.findings = findings;
    }

    /** Adds a started tap to {@code ctx}'s root logger config. */
    static Log4jTap attach(LoggerContext ctx, LogFindings findings) {
        Log4jTap tap = new Log4jTap(findings);
        tap.start();
        ctx.getConfiguration().getRootLogger().addAppender(tap, null, null);
        ctx.updateLoggers();
        return tap;
    }

    void detach(LoggerContext ctx) {
        ctx.getConfiguration().getRootLogger().removeAppender(NAME);
        ctx.updateLoggers();
        stop();
    }

    @Override
    public void append(LogEvent event) {
        String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        findings.accept(event.getLevel().name(), message, event.getThrown());
    }
}
