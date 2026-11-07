/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (the "buckets" section)
 */
package dev.tfp.core.bucket;

import java.util.Map;

/**
 * TFC's bucket server config: whether the wooden and the metal buckets place source blocks. Both default to {@code false}
 * (TFC), so an emptied bucket places a transient flowing block that dries up. The capacities stay TFC's defaults
 * ({@code FluidContainerKind}); see the ledger.
 *
 * @param woodenBucketCanPlaceSources TFC {@code woodenBucketCanPlaceSources}
 * @param metalBucketCanPlaceSources  TFC {@code metalBucketCanPlaceSources}
 */
public record BucketConfig(boolean woodenBucketCanPlaceSources, boolean metalBucketCanPlaceSources) {
    /** TFC's defaults. */
    public static final BucketConfig DEFAULTS = new BucketConfig(false, false);
    /** Our config.yml section. */
    public static final String SECTION = "buckets";
    public static final String WOODEN_KEY = "wooden-bucket-can-place-sources";
    public static final String METAL_KEY = "metal-bucket-can-place-sources";

    /** Whether {@code kind} places source blocks. */
    public boolean canPlaceSources(BucketKind kind) {
        return kind.metal() ? metalBucketCanPlaceSources : woodenBucketCanPlaceSources;
    }

    /**
     * Reads the optional {@value #SECTION} section of a parsed config tree; a missing section, key or a non-boolean value
     * keeps TFC's default.
     */
    public static BucketConfig fromTree(Map<?, ?> root) {
        if (root == null || !(root.get(SECTION) instanceof Map<?, ?> section)) {
            return DEFAULTS;
        }
        return new BucketConfig(bool(section.get(WOODEN_KEY), DEFAULTS.woodenBucketCanPlaceSources),
                bool(section.get(METAL_KEY), DEFAULTS.metalBucketCanPlaceSources));
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }
}
