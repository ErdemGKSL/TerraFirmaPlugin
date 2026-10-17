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
    PEAT_GRASS
}
