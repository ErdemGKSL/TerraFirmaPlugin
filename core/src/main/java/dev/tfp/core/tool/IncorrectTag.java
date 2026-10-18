/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (the INCORRECT_FOR_* tier tags)
 */
package dev.tfp.core.tool;

/**
 * The vanilla {@code incorrect_for_<tier>_tool} block tags that TFC's tiers use. A tier is incorrect for every block in its tag, so
 * such a block drops nothing when mined with that tier. A block may sit in several of these tags.
 */
public enum IncorrectTag {
    WOODEN,
    STONE,
    IRON,
    DIAMOND,
    NETHERITE
}
