/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Placeholder for TFC's surface heights (M2-GEN-06: ChunkHeightFiller, the density stage). TFC's heights come from its
 * noise density and the biome altitude, which are not ported yet. This keeps the coarse shape of the region map instead:
 * land and ocean, and the inland rise of land, bilinearly interpolated between the grid points. It is not TFC's terrain.
 */
package dev.tfp.core.worldgen.chunk;

import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.Units;

/**
 * The placeholder surface height of every block column of a chunk. Pure and thread-safe: it reads the region map through the
 * generator's concurrent caches and writes only the array it returns.
 */
public final class PlaceholderHeights {
    /** Land stands this far above sea level before its inland rise. */
    static final int LAND_BASE = 2;
    /** The inland rise per unit of base land height, capped at {@link #LAND_RISE_CAP} units. */
    static final float LAND_RISE = 1.5f;
    static final int LAND_RISE_CAP = 20;
    /** Ocean floors lie this far below sea level, and this much deeper per unit of ocean depth. */
    static final int OCEAN_BASE = 6;
    static final int OCEAN_PER_DEPTH = 4;

    private PlaceholderHeights() {}

    /**
     * @return The surface height (absolute Y of the top solid block) of each column, {@code 256} entries indexed
     *         {@code x + 16z} (chunk-local), for the chunk at {@code (chunkX, chunkZ)}.
     */
    public static int[] heights(RegionGenerator regions, int chunkX, int chunkZ) {
        final int[] heights = new int[ChunkData.COLUMNS];
        final int blockX = chunkX << 4;
        final int blockZ = chunkZ << 4;
        for (int lz = 0; lz < 16; lz++) {
            final int z = blockZ + lz;
            final int gridZ = Units.blockToGrid(z);
            final float v = (z - Units.gridToBlock(gridZ)) / (float) Units.GRID_WIDTH_IN_BLOCK;
            for (int lx = 0; lx < 16; lx++) {
                final int x = blockX + lx;
                final int gridX = Units.blockToGrid(x);
                final float u = (x - Units.gridToBlock(gridX)) / (float) Units.GRID_WIDTH_IN_BLOCK;

                final float h00 = target(regions.getOrCreateRegionPoint(gridX, gridZ));
                final float h10 = target(regions.getOrCreateRegionPoint(gridX + 1, gridZ));
                final float h01 = target(regions.getOrCreateRegionPoint(gridX, gridZ + 1));
                final float h11 = target(regions.getOrCreateRegionPoint(gridX + 1, gridZ + 1));

                final float along0 = h00 + u * (h10 - h00);
                final float along1 = h01 + u * (h11 - h01);
                heights[lx + 16 * lz] = Math.round(along0 + v * (along1 - along0));
            }
        }
        return heights;
    }

    /** @return The surface height at one grid point, before interpolation. */
    static float target(Region.Point point) {
        if (point.land()) {
            return ChunkData.SEA_LEVEL_Y + LAND_BASE + Math.min(point.baseLandHeight, LAND_RISE_CAP) * LAND_RISE;
        }
        return ChunkData.SEA_LEVEL_Y - OCEAN_BASE - point.oceanDepth * OCEAN_PER_DEPTH;
    }
}
