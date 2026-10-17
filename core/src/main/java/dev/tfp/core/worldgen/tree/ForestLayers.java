/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/TFCLayers.java @ c3b4d9536 (createOverworldForestLayer), with
 * src/main/java/net/dries007/tfc/world/layer/ForestInitLayer.java, ForestRandomizeLayer.java, ForestEdgeLayer.java and
 * ForestRandomizeSmallLayer.java, and the forest type lookup of src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.layer.AdjacentTransformLayer;
import dev.tfp.core.worldgen.layer.AreaContext;
import dev.tfp.core.worldgen.layer.AreaFactory;
import dev.tfp.core.worldgen.layer.CenterTransformLayer;
import dev.tfp.core.worldgen.layer.ConcurrentArea;
import dev.tfp.core.worldgen.layer.SourceLayer;
import dev.tfp.core.worldgen.layer.ZoomLayer;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Random;

/**
 * TFC's forest layer: one {@link ForestType} per chunk, from a noise of forest or grassland that is randomized, zoomed, edged
 * and zoomed again. Thread-safe: the areas are per thread ({@link ConcurrentArea}).
 */
public final class ForestLayers {
    private final ConcurrentArea<ForestType> area;

    /** @param seed the forest layer seed: in TFC the chunk data generator's last {@code seed.next()} */
    public ForestLayers(long seed) {
        this.area = new ConcurrentArea<>(createOverworldForestLayer(seed), ForestType::valueOf);
    }

    /** The forest type of chunk {@code (chunkX, chunkZ)}, as {@code forestTypeLayer.get(blockX >> 4, blockZ >> 4)}. */
    public ForestType forestType(int chunkX, int chunkZ) {
        return area.get(chunkX, chunkZ);
    }

    /** {@code TFCLayers.createOverworldForestLayer}: the seed's {@link Random} draws the noise seed and each layer seed. */
    static AreaFactory createOverworldForestLayer(long seed) {
        final Random random = new Random(seed);
        AreaFactory layer;
        layer = new InitLayer(new OpenSimplex2D(random.nextInt()).spread(0.25f)).apply(random.nextLong());
        layer = RANDOMIZE.apply(random.nextLong(), layer);
        layer = ZoomLayer.FUZZY.apply(random.nextLong(), layer);
        layer = RANDOMIZE.apply(random.nextLong(), layer);
        layer = ZoomLayer.FUZZY.apply(random.nextLong(), layer);
        layer = ZoomLayer.NORMAL.apply(random.nextLong(), layer);
        layer = EDGE.apply(random.nextLong(), layer);
        layer = RANDOMIZE_SMALL.apply(random.nextLong(), layer);
        for (int i = 0; i < 2; i++) {
            layer = ZoomLayer.NORMAL.apply(random.nextLong(), layer);
        }
        return layer;
    }

    /** {@code ForestInitLayer}: secondary diverse forest where the noise is not negative, grassland elsewhere. */
    private record InitLayer(Noise2D forestBaseNoise) implements SourceLayer {
        @Override
        public int apply(AreaContext context, int x, int z) {
            final float noise = (float) forestBaseNoise.noise(x, z);
            return noise < 0 ? ForestType.GRASSLAND.ordinal() : ForestType.SECONDARY_DIVERSE.ordinal();
        }
    }

    /** {@code ForestRandomizeLayer}. */
    private static final CenterTransformLayer RANDOMIZE = (context, value) -> {
        final ForestType current = ForestType.valueOf(value);
        final XoroshiroRandomSource source = context.random();
        if (current.isNone()) {
            final int random = source.nextInt(20);
            if (random <= 2) {
                return ForestType.SHRUBLAND.ordinal();
            } else if (random <= 5) {
                return ForestType.savannaForestType(source);
            } else if (random == 6) {
                return ForestType.secondaryForestType(source);
            }
        } else if (current.isSavanna()) {
            final int random = source.nextInt(16);
            if (random <= 2) {
                return ForestType.secondaryForestType(source);
            } else if (random <= 6) {
                return ForestType.SPARSE.ordinal();
            } else if (random <= 8) {
                return ForestType.SHRUBLAND.ordinal();
            }
        } else if (current.isPrimary() || current.isSecondary()) {
            final int random = source.nextInt(24);
            if (random == 0) {
                return ForestType.SECONDARY_BAMBOO.ordinal();
            }
            if (random == 1 && !current.isSecondary()) {
                return ForestType.savannaForestType(source);
            } else if (random == 2) {
                return ForestType.SHRUBLAND.ordinal();
            } else if (random <= 6) {
                return ForestType.primaryForestType(source);
            }
        }
        return value;
    };

    /** {@code ForestEdgeLayer}: a full forest next to anything else becomes an edge forest. */
    private static final AdjacentTransformLayer EDGE = (context, north, east, south, west, center) -> {
        if (isFullForest(center)) {
            if (!isFullForest(north) || !isFullForest(east) || !isFullForest(south) || !isFullForest(west)) {
                return ForestType.edgeForestType(context.random());
            }
        }
        return center;
    };

    /** {@code ForestRandomizeSmallLayer}. */
    private static final CenterTransformLayer RANDOMIZE_SMALL = (context, value) -> {
        final ForestType current = ForestType.valueOf(value);
        if (current.isPrimary() || current.isSecondary()) {
            final int random = context.random().nextInt(current.isSecondary() ? 40 : 25);
            if (random == 0) {
                return ForestType.CLEARING.ordinal();
            } else if (random == 1) {
                return ForestType.SPARSE.ordinal();
            } else if (random == 3) {
                return ForestType.deadForestType(context.random());
            }
        } else if (current.isSavanna() || current.isNone()) {
            final int random = context.random().nextInt(30);
            if (random == 0) {
                return current.isSavanna() ? ForestType.secondaryForestType(context.random()) : ForestType.edgeForestType(context.random());
            }
        }
        if (context.random().nextInt(10) == 0) {
            return ForestType.valueOf(value).alternate().ordinal();
        }
        return value;
    };

    private static boolean isFullForest(int value) {
        final ForestType type = ForestType.valueOf(value);
        return type.isPrimary() || type.isSecondary();
    }
}
