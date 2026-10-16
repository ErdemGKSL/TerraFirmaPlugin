/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.diagnostics;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises CraftEngine log lines that report a configuration problem in our pack (M1-INT-02,
 * {@code craftengine-config-generation} skill "Load pipeline and validation"). CraftEngine reports
 * config errors only in its log, as {@code Found <n> issue(s) in file '<path>':} followed by
 * {@code [k] <node> - <message>} lines; neither its reload event nor its API carries them.
 *
 * <p>Pure function over (level, message). A log appender feeds it; the self-test harness asserts that
 * nothing matched for our namespace.
 */
public final class CraftEngineIssueMatcher {
    /** CraftEngine's {@code resource.errors_detected} message (en). */
    private static final Pattern FOUND = Pattern.compile("Found (\\d+) issue(?:\\(s\\)|s)? in file '([^']*)'");

    /** What kind of line matched. */
    public enum Kind {
        /** {@code Found N issue(s) in file '...'} for a file of our pack. */
        FILE_ISSUES,
        /** A warning or error line that names one of our ids ({@code tfp:...}). */
        NAMESPACED_WARNING
    }

    /**
     * @param kind which rule matched
     * @param count issues reported by the line (N for {@link Kind#FILE_ISSUES}, 1 otherwise)
     * @param file the reported file, or {@code null}
     * @param message the original message
     */
    public record Issue(Kind kind, int count, String file, String message) {
    }

    private final String namespace;
    private final Pattern packPath;
    private final Pattern namespacedId;

    /** @param namespace our CraftEngine namespace and pack folder name ({@code tfp}) */
    public CraftEngineIssueMatcher(String namespace) {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("bad namespace '" + namespace + "'");
        }
        this.namespace = namespace;
        String ns = Pattern.quote(namespace);
        // plugins/CraftEngine/resources/<ns>/... (absolute or relative), or <ns>/... relative to resources/.
        // Matched after normalising '\\' to '/'.
        this.packPath = Pattern.compile("(?:^|/)resources/" + ns + "/|^" + ns + "/");
        this.namespacedId = Pattern.compile("(?<![a-z0-9_-])" + ns + ":[a-z0-9_./-]");
    }

    public String namespace() {
        return namespace;
    }

    /**
     * Classifies one log event.
     *
     * @param level the logger level name ({@code WARN}, {@code WARNING}, {@code ERROR}, {@code SEVERE},
     *     {@code INFO}, ...); case-insensitive
     * @param message the formatted message, without the logger prefix
     * @return the issue, or empty if the line is not about our pack
     */
    public Optional<Issue> match(String level, String message) {
        if (message == null || message.isEmpty()) {
            return Optional.empty();
        }
        Matcher found = FOUND.matcher(message);
        if (found.find()) {
            String file = found.group(2);
            if (packPath.matcher(file.replace('\\', '/')).find()) {
                return Optional.of(new Issue(Kind.FILE_ISSUES, Integer.parseInt(found.group(1)), file, message));
            }
            return Optional.empty();
        }
        if (isWarningOrWorse(level) && namespacedId.matcher(message).find()) {
            return Optional.of(new Issue(Kind.NAMESPACED_WARNING, 1, null, message));
        }
        return Optional.empty();
    }

    static boolean isWarningOrWorse(String level) {
        if (level == null) {
            return false;
        }
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "WARN", "WARNING", "ERROR", "SEVERE", "FATAL" -> true;
            default -> false;
        };
    }
}
