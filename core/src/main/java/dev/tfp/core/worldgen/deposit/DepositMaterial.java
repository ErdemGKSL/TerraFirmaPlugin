/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.deposit;

/**
 * The blocks a soil disc can replace, as the terrain holds them: TFC's per-rock and per-soil blocks with the variant dropped
 * (the {@code replace} keys of the disc's {@code states}).
 */
public enum DepositMaterial {
    /** {@code tfc:rock/raw/<rock>}. */
    RAW_ROCK,
    /** {@code tfc:rock/gravel/<rock>}. */
    GRAVEL,
    /** {@code tfc:dirt/<soil>}. */
    DIRT,
    /** {@code tfc:grass/<soil>}. */
    GRASS,
    /** {@code tfc:mud/<soil>}. */
    MUD
}
