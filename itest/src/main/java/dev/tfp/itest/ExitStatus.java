/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Makes the server process exit with the self-test's status (M1-TEST-01), so CI sees a failing run even
 * without reading the report. The server always exits 0 on a normal stop; a shutdown hook that waits for the
 * server thread to finish its stop (worlds saved, plugins disabled) and then halts with our code is the only
 * way to change that without patching the server. {@code :itest:runSelfTest} also checks the report.
 */
final class ExitStatus {
    /** Longest wait for the server thread before halting anyway. */
    static final Duration WAIT = Duration.ofSeconds(120);

    private ExitStatus() {
    }

    /** For {@code code != 0}: registers the hook. Code 0 changes nothing. */
    static void arrange(int code, Thread serverThread, Logger log) {
        if (code == 0) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("tfp-selftest-exit").unstarted(() -> {
            waitForStop(serverThread, WAIT);
            log.severe("[selftest] exiting with status " + code);
            Runtime.getRuntime().halt(code);
        }));
    }

    /** Waits until {@code serverThread} has ended or is inside {@code System.exit} (it then waits for the hooks). */
    static boolean waitForStop(Thread serverThread, Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!stopped(serverThread)) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    static boolean stopped(Thread t) {
        return t == null || !t.isAlive() || inExit(t.getStackTrace());
    }

    /** True if the stack is inside {@code System.exit}/{@code Runtime.exit} (blocked on the shutdown hooks). */
    static boolean inExit(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            String c = e.getClassName();
            if (c.equals("java.lang.Shutdown")
                    || (c.equals("java.lang.Runtime") || c.equals("java.lang.System")) && e.getMethodName().equals("exit")) {
                return true;
            }
        }
        return false;
    }
}
