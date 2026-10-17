/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536 (the slope map of the quarter samples), and
 * src/main/java/net/dries007/tfc/world/surface/SurfaceManager.java (sampleSlope)
 */
package dev.tfp.core.worldgen.surface;

import java.util.function.IntBinaryOperator;

/**
 * The slope of a chunk's columns, as TFC computes it: the surface height is sampled every four blocks on a 7 x 7 grid that
 * covers the chunk and its border, each 2 x 2 square of samples gives the slope of its cell (the sum of the distances of the
 * four heights from their mean), and a column's slope is the bilinear blend of the four cells around it, times 0.8. Pure.
 */
public final class SurfaceSlope {
    /** The number of quarter samples per side: the chunk's 16 blocks, its border, and one more sample at each end. */
    private static final int SAMPLES = 7;
    /** The number of cells per side: one fewer than the samples. */
    private static final int CELLS = SAMPLES - 1;

    private SurfaceSlope() {}

    /**
     * @param chunkMinX the block x of the chunk's low corner
     * @param chunkMinZ the block z of the chunk's low corner
     * @param heightAt  the surface height of a block column, absolute coordinates
     * @return The slope of each of the {@code 6 x 6} cells, indexed {@code cellX + 6 * cellZ}.
     */
    public static double[] slopeMap(int chunkMinX, int chunkMinZ, IntBinaryOperator heightAt) {
        // The quarter samples, as TFC's setupColumn of x0 = chunkMinX + ((x - 1) << 2): index x + 7z
        final double[] quart = new double[SAMPLES * SAMPLES];
        for (int z = 0; z < SAMPLES; z++) {
            for (int x = 0; x < SAMPLES; x++) {
                quart[x + SAMPLES * z] = heightAt.applyAsInt(chunkMinX + ((x - 1) << 2), chunkMinZ + ((z - 1) << 2));
            }
        }

        final double[] slopeMap = new double[CELLS * CELLS];
        for (int x = 0; x < CELLS; x++) {
            for (int z = 0; z < CELLS; z++) {
                // Math people (including TFC) cry at what this is called 'the derivative'
                final double nw = quart[(x + 0) + SAMPLES * (z + 0)];
                final double ne = quart[(x + 1) + SAMPLES * (z + 0)];
                final double sw = quart[(x + 0) + SAMPLES * (z + 1)];
                final double se = quart[(x + 1) + SAMPLES * (z + 1)];

                final double center = (nw + ne + sw + se) / 4;
                slopeMap[x + CELLS * z] = Math.abs(nw - center) + Math.abs(ne - center) + Math.abs(sw - center) + Math.abs(se - center);
            }
        }
        return slopeMap;
    }

    /**
     * TFC's {@code SurfaceManager.sampleSlope}: the slope of a column of the chunk, from its cell map.
     *
     * @param slopeMap the map of {@link #slopeMap}
     * @param x        the chunk-local x, {@code 0..15}
     * @param z        the chunk-local z, {@code 0..15}
     */
    public static double sample(double[] slopeMap, int x, int z) {
        // compute slope contribution from lerp of corners
        final int offsetX = x + 2;
        final int offsetZ = z + 2;
        final int cellX = offsetX >> 2;
        final int cellZ = offsetZ >> 2;
        final double deltaX = ((double) offsetX - (cellX << 2)) * 0.25;
        final double deltaZ = ((double) offsetZ - (cellZ << 2)) * 0.25;
        double slope = 0;
        slope += slopeMap[(cellX + 0) + 6 * (cellZ + 0)] * (1 - deltaX) * (1 - deltaZ);
        slope += slopeMap[(cellX + 1) + 6 * (cellZ + 0)] * (deltaX) * (1 - deltaZ);
        slope += slopeMap[(cellX + 0) + 6 * (cellZ + 1)] * (1 - deltaX) * (deltaZ);
        slope += slopeMap[(cellX + 1) + 6 * (cellZ + 1)] * (deltaX) * (deltaZ);
        slope *= 0.8f;
        return slope;
    }
}
