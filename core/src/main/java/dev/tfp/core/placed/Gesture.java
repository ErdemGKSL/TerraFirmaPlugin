/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.placed;

import java.util.Locale;
import java.util.Optional;

/**
 * Config {@code placed-items.gesture}: how a player places an item (TFC's key V has no vanilla-client equivalent,
 * NP-CLIENT-01). {@link #HOLD}: hold right-click for one second on a top face. {@link #SNEAK_DOUBLE_TAP}: start sneaking
 * twice within {@link SneakDoubleTap#WINDOW_MS}, then a right-click on a top face places at once (touch clients, where a
 * long press is a break). {@link #BOTH} (the default) allows either.
 */
public enum Gesture {
    HOLD,
    SNEAK_DOUBLE_TAP,
    BOTH;

    public static final Gesture DEFAULT = BOTH;

    /** The config value ({@code hold}, {@code sneak-double-tap}, {@code both}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The gesture of a config value; empty for an unknown one. */
    public static Optional<Gesture> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String norm = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (Gesture g : values()) {
            if (g.id().equals(norm)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    public boolean hold() {
        return this != SNEAK_DOUBLE_TAP;
    }

    public boolean doubleTap() {
        return this != HOLD;
    }
}
