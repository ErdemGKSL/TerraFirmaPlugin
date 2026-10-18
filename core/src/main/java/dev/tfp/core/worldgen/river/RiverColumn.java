/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkHeightFiller.java @ c3b4d9536 (computeInitialRiverWeights,
 * adjustHeightForRiverContributions) and src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536
 * (calculateNoiseAtHeight: the river stage, the air threshold and the slide; updateLocalCaches: the river biome; fillColumn:
 * the river water rule), with BiomeNoiseSampler.SOLID and AIR_THRESHOLD.
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.Mth;
import java.util.Map;

/**
 * The river decision of one block column: the river-adjusted surface height, and per y whether the river leaves the block
 * solid or opens it (air, or water below the sea level). Pure and immutable.
 *
 * <p>TFC blends the samplers of every biome around the column by their weights: {@code SurfaceHeights} passes the column's
 * weights by blend type ({@link #sample(Map, double[], RiverInfo, int, int, double)}); the single-type form
 * {@link #sample(Map, RiverBlendType, RiverInfo, int, int, double)} gives one type the weight 1.
 * {@link #density} takes the biome and shore density as its input; {@link #opens} uses TFC's {@code SOLID} (0), and the
 * terrain ({@code SurfaceHeights.Column.density}) passes the carved lake density of the column's biome weights.
 */
public final class RiverColumn {
    /** {@code BiomeNoiseSampler.SOLID}: the density of solid terrain. */
    public static final double SOLID = 0;
    /** {@code BiomeNoiseSampler.AIR_THRESHOLD}: densities above it are air (or water). */
    public static final double AIR_THRESHOLD = 0.4;
    /** {@code TFCChunkGenerator.SEA_LEVEL_Y}. */
    public static final int SEA_LEVEL_Y = RiverNoise.SEA_LEVEL_Y;
    /** A river normalized square distance below this (and a surface at most one above sea level) makes the river biome. */
    public static final double RIVER_BIOME_NORM_DIST_SQ = 1.1;

    private final RiverInfo info;
    private final double[] weights; // by RiverBlendType ordinal; NONE = 1 when there is no river
    private final RiverNoiseSampler.Column[] columns; // by ordinal, null where the weight is 0
    private final double height;

    private RiverColumn(RiverInfo info, double[] weights, RiverNoiseSampler.Column[] columns, double height) {
        this.info = info;
        this.weights = weights;
        this.columns = columns;
        this.height = height;
    }

    /** The column of one biome's blend type at weight 1 (tests; the terrain blends every type by its biome weights). */
    public static RiverColumn sample(Map<RiverBlendType, RiverNoiseSampler> samplers, RiverBlendType type, RiverInfo info,
                                     int x, int z, double heightIn) {
        final double[] weights = new double[RiverBlendType.SIZE];
        weights[type.ordinal()] = 1;
        return sample(samplers, weights, info, x, z, heightIn);
    }

    /**
     * TFC's {@code adjustHeightForRiverContributions}: every blend type with a weight samples the column once, and the heights
     * are blended by the weights. Without a river the column keeps {@code heightIn} and only the {@code NONE} weight is set.
     *
     * @param weights the blend weights by {@link RiverBlendType#ordinal()} (TFC's {@code computeInitialRiverWeights}; copied)
     * @param info    the column's river, or null
     */
    public static RiverColumn sample(Map<RiverBlendType, RiverNoiseSampler> samplers, double[] weights, RiverInfo info,
                                     int x, int z, double heightIn) {
        if (info == null) {
            return none(heightIn);
        }
        final double[] w = weights.clone();
        final RiverNoiseSampler.Column[] columns = new RiverNoiseSampler.Column[RiverBlendType.SIZE];
        double riverBlendHeight = 0d;
        for (RiverBlendType type : RiverBlendType.ALL) {
            final double weight = w[type.ordinal()];
            if (type == RiverBlendType.NONE) {
                riverBlendHeight += weight * heightIn;
            } else if (weight > 0) {
                final RiverNoiseSampler.Column column = samplers.get(type).column(info, x, z, heightIn, weight);
                columns[type.ordinal()] = column;
                riverBlendHeight += weight * column.height();
            }
        }
        return new RiverColumn(info, w, columns, riverBlendHeight);
    }

