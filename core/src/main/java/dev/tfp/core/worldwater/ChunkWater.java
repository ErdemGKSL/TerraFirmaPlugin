/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536 (fillColumn: localBiome, flow, surface height)
 */
package dev.tfp.core.worldwater;

import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.river.Flow;

/**
 * The water facts of one chunk, from the seed (PAR-FLUID-03): per column the TFC local biome, the river flow and the surface
 * height, which {@link WorldWaterRules#classify} turns into the kind of each water block. Immutable; built on any thread,
 * shared by the region threads.
 */
public final class ChunkWater {
    private final int chunkX;
    private final int chunkZ;
    private final int[] biomes;
    private final Flow[] flows;
    private final int[] heights;

    private ChunkWater(int chunkX, int chunkZ, int[] biomes, Flow[] flows, int[] heights) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.biomes = biomes;
        this.flows = flows;
        this.heights = heights;
    }

    /** The water facts of a chunk from TFC's column pass ({@link SurfaceHeights#chunk}). */
    public static ChunkWater of(SurfaceHeights.Chunk chunk) {
        final int[] biomes = new int[256];
        final Flow[] flows = new Flow[256];
        final int[] heights = new int[256];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                final int i = localX + 16 * localZ;
                final SurfaceHeights.Column column = chunk.column(localX, localZ);
                biomes[i] = column.localBiome();
                heights[i] = column.surfaceHeight();
                flows[i] = WorldWaterRules.columnFlow(biomes[i], chunk.rivers().flow(localX, localZ));
            }
        }
        return new ChunkWater(chunk.chunkX(), chunk.chunkZ(), biomes, flows, heights);
    }

    /** Test seam: a chunk of one biome, flow and height everywhere. */
    public static ChunkWater uniform(int chunkX, int chunkZ, int biome, Flow flow, int height) {
        final int[] biomes = new int[256];
        final Flow[] flows = new Flow[256];
        final int[] heights = new int[256];
        java.util.Arrays.fill(biomes, biome);
        java.util.Arrays.fill(flows, WorldWaterRules.columnFlow(biome, flow));
        java.util.Arrays.fill(heights, height);
        return new ChunkWater(chunkX, chunkZ, biomes, flows, heights);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** @return the TFC local biome id of a column, {@code 0..15} local coordinates */
    public int biome(int localX, int localZ) {
        return biomes[(localX & 15) + 16 * (localZ & 15)];
    }

    /** @return the river flow of a column ({@link Flow#NONE} where the biome has no rivers) */
    public Flow flow(int localX, int localZ) {
        return flows[(localX & 15) + 16 * (localZ & 15)];
    }

    /** @return the surface height of a column */
    public int surfaceHeight(int localX, int localZ) {
        return heights[(localX & 15) + 16 * (localZ & 15)];
    }

    /** The kind of a water block at {@code y} in a column (no spring pool: see {@link WorldWaterRules#classify}). */
    public WaterKind kind(int localX, int y, int localZ) {
        return WorldWaterRules.classify(biome(localX, localZ), flow(localX, localZ), surfaceHeight(localX, localZ), y, false);
    }

    /** The river flow of a water block at {@code y}: the column's flow where it is river water, else {@link Flow#NONE}. */
    public Flow riverFlow(int localX, int y, int localZ) {
        return kind(localX, y, localZ) == WaterKind.RIVER ? flow(localX, localZ) : Flow.NONE;
    }
}
