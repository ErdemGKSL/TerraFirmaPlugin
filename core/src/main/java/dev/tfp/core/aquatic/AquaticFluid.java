/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java and src/generated/resources/data/tfc/tags/fluid/*.json @ c3b4d9536
 */
package dev.tfp.core.aquatic;

/**
 * The water a position holds, as the aquatic rules tell it apart. The Paper glue maps Minecraft's fluid to one of these: a
 * flowing fluid is the same as its source (TFC's {@code isSame} and the fluid tags both count the two), so both map to one
 * constant. Air, lava and any other fluid are {@link #NOT_WATER}.
 *
 * <p>TFC's river water is its own fluid, not vanilla water, so a fish that needs vanilla water does not spawn in it.
 */
public enum AquaticFluid {
    /** Vanilla {@code minecraft:water}, source or flowing. */
    VANILLA_WATER,
    /** {@code tfc:river_water}, source or flowing. */
    RIVER_WATER,
    /** {@code tfc:salt_water}, source or flowing. */
    SALT_WATER,
    /** {@code tfc:spring_water}, source or flowing. */
    SPRING_WATER,
    /** Anything else, including no fluid at all. */
    NOT_WATER
}
