/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536 (calculateNoiseAtHeight: the shore stage and the
 * forced no-carve zone; fillColumn: maxFilledY), with src/main/java/net/dries007/tfc/world/TFCAquifer.java @ c3b4d9536
 * (sampleState: a density above 0 is solid).
 */
package dev.tfp.core.worldgen.density;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.height.ShoreBlendType;

/**
 * The parts of TFC's per-block terrain density ({@code calculateNoiseAtHeight}) between the biome samplers and the river stage,
 * and the solid rule. The density is per block and needs only the column: TFC evaluates it per block, without the 4 x 8 x 4 cell
 * interpolation it uses for the cave noises, so no neighbour chunk is read. {@code SurfaceHeights.Column.density} composes it:
 * the biome stage (the carved lakes and the cenotes), {@link #postShoreNoise}, then the river stage, the air threshold and the
 * slide ({@code RiverColumn.density}).
 */
public final class TerrainDensity {
    private TerrainDensity() {}

    /**
     * {@code fillColumn}'s {@code maxFilledY}: TFC fills a column only below {@code 1 + max(surface, sea level)}; every block at
     * or above it is air whatever the density says.
     */
    public static int maxFilledY(int surfaceHeight) {
        return 1 + Math.max(surfaceHeight, ChunkData.SEA_LEVEL_Y);
    }

    /** TFC's solid rule: {@code TFCAquifer.sampleState} keeps the base block where the density is above 0. */
    public static boolean isSolid(double density) {
        return density > 0;
    }

    /**
     * TFC's shore stage and forced no-carve zone of {@code calculateNoiseAtHeight}: each shore blend type modifies the biome
     * density and the results are summed by the shore blend weights, then the density fades to solid toward
     * {@code surfaceHeight - depth} where the volcanoes raised the column.
     *
     * @param initialNoise the biome stage (the weighted sum of the column's biome samplers)
     * @param shoreWeights the shore blend weights by {@link ShoreBlendType#ordinal()}
     * @param shores       the shore samplers' column states by ordinal (null entries or a null array pass the density through)
     */
    public static double postShoreNoise(double initialNoise, double[] shoreWeights, ShoreDensity.Column[] shores, int y,
                                        int surfaceHeight, int surfaceIntegrityDepth) {
        double noise = 0;
        for (ShoreBlendType type : ShoreBlendType.ALL) {
            final double weight = shoreWeights[type.ordinal()];
            if (type == ShoreBlendType.NONE) {
                noise += weight * initialNoise;
            } else if (weight > 0) {
                final ShoreDensity.Column shore = shores == null ? null : shores[type.ordinal()];
                noise += weight * (shore == null ? initialNoise : shore.noise(y, initialNoise));
            }
        }
        if (surfaceIntegrityDepth > 0) {
            final int carveBelowHeight = surfaceHeight - surfaceIntegrityDepth;
            noise = Mth.clampedMap(y, carveBelowHeight - 10, carveBelowHeight, noise, 0);
        }
        return noise;
    }
}
