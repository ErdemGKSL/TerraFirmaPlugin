/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java @ c3b4d9536 (chest rule)
 * file: src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536 (small vessel rule)
 * file: src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (large vessel rule)
 * file: src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536 (placed rule)
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (barrel rule)
 * file: src/main/java/net/dries007/tfc/common/items/TFCBundleItem.java @ c3b4d9536 (bundle rule)
 */
package dev.tfp.core.item;

/**
 * Which items each TFC container accepts, from the size and weight of the item. Pure functions of the item's
 * {@link ItemSizing} and the {@link ItemSizeLimits}; the Paper layer calls these in its insert checks.
 */
public final class ContainerRules {
    /** Where a dropped item goes when placed on the ground with the placement key. */
    public enum PlacedSlot {
        /** One of four group slots. */
        GROUP,
        /** The single centre slot, holding a large item alone. */
        SINGLE,
        /** Not placeable. */
        NONE
    }

    private ContainerRules() {
    }

    /** TFC {@code TFCChestBlockEntity.isItemValid}: size at most the chest limit. */
    public static boolean chest(ItemSizing item, ItemSizeLimits limits) {
        return item.size().isEqualOrSmallerThan(limits.chest());
    }

    /** TFC {@code VesselItem.canContainItem}: size at most the small vessel limit. */
    public static boolean smallVessel(ItemSizing item, ItemSizeLimits limits) {
        return item.size().isEqualOrSmallerThan(limits.smallVessel());
    }

    /** TFC {@code LargeVesselBlockEntity.isItemValid}: strictly smaller than {@code LARGE}, not configurable. */
    public static boolean largeVessel(ItemSizing item) {
        return item.size().isSmallerThan(ItemSize.LARGE);
    }

    /**
     * TFC {@code PlacedItemBlockEntity.insertItem}: an item at most the group limit goes in a group slot; an item above
     * it but at most the single limit goes in the centre slot. TFC also requires the block to be empty of other items for the centre slot; the Paper layer checks that.
     */
    public static PlacedSlot placed(ItemSizing item, ItemSizeLimits limits) {
        if (item.size().isEqualOrSmallerThan(limits.placedGroup())) {
            return PlacedSlot.GROUP;
        }
        if (item.size().isEqualOrSmallerThan(limits.placedSingle())) {
            return PlacedSlot.SINGLE;
        }
        return PlacedSlot.NONE;
    }

    /**
     * TFC {@code BarrelBlockEntity.isItemValid}: the barrel refuses only items that are both huge and very heavy, so
     * that unfired large vessels can still be used as recipe inputs.
     */
    public static boolean barrel(ItemSizing item) {
        return item.size().isSmallerThan(ItemSize.HUGE) || item.weight().isSmallerThan(ItemWeight.VERY_HEAVY);
    }

    /** TFC {@code TFCBundleItem.overrideOtherStackedOnMe}: a bundle takes only light items no larger than normal. */
    public static boolean bundle(ItemSizing item) {
        return item.weight().isSmallerThan(ItemWeight.MEDIUM) && item.size().isSmallerThan(ItemSize.LARGE);
    }
}
