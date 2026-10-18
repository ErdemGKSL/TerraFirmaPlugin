/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/BiomeNoise.java @ c3b4d9536 (undergroundLakes), with
 * src/main/java/net/dries007/tfc/world/ChunkHeightFiller.java @ c3b4d9536 (sampleColumnHeightAndBiome: the per-column weighted
 * map of biome noise samplers, the surface integrity depth), src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java
 * @ c3b4d9536 (calculateNoiseAtHeight: the biome stage, the shore stage weights and the forced no-carve zone) and
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (the biomes with carving(BiomeNoise::undergroundLakes)).
 */
package dev.tfp.core.worldgen.height;

import static dev.tfp.core.worldgen.chunk.ChunkData.SEA_LEVEL_Y;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.density.TerrainDensity;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;

/**
 * TFC's carved lakes in chunks: the density of the lake biomes built with {@code carving(BiomeNoise::undergroundLakes)} (the
 * mountain, plateau and subglacial lakes), blended by the column's biome weights, as TFC's terrain density stage sums
 * {@code sampler.noise(y) * weight} over every biome sampler of the column. Every other biome's density is {@code SOLID} (0),
 * so only the lake biomes add to the sum. The basins of the height-map lakes ({@code LAKE}, {@code RIFT_LAKE},
 * {@code MELTWATER_LAKE}, ...) are their height maps and need no carving.
 *
 * <p>All lake biomes build their sampler from the same level seed, so their noises are equal per column: a column keeps the
 * lake weights and one centre and depth. Pure and immutable; any thread.
 */
public final class LakeCarving {
    /** The water level of a carved lake: TFC fills it from the sea-level aquifer (the lake biomes' aquifer height is sea level - 16). */
    public static final int LAKE_LEVEL_Y = SEA_LEVEL_Y;

    private final Noise2D blobsNoise;
    private final Noise2D depthNoise;
    private final Noise2D centerNoise;

    /** The noises of {@code BiomeNoise.undergroundLakes(seed, heightNoise)} for the level seed. */
    public LakeCarving(long levelSeed) {
        this.blobsNoise = new OpenSimplex2D(levelSeed + 1).spread(0.04f).abs();
        this.depthNoise = new OpenSimplex2D(levelSeed + 2).octaves(4).scaled(2, 18).spread(0.2f);
        this.centerNoise = new OpenSimplex2D(levelSeed + 3).octaves(2).spread(0.06f).scaled(SEA_LEVEL_Y - 4, SEA_LEVEL_Y + 4);
    }

    /** @return whether TFC builds the biome with {@code carving(BiomeNoise::undergroundLakes)}. */
    public static boolean isCarvedLake(int biome) {
        return biome == TfcBiomes.MOUNTAIN_LAKE || biome == TfcBiomes.VOLCANIC_MOUNTAIN_LAKE || biome == TfcBiomes.OLD_MOUNTAIN_LAKE
            || biome == TfcBiomes.OCEANIC_MOUNTAIN_LAKE || biome == TfcBiomes.VOLCANIC_OCEANIC_MOUNTAIN_LAKE
            || biome == TfcBiomes.PLATEAU_LAKE || biome == TfcBiomes.SUBGLACIAL_LAKE;
    }

    /**
     * The lake density of one column: {@code setColumn} of the lake sampler, with the weights of the column's lake biomes in the
     * order of {@code weights}. {@link Column#NONE} when no lake biome has a weight.
     */
    public Column column(BiomeWeights weights, int x, int z) {
        int count = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (isCarvedLake(weights.id(i))) {
                count++;
            }
        }
        if (count == 0) {
            return Column.NONE;
        }
        final double[] lakeWeights = new double[count];
        int n = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (isCarvedLake(weights.id(i))) {
                lakeWeights[n++] = weights.weight(i);
            }
        }
        // setColumn
        final double h0 = Mth.clamp((0.7f - blobsNoise.noise(x, z)) * (1 / 0.3f), 0, 1);
        final double h1 = depthNoise.noise(x, z);
        return new Column(lakeWeights, centerNoise.noise(x, z), h0 * h1);
    }

    /** {@code undergroundLakes(...).noise(y)} of one sampler at a column with the given centre and depth: 0 is solid. */
    public static double samplerNoise(double center, double height, int y) {
        final double delta = Math.abs(center - y);
        return Mth.clamp(0.2f + 0.05f * (height - delta), 0, 1);
    }

    /**
     * TFC's {@code surfaceIntegrityDepth}: where the volcanoes raise a column, the depth below its surface that the biome
     * density may not carve, {@code (int) delta * 3}; 0 otherwise.
     */
    public static int surfaceIntegrityDepth(double height, double preVolcanicHeight) {
        final double volcanoHeightDelta = height - preVolcanicHeight;
        return volcanoHeightDelta > 0 ? (int) volcanoHeightDelta * 3 : 0;
    }

    /**
     * TFC's shore stage and forced no-carve zone of {@code calculateNoiseAtHeight} with every shore type passing the density
     * through (exact where only {@code NONE} has a shore weight); the full stage with the shore densities is
     * {@link TerrainDensity#postShoreNoise}.
     *
     * @param shoreWeights the shore blend weights by {@link ShoreBlendType#ordinal()}
     */
    public static double postShoreNoise(double initialNoise, double[] shoreWeights, int y, int surfaceHeight, int surfaceIntegrityDepth) {
        return TerrainDensity.postShoreNoise(initialNoise, shoreWeights, null, y, surfaceHeight, surfaceIntegrityDepth);
    }

    /**
     * The lake part of one column's biome density.
     *
     * @param weights the weights of the column's lake biomes (each its own sampler in TFC)
     * @param center  the lake's centre height ({@code centerNoise})
     * @param height  the lake's half depth ({@code h0 * h1})
     */
    public record Column(double[] weights, double center, double height) {
        /** No lake biome in the column's weights: the density is solid everywhere. */
        public static final Column NONE = new Column(new double[0], 0, 0);

        /** @return whether a lake biome has a weight in the column. */
        public boolean present() {
            return weights.length > 0;
        }

        /** @return the sum of the lake biome weights, in [0, 1]. */
        public double weight() {
            double sum = 0;
            for (double w : weights) {
                sum += w;
            }
            return sum;
        }

        /**
         * TFC's biome stage of {@code calculateNoiseAtHeight}: {@code sum(sampler.noise(y) * weight)} over the lake samplers
         * (the other samplers add {@code SOLID * weight} = 0). 0 is solid, above {@code AIR_THRESHOLD} is open.
         */
        public double noise(int y) {
            if (weights.length == 0) {
                return HeightNoiseSampler.SOLID;
            }
            final double value = samplerNoise(center, height, y);
            double noise = 0;
            for (double w : weights) {
                noise += value * w;
            }
            return noise;
        }
    }
}
