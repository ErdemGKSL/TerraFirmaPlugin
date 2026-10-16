/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import dev.tfp.core.diagnostics.CraftEngineIssueMatcher.Issue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects CraftEngine configuration issues that concern our pack (M1-INT-02). Fed one log event at a
 * time by a log appender; read by {@code /tfp version} and the self-test ("zero CE issues logged").
 *
 * <p><b>Wiring deferred to M1-TEST-01.</b> On Paper CraftEngine logs through the SLF4J logger of its
 * {@code PluginProviderContext} (javap of {@code PaperCraftEngineBootstrap}: it builds a
 * {@code Slf4jPluginLogger}), which is Log4j underneath, so a {@code java.util.logging.Handler} never
 * sees these lines. Attaching a Log4j appender needs {@code log4j-core}, which is not on the Paper API
 * compile classpath; the in-server self-test harness will attach it and call {@link #accept}.
 * Thread-safe (log events arrive on any thread).
 */
public final class CraftEngineIssueLog {
    private static final int KEEP = 50;

    private final CraftEngineIssueMatcher matcher;
    private final AtomicInteger total = new AtomicInteger();
    private final Deque<Issue> recent = new ArrayDeque<>();

    public CraftEngineIssueLog(String namespace) {
        this.matcher = new CraftEngineIssueMatcher(namespace);
    }

    /** Classifies one log event; counts and keeps it if it concerns our pack. */
    public Optional<Issue> accept(String level, String message) {
        Optional<Issue> issue = matcher.match(level, message);
        issue.ifPresent(i -> {
            total.addAndGet(i.count());
            synchronized (recent) {
                if (recent.size() == KEEP) {
                    recent.removeFirst();
                }
                recent.addLast(i);
            }
        });
        return issue;
    }

    /** Issues reported for our pack since start (sum of the N in "Found N issue(s)", +1 per warning). */
    public int total() {
        return total.get();
    }

    /** The latest {@value #KEEP} matching lines, oldest first. */
    public List<Issue> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }
}
