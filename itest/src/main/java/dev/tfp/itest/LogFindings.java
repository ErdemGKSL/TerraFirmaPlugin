/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the two log conditions that fail a self-test run (M1-TEST-01): thread-ownership violations
 * ({@link ThreadAssertMatcher}) and CraftEngine configuration issues for our pack
 * ({@link CraftEngineIssueMatcher}, M1-INT-02). Fed either live, one log event at a time ({@link #accept},
 * from {@link Log4jTap}), or from the server's {@code logs/latest.log} ({@link #scan}). Thread-safe.
 */
public final class LogFindings {
    /** Lines kept per kind in the report. */
    static final int KEEP = 20;
    /** Paper's file layout: {@code [HH:mm:ss] [thread/LEVEL]: message}. */
    private static final Pattern FILE_LINE = Pattern.compile(
            "^\\[\\d{2}:\\d{2}:\\d{2}] \\[(.*)/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)]: (.*)$");
    /** Paper prefixes plugin messages with {@code [LoggerName] }; the CE matcher wants the bare message. */
    private static final Pattern LOGGER_PREFIX = Pattern.compile("^\\[[^\\]\\s]+] ");

    private final ThreadAssertMatcher threads;
    private final CraftEngineIssueMatcher craftEngine;
    /** Further packs checked the same way (the self-test's own {@code tfpitest} pack). */
    private final List<CraftEngineIssueMatcher> otherPacks;
    private final AtomicInteger threadCount = new AtomicInteger();
    private final AtomicInteger ceCount = new AtomicInteger();
    private final List<String> threadLines = new ArrayList<>();
    private final List<String> ceLines = new ArrayList<>();
    private volatile String source = "none";

    public LogFindings(ThreadAssertMatcher threads, CraftEngineIssueMatcher craftEngine,
            CraftEngineIssueMatcher... otherPacks) {
        this.threads = threads;
        this.craftEngine = craftEngine;
        this.otherPacks = List.of(otherPacks);
    }

    /** The CraftEngine namespaces whose issues are counted ({@code tfp} first). */
    public List<String> namespaces() {
        List<String> out = new ArrayList<>();
        out.add(craftEngine.namespace());
        otherPacks.forEach(m -> out.add(m.namespace()));
        return out;
    }

    /** Where the log events came from (shown in the report). */
    public void source(String source) {
        this.source = source;
    }

    public String source() {
        return source;
    }

    /**
     * One log event.
     *
     * @param level   logger level name
     * @param message formatted message (may start with Paper's {@code [Logger] } prefix)
     * @param thrown  attached exception, or null
     */
    public void accept(String level, String message, Throwable thrown) {
        StringBuilder extra = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
            extra.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
            if (extra.length() > 8192) {
                break;
            }
        }
        accept(level, message, extra.toString());
    }

    /** One log event whose exception is already text ({@code extra}: stack trace lines, may be empty). */
    public void accept(String level, String message, String extra) {
        String msg = message == null ? "" : message;
        Optional<String> rule = threads.match(extra.isEmpty() ? msg : msg + "\n" + extra);
        if (rule.isPresent()) {
            threadCount.incrementAndGet();
            keep(threadLines, "[" + rule.get() + "] " + firstLines(msg, extra));
        }
        String bare = LOGGER_PREFIX.matcher(msg).replaceFirst("");
        Optional<CraftEngineIssueMatcher.Issue> issue = craftEngine.match(level, bare);
        for (int i = 0; issue.isEmpty() && i < otherPacks.size(); i++) {
            issue = otherPacks.get(i).match(level, bare);
        }
        issue.ifPresent(found -> {
            ceCount.addAndGet(found.count());
            keep(ceLines, found.message());
        });
    }

    /** Feeds a whole log file's lines (Paper's {@code latest.log} layout; stack traces are continuation lines). */
    public void scan(List<String> lines) {
        String level = null;
        String message = null;
        StringBuilder extra = new StringBuilder();
        for (String line : lines) {
            Matcher m = FILE_LINE.matcher(line);
            if (m.matches()) {
                if (message != null) {
                    accept(level, message, extra.toString());
                }
                level = m.group(2);
                message = m.group(3);
                extra.setLength(0);
            } else if (message != null && extra.length() < 8192) {
                extra.append(line).append('\n');
            }
        }
        if (message != null) {
            accept(level, message, extra.toString());
        }
    }

    public int threadViolations() {
        return threadCount.get();
    }

    public int craftEngineIssues() {
        return ceCount.get();
    }

    /** The CraftEngine issue lines kept so far (at most {@value #KEEP}). */
    public List<String> craftEngineLines() {
        synchronized (ceLines) {
            return List.copyOf(ceLines);
        }
    }

    /** The two log cases for the report. */
    public List<CaseResult> results() {
        return List.of(
                result("server log has no thread-ownership violations", threadCount.get(), "thread-ownership violation(s)",
                        threadLines),
                result("server log has no CraftEngine issues for '" + String.join("', '", namespaces()) + "' (M1-INT-02)",
                        ceCount.get(), "CraftEngine issue(s) for our pack", ceLines));
    }

    private CaseResult result(String name, int count, String what, List<String> lines) {
        if (count == 0) {
            return CaseResult.passed(name, "none (log source: " + source + ")", Duration.ZERO);
        }
        List<String> copy;
        synchronized (lines) {
            copy = List.copyOf(lines);
        }
        return CaseResult.failed(name, count + " " + what + " (log source: " + source + ")", String.join("\n", copy),
                Duration.ZERO);
    }

    private static void keep(List<String> lines, String line) {
        synchronized (lines) {
            if (lines.size() < KEEP) {
                lines.add(line);
            }
        }
    }

    private static String firstLines(String msg, String extra) {
        String s = extra.isEmpty() ? msg : msg + " | " + extra.lines().limit(3).reduce((a, b) -> a + " | " + b).orElse("");
        return s.length() > 600 ? s.substring(0, 600) + "..." : s;
    }
}
