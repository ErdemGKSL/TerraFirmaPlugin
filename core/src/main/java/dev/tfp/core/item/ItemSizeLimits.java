/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (size limit defaults)
 */
package dev.tfp.core.item;

/**
 * The configurable size limits of TFC's {@code ServerConfig}, with TFC's defaults. Every limit is inclusive.
 *
 * @param chest {@code chestMaximumItemSize}
 * @param smallVessel {@code smallVesselMaximumItemSize}
 * @param placedGroup {@code maxPlacedItemSize}: the largest item that can be placed as four on the ground
 * @param placedSingle {@code maxPlacedLargeItemSize}: the largest item that can be placed alone on the ground
 */
public record ItemSizeLimits(ItemSize chest, ItemSize smallVessel, ItemSize placedGroup, ItemSize placedSingle) {
    /** TFC's default config values. */
    public static final ItemSizeLimits DEFAULT = new ItemSizeLimits(
        ItemSize.LARGE, ItemSize.SMALL, ItemSize.LARGE, ItemSize.HUGE);
}
