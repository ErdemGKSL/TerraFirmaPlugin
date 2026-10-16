/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core;

/** Project-wide constants. */
public final class Tfp {
    /** Namespace of every id we register (blocks, items, recipes, packs). Provisional name. */
    public static final String NAMESPACE = "tfp";

    private Tfp() {
    }

    /** {@code tfp:<path>}. */
    public static String id(String path) {
        if (path.isEmpty() || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("path must be non-empty and unqualified: " + path);
        }
        return NAMESPACE + ':' + path;
    }
}
