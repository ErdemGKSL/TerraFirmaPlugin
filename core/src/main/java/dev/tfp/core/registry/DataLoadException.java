/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import java.util.List;

/** The data load failed. Carries every problem found (the loader does not stop at the first). */
public final class DataLoadException extends RuntimeException {
    private static final int SHOWN = 50;

    private final List<String> errors;

    public DataLoadException(List<String> errors) {
        super(message(errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }

    private static String message(List<String> errors) {
        StringBuilder sb = new StringBuilder(errors.size() + " data error(s):");
        errors.stream().limit(SHOWN).forEach(e -> sb.append("\n  - ").append(e));
        if (errors.size() > SHOWN) {
            sb.append("\n  ... and ").append(errors.size() - SHOWN).append(" more");
        }
        return sb.toString();
    }
}
