/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AquaticCritter.java @ c3b4d9536 (canSpawnIn) and
 * src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java (SALT_WATER)
 */
package dev.tfp.core.critter;

/**
 * The fluid a water critter is asked about. TFC matches a fluid with {@code isSame} against a source fluid, so a flowing
 * variant does not match: only the two source kinds are accepted.
 */
public enum CritterFluid {
    /** Vanilla {@code Fluids.WATER} source. */
    FRESH_SOURCE,
    /** Vanilla flowing water. */
    FRESH_FLOWING,
    /** TFC {@code SALT_WATER} source. */
    SALT_SOURCE,
    /** TFC salt water, flowing. */
    SALT_FLOWING,
    /** Any other fluid, such as lava. */
    OTHER
}
