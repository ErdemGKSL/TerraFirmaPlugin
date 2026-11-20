/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/noise/TrilinearInterpolator.java @ c3b4d9536 (fillSlice, selectCellYZ,
 * updateForXZ, updateForY), with the cell settings of world/TFCChunkGenerator.java (createNoiseSamplingSettingsForChunk) for
 * the overworld noise settings (cells 4 x 8 x 4 from y -64, 48 cells high).
 */
package dev.tfp.core.worldgen.density;

import dev.tfp.core.worldgen.Mth;

/**
 * The cell-corner samples of one density function over one chunk and TFC's trilinear interpolation between them: the
 * function is computed at every corner of the chunk's 4 x 8 x 4 cells, and a block's value is lerped along x, then z, then y,
 * in the order of {@code TrilinearInterpolator}, so the value is the same double TFC computes. Immutable after construction.
 */
public final class CellInterpolator {
    public static final int CELL_WIDTH = 4;
    public static final int CELL_HEIGHT = 8;
    /** Corners per chunk along x and z. */
    static final int CORNERS_XZ = 16 / CELL_WIDTH + 1;

    private final int minBlockX;
    private final int minBlockZ;
    private final int minY;
    private final int cornersY;
    /** {@code [x][z][y]} flattened. */
    private final double[] corners;

    /**
     * @param minY      the lowest block y (a multiple of {@link #CELL_HEIGHT})
     * @param cellCountY the number of cells in height
     */
    public CellInterpolator(VanillaDensity function, int chunkX, int chunkZ, int minY, int cellCountY) {
        this.minBlockX = chunkX << 4;
        this.minBlockZ = chunkZ << 4;
        this.minY = minY;
        this.cornersY = cellCountY + 1;
        this.corners = new double[CORNERS_XZ * CORNERS_XZ * cornersY];
        final int firstCellX = Math.floorDiv(minBlockX, CELL_WIDTH);
        final int firstCellY = Math.floorDiv(minY, CELL_HEIGHT);
        final int firstCellZ = Math.floorDiv(minBlockZ, CELL_WIDTH);
        int i = 0;
        for (int dx = 0; dx < CORNERS_XZ; dx++) {
            for (int dz = 0; dz < CORNERS_XZ; dz++) {
                for (int dy = 0; dy < cornersY; dy++) {
                    corners[i++] = function.compute((firstCellX + dx) * CELL_WIDTH, (firstCellY + dy) * CELL_HEIGHT,
                        (firstCellZ + dz) * CELL_WIDTH);
                }
            }
        }
    }

    private double corner(int cx, int cz, int cy) {
        return corners[(cx * CORNERS_XZ + cz) * cornersY + cy];
    }

    /** The interpolated value at a block of the chunk ({@code y} within the cells). */
    public double sample(int x, int y, int z) {
        final int lx = x - minBlockX;
        final int lz = z - minBlockZ;
        final int ly = y - minY;
        final int cx = lx / CELL_WIDTH;
        final int cz = lz / CELL_WIDTH;
        final int cy = ly / CELL_HEIGHT;
        final double deltaX = (double) (lx % CELL_WIDTH) / CELL_WIDTH;
        final double deltaZ = (double) (lz % CELL_WIDTH) / CELL_WIDTH;
        final double deltaY = (double) (ly % CELL_HEIGHT) / CELL_HEIGHT;
        final double noise000 = corner(cx, cz, cy);
        final double noise001 = corner(cx, cz + 1, cy);
        final double noise100 = corner(cx + 1, cz, cy);
        final double noise101 = corner(cx + 1, cz + 1, cy);
        final double noise010 = corner(cx, cz, cy + 1);
        final double noise011 = corner(cx, cz + 1, cy + 1);
        final double noise110 = corner(cx + 1, cz, cy + 1);
        final double noise111 = corner(cx + 1, cz + 1, cy + 1);
        final double valueX00 = Mth.lerp(deltaX, noise000, noise100);
        final double valueX10 = Mth.lerp(deltaX, noise010, noise110);
        final double valueX01 = Mth.lerp(deltaX, noise001, noise101);
        final double valueX11 = Mth.lerp(deltaX, noise011, noise111);
        final double valueX0Z = Mth.lerp(deltaZ, valueX00, valueX01);
        final double valueX1Z = Mth.lerp(deltaZ, valueX10, valueX11);
        return Mth.lerp(deltaY, valueX0Z, valueX1Z);
    }
}
