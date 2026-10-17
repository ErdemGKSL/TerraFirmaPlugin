/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/Indicator.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.ore;

/**
 * The surface indicator of a vein type ({@code config.indicator}): the small ore pieces that a vein scatters on the ground
 * above it. Immutable.
 *
 * @param depth             an indicator is placed only when the ground is less than this far from the vein's highest ore
 * @param rarity            one column in {@code rarity} of the vein's ore columns tries an above-ground indicator; 0 never
 * @param undergroundRarity the chance of each underground try (not ported: there are no caves yet)
 * @param undergroundCount  the underground tries per ore column (not ported)
 * @param blocks            the weighted indicator blocks ({@code tfp:ore/small_<ore>})
 */
public record Indicator(int depth, int rarity, int undergroundRarity, int undergroundCount, Weighted<String> blocks) {
    public Indicator {
        if (depth <= 0 || undergroundRarity <= 0) {
            throw new IllegalArgumentException("indicator depth and underground_rarity must be positive");
        }
    }
}
