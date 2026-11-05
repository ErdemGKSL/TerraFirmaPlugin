/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/HealthDisplayStyle.java @ c3b4d9536
 */
package dev.tfp.core.health;

import java.util.Locale;
import java.util.Optional;

/**
 * TFC's {@code healthDisplayStyle} client option: how the health bar text is written. The four formatters are TFC's
 * {@code String.format} patterns, applied to the health in TFC's attribute units (vanilla HP x the nutrition health
 * modifier, since TFC changes the max health attribute). TFC formats with the client's default locale; ours always uses
 * {@link Locale#ROOT} (decimal point {@code .}), as every HUD placeholder does (see the ledger).
 */
public enum HealthDisplayStyle {
    /** {@code %.0f/%.0f} of health x 50, TFC's default ({@code 750/1000}). */
    TFC,
    /** {@code %.1f/%.1f} of the health ({@code 15.0/20.0}). */
    VANILLA,
    /** {@code %.0f} of health x 50 ({@code 750}). */
    TFC_CURRENT,
    /** {@code %.1f} of the health ({@code 15.0}). */
    VANILLA_CURRENT;

    /** TFC's {@code ClientConfig} default. */
    public static final HealthDisplayStyle DEFAULT = TFC;

    /** TFC {@code HealthDisplayStyle.format}: {@code currentHealth} and {@code maxHealth} in TFC's attribute units. */
    public String format(float currentHealth, float maxHealth) {
        return switch (this) {
            case TFC -> String.format(Locale.ROOT, "%.0f/%.0f", currentHealth * 50, maxHealth * 50);
            case VANILLA -> String.format(Locale.ROOT, "%.1f/%.1f", currentHealth, maxHealth);
            case TFC_CURRENT -> String.format(Locale.ROOT, "%.0f", currentHealth * 50);
            case VANILLA_CURRENT -> String.format(Locale.ROOT, "%.1f", currentHealth);
        };
    }

    /**
     * The text of a {@link HealthDisplay} (whose values are already on TFC's x 50 scale): the values are turned back into
     * attribute units with {@link HealthConfig#displayUnitsPerHp()} and formatted as TFC does.
     */
    public String format(HealthDisplay display, HealthConfig config) {
        float units = config.displayUnitsPerHp();
        return format(display.health() / units, display.maxHealth() / units);
    }

    /** The style named {@code name} (TFC's enum constant, case and {@code -}/{@code _} insensitive), if any. */
    public static Optional<HealthDisplayStyle> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String n = name.strip().replace('-', '_').toUpperCase(Locale.ROOT);
        for (HealthDisplayStyle s : values()) {
            if (s.name().equals(n)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
