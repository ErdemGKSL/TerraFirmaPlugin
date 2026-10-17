/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/TFCLayers.java @ c3b4d9536 (createOverworldRockLayer), with
 * src/main/java/net/dries007/tfc/world/layer/RegionLayer.java and RegionRockLayer.java
 */
package dev.tfp.core.worldgen.chunk;

import dev.tfp.core.worldgen.layer.AreaContext;
import dev.tfp.core.worldgen.layer.AreaFactory;
import dev.tfp.core.worldgen.layer.SmoothLayer;
import dev.tfp.core.worldgen.layer.TypedArea;
import dev.tfp.core.worldgen.layer.TypedAreaFactory;
import dev.tfp.core.worldgen.layer.TypedSourceLayer;
import dev.tfp.core.worldgen.layer.Area;
import dev.tfp.core.worldgen.layer.ZoomLayer;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.Units;
import java.util.Random;

/**
 * The rock id field at block scale: the region's rock ids at grid scale (128 blocks per cell), zoomed down to one id per
 * block column and smoothed. Its ids are the {@link Region.Point#rock} values, with the rock layer seed in the upper bits and
 * the supertype in the lower two. Built per thread (the areas are not synchronised).
 */
final class RockLayers {
    private RockLayers() {}

    /** {@code TFCLayers.createOverworldRockLayer}: the seed's {@link Random} draws the region layer seed, as in TFC. */
    static AreaFactory createOverworldRockLayer(RegionGenerator generator, long seed) {
        final Random random = new Random(seed);
        final TypedAreaFactory<Region.Point> regionLayer = new RegionLayer(generator).apply(random.nextLong());

        AreaFactory layer = RegionRockLayer.apply(regionLayer); // Grid scale (128x)
        for (int i = 0; i < Units.GRID_BITS - 1; i++) {
            layer = ZoomLayer.NORMAL.apply(seed, layer);
        }
        layer = SmoothLayer.INSTANCE.apply(seed, layer);
        layer = ZoomLayer.NORMAL.apply(seed, layer);
        layer = SmoothLayer.INSTANCE.apply(seed, layer);
        return layer;
    }

    /** {@code RegionLayer}: the region point at each grid coordinate. */
    private record RegionLayer(RegionGenerator generator) implements TypedSourceLayer<Region.Point> {
        @Override
        public Region.Point apply(AreaContext context, int x, int z) {
            return generator.getOrCreateRegionPoint(x, z);
        }
    }

    /** {@code RegionRockLayer}: the rock id of each region point. */
    private static final class RegionRockLayer {
        static AreaFactory apply(TypedAreaFactory<Region.Point> prev) {
            return () -> {
                final TypedArea<Region.Point> prevArea = prev.get();
                return new Area((x, z) -> prevArea.get(x, z).rock, 1024);
            };
        }
    }
}
