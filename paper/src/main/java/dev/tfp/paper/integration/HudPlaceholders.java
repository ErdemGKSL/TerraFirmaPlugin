/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * The number placeholders we registered in the HUD plugin, evaluated through that plugin's own registry
 * (M1-INT-04): proves that a placeholder is registered under its name and reads the {@link HudSnapshot} of
 * the player, without the world. Used by diagnostics and the in-server self-test.
 */
public interface HudPlaceholders {
    /** Names of our number placeholders as the HUD plugin knows them (empty when nothing is registered). */
    Set<String> registered();

    /**
     * Evaluates the number placeholder {@code name} for {@code player} the way the HUD plugin would on its
     * update thread. Any thread (the functions only read the snapshot cache).
     *
     * @return empty if the HUD plugin has no placeholder of that name
     */
    OptionalDouble evaluate(String name, UUID player);

    /**
     * Text placeholders we registered (the date and season, M2-CAL-02). They read the published clock, never the
     * world. Empty by default, for a HUD plugin without text support.
     */
    default Set<String> registeredText() {
        return Set.of();
    }

    /** Evaluates the text placeholder {@code name} the way {@link #evaluate} does; empty if not registered. */
    default Optional<String> evaluateText(String name, UUID player) {
        return Optional.empty();
    }
}
