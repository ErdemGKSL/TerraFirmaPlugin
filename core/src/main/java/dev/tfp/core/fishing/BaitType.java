/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/BaitType.java @ c3b4d9536
 */
package dev.tfp.core.fishing;

/**
 * The bait a fishing rod carries, TFC {@code BaitType}. The caller reads the item tags: {@code tfc:small_fishing_bait} (seeds and
 * shellfish) and {@code tfc:large_fishing_bait} (raw fish). Immutable enum; no state.
 */
public enum BaitType {
    /** No bait, or an item that is no bait. */
    NONE,
    /** Bait a small fish takes. */
    SMALL,
    /** Bait a large fish takes, and that only the rods of the large tier can hold. */
    LARGE;

    /**
     * The type of one bait item. An empty stack is {@link #NONE}: pass both flags false.
     *
     * <p>Small is checked first, as TFC does, so an item in both tags is {@link #SMALL}.
     *
     * @param inSmallBaitTag whether the item is in {@code tfc:small_fishing_bait}
     * @param inLargeBaitTag whether the item is in {@code tfc:large_fishing_bait}
     * @return the bait type
     */
    public static BaitType of(boolean inSmallBaitTag, boolean inLargeBaitTag) {
        if (inSmallBaitTag) {
            return SMALL;
        }
        return inLargeBaitTag ? LARGE : NONE;
    }
}
