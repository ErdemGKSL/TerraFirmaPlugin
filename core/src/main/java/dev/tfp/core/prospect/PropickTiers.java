/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (level per metal)
 */
package dev.tfp.core.prospect;

import java.util.Map;

/** The {@code LevelTier.level()} of each metal, which sets the prospector pick's false negative chance. */
public final class PropickTiers {
    private static final Map<String, Integer> LEVEL = Map.ofEntries(
        Map.entry("copper", 1), Map.entry("bronze", 2), Map.entry("bismuth_bronze", 2), Map.entry("black_bronze", 2),
        Map.entry("wrought_iron", 3), Map.entry("steel", 4), Map.entry("black_steel", 5), Map.entry("blue_steel", 6),
        Map.entry("red_steel", 6));

    private PropickTiers() {
    }

    /** The level of a metal; 0 for an unknown one. */
    public static int level(String metal) {
        return LEVEL.getOrDefault(metal, 0);
    }

    /** The metal of a prospector's pick item id ({@code tfp:metal/propick/copper}), or null for another item. */
    public static String metalOf(String itemId) {
        final String prefix = "tfp:metal/propick/";
        return itemId != null && itemId.startsWith(prefix) ? itemId.substring(prefix.length()) : null;
    }

    /**
     * The TFC translation key of the block name a result names: {@code tfp:ore/normal_native_copper/granite} gives
     * {@code block.tfc.ore.normal_native_copper.granite.prospected}.
     */
    public static String prospectedKey(String blockId) {
        final int colon = blockId.indexOf(':');
        return "block.tfc." + blockId.substring(colon + 1).replace('/', '.') + ".prospected";
    }

    /** The TFC message key of a result ({@code tfc.tooltip.propick.found_traces}, ...). */
    public static String messageKey(ProspectResult result) {
        return "tfc.tooltip.propick." + switch (result) {
            case VERY_LARGE -> "found_very_large";
            case LARGE -> "found_large";
            case MEDIUM -> "found_medium";
            case SMALL -> "found_small";
            case TRACES -> "found_traces";
            case FOUND -> "found";
            case NOTHING -> "nothing";
        };
    }
}
