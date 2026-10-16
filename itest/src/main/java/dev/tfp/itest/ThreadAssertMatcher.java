/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognises thread-ownership violations in server log text (message plus exception lines). Pure.
 *
 * <ul>
 *   <li>our {@code ThreadAsserts} ({@code dev.tfp.paper.scheduler.ThreadAssertionException}, and its message
 *       texts in case the class name is not printed);</li>
 *   <li>Folia/Paper's {@code TickThread} checks: {@code "Thread failed main thread check: ..."} thrown and
 *       {@code "Thread <name> failed main thread check: ..."} logged (strings of
 *       {@code ca.spottedleaf.moonrise.common.util.TickThread} and {@code org.spigotmc.AsyncCatcher} in the Folia
 *       1.21.11 server jar);</li>
 *   <li>Spigot's {@code AsyncCatcher}: {@code IllegalStateException: Asynchronous <what>!}.</li>
 * </ul>
 */
public final class ThreadAssertMatcher {
    /** A named rule. */
    record Rule(String name, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("tfp ThreadAsserts", Pattern.compile("\\bThreadAssertionException\\b")),
            new Rule("tfp ThreadAsserts", Pattern.compile(
                    "IllegalStateException: (?:not region owner of |not owner of entity |blocking/heavy work on tick thread"
                            + "|expected a tfp worker thread)")),
            new Rule("Folia/Paper tick-thread check", Pattern.compile("failed main thread check")),
            new Rule("AsyncCatcher", Pattern.compile("IllegalStateException: Asynchronous [^\\n]*!")));

    /** @return the rule name if {@code text} reports a thread-ownership violation */
    public Optional<String> match(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        for (Rule r : RULES) {
            if (r.pattern().matcher(text).find()) {
                return Optional.of(r.name());
            }
        }
        return Optional.empty();
    }
}
