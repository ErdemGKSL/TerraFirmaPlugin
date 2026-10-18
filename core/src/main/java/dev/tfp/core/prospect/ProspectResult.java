/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PropickItem.java @ c3b4d9536
 */
package dev.tfp.core.prospect;

/**
 * The result of one prospector pick use, as TFC's {@code ProspectResult}. The order is TFC's, so {@link #ordinal()} matches the
 * ordinal TFC sends in its packet. The rating tiers come from the amount of a block found in the scan area; {@link #FOUND} is a direct
 * hit on a prospectable block, and {@link #NOTHING} is a miss, including a false negative.
 */
public enum ProspectResult {
    VERY_LARGE,
    LARGE,
    MEDIUM,
    SMALL,
    TRACES,
    FOUND,
    NOTHING
}
