/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scheduler;

/**
 * Thrown by {@link ThreadAsserts}. A distinct type (still an {@link IllegalStateException}) so the in-server
 * self-test ({@code :itest}) can recognise our thread-assertion failures in the server log by class name.
 */
public final class ThreadAssertionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public ThreadAssertionException(String message) {
        super(message);
    }
}
