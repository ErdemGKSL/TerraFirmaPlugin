/*
 * TerraFirmaPlugin
 * Copyright (c) 2026 TerraFirmaPlugin contributors
 * Licensed under the EUPL-1.2 (see LICENSE).
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
