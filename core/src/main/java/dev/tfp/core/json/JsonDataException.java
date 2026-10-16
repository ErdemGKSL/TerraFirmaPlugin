/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

/** A data file did not match its schema. The message starts with the file and JSON path. */
public final class JsonDataException extends RuntimeException {
    private final String location;
    private final String problem;

    public JsonDataException(String location, String problem) {
        super(location + ": " + problem);
        this.location = location;
        this.problem = problem;
    }

    /** {@code <source> $.a.b[2]}. */
    public String location() {
        return location;
    }

    public String problem() {
        return problem;
    }
}
