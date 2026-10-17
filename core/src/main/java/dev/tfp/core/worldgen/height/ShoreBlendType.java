/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/shore/ShoreBlendType.java @ c3b4d9536 (the constants in TFC's order)
 */
package dev.tfp.core.worldgen.height;

/** The shore shape of a biome. The ordinal is TFC's: the shore weights are summed per ordinal. */
public enum ShoreBlendType {
    NONE, // No shore modification
    CLASSIC, // Matches 1.20 beaches, simple cliffs with noisy sand below
    SANDY, // Typical monoslope
    EMBAYMENTS,
    UPPER_TERRACE,
    LOWER_TERRACE,
    SETBACK_CLIFFS,
    DUNES,
    ROCKY_SHORES,
    SEA_STACKS; // Monoslopes mixed with protruding rocks

    public static final ShoreBlendType[] ALL = values();
    public static final int SIZE = ALL.length;
}