    /**
     * The river blend weight of a type as TFC's density stage reads it: the biome weights summed by blend type, or 1 at
     * {@code NONE} and 0 elsewhere when the column has no river ({@code adjustHeightForRiverContributions}).
     */
    public double weight(RiverBlendType type) {
        if (info == null) {
            return type == RiverBlendType.NONE ? 1.0 : 0.0;
        }
        return weights[type.ordinal()];
    }

    /** @return the river of this column, or null. */
    public RiverInfo info() {
        return info;
    }

    /** @return {@code true} if a river is within reach of this column (only then can it change a block). */
    public boolean hasRiver() {
        return info != null;
    }

    /** @return the river-adjusted surface height (TFC's column height before the volcanoes). */
    public double height() {
        return height;
    }

    /** @return the integer surface height TFC keeps per column: {@code (int) height}. */
    public int surfaceHeight() {
        return (int) height;
    }

    /**
     * TFC's density at {@code y} from the river stage on: the river samplers modify {@code noiseIn} (the biome and shore density,
     * where 0 is solid and higher is air) and their weighted sum is added to {@code noiseIn} itself (TFC's
     * {@code calculateNoiseAtHeight} keeps the post-shore noise in its accumulator, so a column without a river counts it twice),
     * then {@code AIR_THRESHOLD - noise}, the slide above {@code surfaceHeight}, clamped to [-1, 1]. Positive is solid.
     *
     * @param surfaceHeight TFC's {@code heightNoiseValue}: the column's final integer surface height
     */
    public double density(int y, double noiseIn, int surfaceHeight) {
        // TFC does not reset the noise before the river stage: the weighted river densities are added to the post-shore noise
        double noise = noiseIn;
        if (info == null) {
            noise += 1.0 * noiseIn; // weight 1 at NONE
        } else {
            for (RiverBlendType type : RiverBlendType.ALL) {
                final double weight = weights[type.ordinal()];
                if (type == RiverBlendType.NONE) {
                    noise += weight * noiseIn;
                } else if (weight > 0) {
                    noise += weight * columns[type.ordinal()].noise(y, noiseIn);
                }
            }
        }
        noise = AIR_THRESHOLD - noise; // Positive noise = solid
        if (y > surfaceHeight) {
            // Slide down if we're above the expected height (TFC's heightNoiseValue is a double: the product is a double)
            noise -= (y - (double) surfaceHeight) * 0.2f;
        }
        return Mth.clamp(noise, -1, 1);
    }

    /**
     * Whether the river opens a block that the terrain holds solid at {@code y}: the density with solid terrain
     * ({@link #SOLID}) is not positive. Always false without a river. The plugin asks only for positions at or below its own
     * surface: TFC's slide keeps one more solid block above {@code surfaceHeight}, which the plugin (no terrain density yet) does not add.
     */
    public boolean opens(int y, int surfaceHeight) {
        return info != null && density(y, SOLID, surfaceHeight) <= 0;
    }

    /**
     * TFC's {@code updateLocalCaches}: a column whose biome has rivers becomes the river biome where its surface is at most one
     * above sea level and the river is near ({@link #RIVER_BIOME_NORM_DIST_SQ}).
     *
     * @param biomeId       the column's TFC biome id
     * @param surfaceHeight the column's final height (after the volcanoes)
     */
    public boolean isRiverBiome(int biomeId, double surfaceHeight) {
        return surfaceHeight <= SEA_LEVEL_Y + 1 && info != null && info.normDistSq() < RIVER_BIOME_NORM_DIST_SQ
            && RiverBiomes.hasRivers(biomeId);
    }

    /**
     * The fluid of an opened position: TFC forces the aquifers below the river near rivers, so an open position takes the
     * sea-level fluid: water below the sea level, air at or above it.
     */
    public static boolean isWater(int y) {
        return y < SEA_LEVEL_Y;
    }

    /**
     * TFC's {@code fillColumn} rule for river water: a water block of a flowing column at or above
     * {@code min(seaLevel - 4, surfaceHeight)} is river water with the column's flow; other water is still.
     */
    public static boolean isRiverWater(int y, Flow flow, int surfaceHeight) {
        return flow != Flow.NONE && y >= Math.min(SEA_LEVEL_Y - 4, surfaceHeight);
    }

    /** The column of no river: {@code heightIn} unchanged, nothing opened. */
    public static RiverColumn none(double heightIn) {
        return new RiverColumn(null, null, null, heightIn);
    }
}
