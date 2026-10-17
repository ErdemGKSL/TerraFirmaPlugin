/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import java.util.Locale;

/** The gems of the decorated fissures and hot springs ({@code tfc:ore/<gem>/<rock>}). */
public enum Gem {
    TOPAZ,
    DIAMOND,
    SAPPHIRE,
    EMERALD;

    /** The gem's id segment, e.g. {@code topaz}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
