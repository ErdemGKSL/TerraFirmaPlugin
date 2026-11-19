/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.deposit;

/** What a soil disc writes in place of a {@link DepositMaterial} (the {@code with} values, variant dropped). */
public enum DepositKind {
    /** {@code tfc:deposit/<mineral>/<rock>}: gravel with a native metal or cassiterite. */
    ORE_DEPOSIT,
    /** {@code tfc:clay/<soil>}. */
    CLAY,
    /** {@code tfc:clay_grass/<soil>}. */
    CLAY_GRASS,
    /** {@code tfc:peat}. */
    PEAT,
    /** {@code tfc:peat_grass}. */
    PEAT_GRASS,
    /** {@code tfc:rooted_dirt/<soil>}: the soil variant of the dirt or grass it replaces (PAR-WORLD-09). */
    ROOTED_DIRT,
    /** {@code minecraft:powder_snow} ({@link PowderSnow}, PAR-WORLD-09). */
    POWDER_SNOW,
    /**
     * The same soil block in the mollisol variant, when its soil is entisol, aridisol or oxisol ({@link MollisolDiscs},
     * PAR-WORLD-09). The terrain does not carry the soil variant, so the writer resolves it.
     */
    MOLLISOL
}
