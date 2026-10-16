/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A {@code .bbmodel} registered with the entity-model plugin under {@code id}. The stream is opened
 * on the model plugin's own threads (possibly in parallel), so {@code open} must be thread-safe.
 */
public record ModelSource(String id, Opener open) {
    /** BetterModel's id rule. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_.]+");

    public ModelSource {
        Objects.requireNonNull(open, "open");
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("model id must match [a-z0-9_.]+, got '" + id + "'");
        }
    }

    /** Opens a fresh stream of the model JSON. */
    @FunctionalInterface
    public interface Opener {
        InputStream open() throws IOException;
    }
}
