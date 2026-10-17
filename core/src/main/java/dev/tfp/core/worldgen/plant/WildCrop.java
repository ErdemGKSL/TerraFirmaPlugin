/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java @ c3b4d9536 (which wild block each crop creates), with
 * the canSurvive rules of WildCropBlock, WildDoubleCropBlock (doubleBlockSurvives) and FloodedWildCropBlock in the same
 * package, WildSpreadingCropBlock (getFruit) and the tag src/generated/resources/data/tfc/tags/block/wild_crop_grows_on.json
 */
package dev.tfp.core.worldgen.plant;

import java.util.Map;

/** The wild crop blocks of TFC's crops ({@code tfp:wild_crop/<crop>}): which block class each one is. */
public enum WildCrop {
    /** {@code WildCropBlock}: one block on {@code tfc:wild_crop_grows_on}. */
    DEFAULT,
    /** {@code WildDoubleCropBlock}: a bottom and a top block. */
    DOUBLE,
    /** {@code WildSpreadingCropBlock}: one block with a fruit block beside it. */
    SPREADING,
    /** {@code FloodedWildCropBlock}: one block in water with no fluid above. */
    FLOODED;

    private static final Map<String, WildCrop> CROPS = Map.ofEntries(
        Map.entry("cassava", DEFAULT), Map.entry("green_bean", DOUBLE), Map.entry("lentil", DEFAULT), Map.entry("peanut", DEFAULT),
        Map.entry("soybean", DEFAULT), Map.entry("barley", DEFAULT), Map.entry("oat", DEFAULT), Map.entry("rye", DEFAULT),
        Map.entry("maize", DOUBLE), Map.entry("wheat", DEFAULT), Map.entry("rice", FLOODED), Map.entry("beet", DEFAULT),
        Map.entry("cabbage", DEFAULT), Map.entry("carrot", DEFAULT), Map.entry("garlic", DEFAULT), Map.entry("onion", DEFAULT),
        Map.entry("potato", DEFAULT), Map.entry("squash", DEFAULT), Map.entry("tomato", DOUBLE),
        Map.entry("red_bell_pepper", DEFAULT), Map.entry("yellow_bell_pepper", DEFAULT), Map.entry("pumpkin", SPREADING),
        Map.entry("melon", SPREADING), Map.entry("canola", DEFAULT), Map.entry("radish", DEFAULT), Map.entry("alfalfa", DEFAULT),
        Map.entry("jute", DOUBLE), Map.entry("papyrus", DOUBLE), Map.entry("sugarcane", DOUBLE));

    /** The kind of a wild crop id ({@code tfp:wild_crop/wheat}), or null when it is not one. */
    public static WildCrop of(String id) {
        final String prefix = PlantState.NS + "wild_crop/";
        return id.startsWith(prefix) ? CROPS.get(id.substring(prefix.length())) : null;
    }

    /** {@code WildSpreadingCropBlock.getFruit}: the fruit block of a spreading crop ({@code tfp:pumpkin}, {@code tfp:melon}). */
    public static String fruit(String id) {
        return PlantState.NS + id.substring(id.lastIndexOf('/') + 1);
    }
}
