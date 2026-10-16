/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import java.time.Duration;
import java.util.Objects;

/**
 * One case's outcome, as written to the JUnit report.
 *
 * @param message one line: the failure/error/skip reason, or the pass detail (may be empty)
 * @param detail  longer text (stack trace, log lines); may be empty
 */
public record CaseResult(String name, Status status, String message, String detail, Duration time) {
    /** JUnit's four outcomes. */
    public enum Status {
        PASSED,
        /** An assertion did not hold (JUnit {@code <failure>}). */
        FAILED,
        /** The case itself broke (JUnit {@code <error>}). */
        ERROR,
        SKIPPED
    }

    public CaseResult {
        Objects.requireNonNull(name);
        Objects.requireNonNull(status);
        message = message == null ? "" : message;
        detail = detail == null ? "" : detail;
        time = time == null ? Duration.ZERO : time;
    }

    public static CaseResult passed(String name, String message, Duration time) {
        return new CaseResult(name, Status.PASSED, message, "", time);
    }

    public static CaseResult failed(String name, String message, String detail, Duration time) {
        return new CaseResult(name, Status.FAILED, message, detail, time);
    }

    public static CaseResult error(String name, String message, String detail, Duration time) {
        return new CaseResult(name, Status.ERROR, message, detail, time);
    }

    public static CaseResult skipped(String name, String reason) {
        return new CaseResult(name, Status.SKIPPED, reason, "", Duration.ZERO);
    }

    public boolean passed() {
        return status == Status.PASSED;
    }
}
