/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (the names and their order only)
 */
package dev.tfp.core.worldgen.tree;

import java.util.Locale;

/** TFC's 20 wood types, in TFC's order. */
public enum Wood {
    ACACIA, ASH, ASPEN, BIRCH, BLACKWOOD, CHESTNUT, DOUGLAS_FIR, HICKORY, KAPOK, MANGROVE, MAPLE, OAK, PALM, PINE, ROSEWOOD,
    SEQUOIA, SPRUCE, SYCAMORE, WHITE_CEDAR, WILLOW;

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    /** The name in block ids, e.g. {@code douglas_fir} in {@code tfp:wood/log/douglas_fir}. */
    public String serializedName() {
        return serializedName;
    }

    /** The wood of a serialized name. */
    public static Wood byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
