/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.bettermodel;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a BetterModel event handler so it never throws. If a subscriber throws, BetterModel silently
 * unsubscribes <b>all</b> of our listeners for that event class ({@code bettermodel} skill), so every
 * body is caught and logged here, including {@link LinkageError}s from a mismatched BetterModel version.
 */
final class GuardedSubscriber {
    private GuardedSubscriber() {
    }

    static <T> Consumer<T> guard(Logger logger, String what, Consumer<? super T> handler) {
        return event -> {
            try {
                handler.accept(event);
            } catch (RuntimeException | LinkageError | AssertionError e) {
                logger.log(Level.SEVERE, "BetterModel " + what + " handler failed (kept subscribed)", e);
            }
        };
    }
}
