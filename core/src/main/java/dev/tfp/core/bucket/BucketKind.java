/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (WOODEN_BUCKET, RED_STEEL_BUCKET,
 *       BLUE_STEEL_BUCKET: the FluidContainerItem arguments), src/main/java/net/dries007/tfc/config/ServerConfig.java @
 *       c3b4d9536 (woodenBucketCanPlaceSources, metalBucketCanPlaceSources)
 */
package dev.tfp.core.bucket;

import dev.tfp.core.fluid.FluidContainerKind;
import java.util.Optional;

/**
 * TFC's three tank buckets ({@code FluidContainerItem}s). Every one has {@code canPlaceLiquidsInWorld = true}; whether it
 * places a source block or a transient flowing block is the server config ({@link BucketConfig}): the wooden bucket reads
 * {@code woodenBucketCanPlaceSources}, both metal buckets read {@code metalBucketCanPlaceSources}. None of them fills from
 * an infinite source ({@code allowInfiniteSourceFilling = false} in {@code FluidContainerItem.use}).
 */
public enum BucketKind {
    WOODEN("tfp:wooden_bucket", FluidContainerKind.WOODEN_BUCKET, false),
    RED_STEEL("tfp:metal/bucket/red_steel", FluidContainerKind.RED_STEEL_BUCKET, true),
    BLUE_STEEL("tfp:metal/bucket/blue_steel", FluidContainerKind.BLUE_STEEL_BUCKET, true);

    private final String itemId;
    private final FluidContainerKind container;
    private final boolean metal;

    BucketKind(String itemId, FluidContainerKind container, boolean metal) {
        this.itemId = itemId;
        this.container = container;
        this.metal = metal;
    }

    /** Our CraftEngine item id. */
    public String itemId() {
        return itemId;
    }

    /** The tank: capacity and {@code tfc:usable_in_*} tag. */
    public FluidContainerKind container() {
        return container;
    }

    /** Whether the bucket reads the metal bucket config keys. */
    public boolean metal() {
        return metal;
    }

    /** The bucket of an item id, empty for any other item. */
    public static Optional<BucketKind> byItem(String itemId) {
        for (BucketKind k : values()) {
            if (k.itemId.equals(itemId)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
