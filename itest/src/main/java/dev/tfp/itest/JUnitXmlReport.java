/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the self-test results as a JUnit XML report ({@code selftest-report.xml}): one {@code <testsuite>}
 * root, so CI tools read it and {@code :itest:runSelfTest} can sum its {@code failures}/{@code errors}.
 */
public final class JUnitXmlReport {
    private JUnitXmlReport() {
    }

    public static String toXml(String suite, List<CaseResult> results, Map<String, String> properties, Instant timestamp) {
        long failures = results.stream().filter(r -> r.status() == CaseResult.Status.FAILED).count();
        long errors = results.stream().filter(r -> r.status() == CaseResult.Status.ERROR).count();
        long skipped = results.stream().filter(r -> r.status() == CaseResult.Status.SKIPPED).count();
        Duration total = results.stream().map(CaseResult::time).reduce(Duration.ZERO, Duration::plus);
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"").append(attr(suite)).append("\" tests=\"").append(results.size())
                .append("\" failures=\"").append(failures).append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped).append("\" time=\"").append(seconds(total))
                .append("\" timestamp=\"").append(timestamp.truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", ""))
                .append("\">\n");
        if (!properties.isEmpty()) {
            sb.append("  <properties>\n");
            properties.forEach((k, v) -> sb.append("    <property name=\"").append(attr(k)).append("\" value=\"")
                    .append(attr(v)).append("\"/>\n"));
            sb.append("  </properties>\n");
        }
        for (CaseResult r : results) {
            sb.append("  <testcase classname=\"").append(attr(suite)).append("\" name=\"").append(attr(r.name()))
                    .append("\" time=\"").append(seconds(r.time())).append('"');
            switch (r.status()) {
                case PASSED -> {
                    if (r.message().isEmpty() && r.detail().isEmpty()) {
                        sb.append("/>\n");
                        continue;
                    }
                    sb.append(">\n    <system-out>").append(text(join(r.message(), r.detail()))).append("</system-out>\n");
                }
                case FAILED -> sb.append(">\n    <failure message=\"").append(attr(r.message()))
                        .append("\" type=\"AssertionError\">").append(text(join(r.message(), r.detail()))).append("</failure>\n");
                case ERROR -> sb.append(">\n    <error message=\"").append(attr(r.message())).append("\">")
                        .append(text(join(r.message(), r.detail()))).append("</error>\n");
                case SKIPPED -> sb.append(">\n    <skipped message=\"").append(attr(r.message())).append("\"/>\n");
            }
            sb.append("  </testcase>\n");
        }
        sb.append("</testsuite>\n");
        return sb.toString();
    }

    /** Writes atomically (temp file + move), so a reader never sees half a report. */
    public static void write(Path file, String xml) throws IOException {
        Path abs = file.toAbsolutePath();
        Files.createDirectories(abs.getParent());
        Path tmp = Files.createTempFile(abs.getParent(), abs.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, xml, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String join(String a, String b) {
        return b.isEmpty() ? a : a.isEmpty() ? b : a + "\n" + b;
    }

    private static String seconds(Duration d) {
        return String.format(Locale.ROOT, "%.3f", d.toNanos() / 1e9);
    }

    static String attr(String s) {
        return text(s).replace("\n", "&#10;");
    }

    static String text(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                // also in text: keeps log lines like failures="1" from reaching runSelfTest's attribute check
                case '"' -> sb.append("&quot;");
                default -> {
                    // XML 1.0 forbids most control characters (ANSI colour codes in log lines, for example).
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r' || c == 0xFFFE || c == 0xFFFF) {
                        sb.append('?');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
