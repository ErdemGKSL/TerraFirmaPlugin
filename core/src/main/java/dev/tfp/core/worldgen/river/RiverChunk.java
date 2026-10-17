/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkHeightFiller.java @ c3b4d9536 (sampleRiverEdge) and
 * src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536 (sampleRiverData, calculateFlowAt), with
 * src/main/java/net/dries007/tfc/world/biome/RegionBiomeSource.java (getPartition).
 */
package dev.tfp.core.worldgen.river;

import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionPartition;
import dev.tfp.core.worldgen.region.RiverEdge;
import dev.tfp.core.worldgen.region.Units;

/**
 * The river data of one chunk: the nearest river of every column ({@link RiverInfo}, or null with no river within 50 blocks)
 * and the river flows on TFC's 5 x 5 quart grid, interpolated per column. As TFC, every column of the chunk reads the region
 * partition point of the chunk's minimum corner. Immutable; built on any thread from a {@link RegionGenerator}.
 */
public final class RiverChunk {
    /** A column flows (river water) only within this normalized square distance of its river. */
    public static final double FLOW_NORM_DIST_SQ = 0.28;

    private final int chunkX;
    private final int chunkZ;
    private final RiverInfo[] infos; // 16 x 16, localX + 16 * localZ
    private final Flow[] flows; // 5 x 5 quart grid, quartX + 5 * quartZ
    private final boolean empty;

    private RiverChunk(int chunkX, int chunkZ, RiverInfo[] infos, Flow[] flows) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.infos = infos;
        this.flows = flows;
        boolean none = true;
        for (RiverInfo info : infos) {
            if (info != null) {
                none = false;
                break;
            }
        }
        this.empty = none;
    }

    /** TFC's {@code sampleRiverData}: the rivers of the chunk {@code (chunkX, chunkZ)}. */
    public static RiverChunk sample(RegionGenerator regions, int chunkX, int chunkZ) {
        final int chunkMinX = chunkX << 4;
        final int chunkMinZ = chunkZ << 4;
        final RegionPartition.Point point = partition(regions, chunkMinX, chunkMinZ);
        final RiverInfo[] infos = new RiverInfo[16 * 16];
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                infos[localX + 16 * localZ] = sampleRiverEdge(point, chunkMinX + localX, chunkMinZ + localZ);
            }
        }
        // Flow is sampled on a quart scale and interpolated; the right and bottom edge lie outside the chunk
        final Flow[] flows = new Flow[5 * 5];
        for (int quartX = 0; quartX < 5; quartX++) {
            for (int quartZ = 0; quartZ < 5; quartZ++) {
                final int localX = quartX << 2;
                final int localZ = quartZ << 2;
                final RiverInfo info = quartX < 4 && quartZ < 4
                    ? infos[localX + 16 * localZ]
                    : sampleRiverEdge(point, chunkMinX + localX, chunkMinZ + localZ);
                flows[quartX + 5 * quartZ] = info != null && info.normDistSq() < FLOW_NORM_DIST_SQ ? info.flow() : Flow.NONE;
            }
        }
        return new RiverChunk(chunkX, chunkZ, infos, flows);
    }

    /**
     * The river of one column, as {@link #sample} gives it for the column's chunk (the partition of the chunk's minimum corner).
     * For single-column lookups that should not sample a whole chunk.
     */
    public static RiverInfo sampleColumn(RegionGenerator regions, int blockX, int blockZ) {
        return sampleRiverEdge(partition(regions, blockX & ~15, blockZ & ~15), blockX, blockZ);
    }

    /** {@code RegionBiomeSource.getPartition}: the partition point of a block position. */
    public static RegionPartition.Point partition(RegionGenerator regions, int blockX, int blockZ) {
        return regions.getOrCreatePartitionPoint(Units.blockToGrid(blockX), Units.blockToGrid(blockZ));
    }

    /**
     * TFC's {@code ChunkHeightFiller.sampleRiverEdge}: the river edge of {@code point} with the smallest distance over width,
     * within 50 blocks of the column, or null.
     */
    public static RiverInfo sampleRiverEdge(RegionPartition.Point point, int blockX, int blockZ) {
        final float limitDistInGridSq = 50f * 50f / (Units.GRID_WIDTH_IN_BLOCK * Units.GRID_WIDTH_IN_BLOCK);
        double minDist = limitDistInGridSq; // Only concern ourselves with rivers within a range of 50 ^2 blocks. This helps `maybeIntersect` fail more often.
        double minDistAdjusted = Float.MAX_VALUE;
        RiverEdge minEdge = null;

        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);

        for (RiverEdge edge : point.rivers()) {
            final MidpointFractal fractal = edge.fractal();
            if (fractal.maybeIntersect(exactGridX, exactGridZ, minDist)) {
                // Minimum by square distance would get us the closest edge, but would fail in the case some edges are wider than others
                // Since in most situations, we're actually concerned about distance / width, we want to have the one with the highest weight in that respect.
                final double dist = fractal.intersectDistance(exactGridX, exactGridZ);
                if (dist < limitDistInGridSq) { // Extra check that we intersect at a shorter distance than can possibly affect this location
                    final double distAdjusted = dist / edge.widthSq();
                    if (distAdjusted < minDistAdjusted) {
                        minDist = dist;
                        minDistAdjusted = distAdjusted;
                        minEdge = edge;
                    }
                }
            }
        }

        if (minEdge != null) {
            final double realWidth = minEdge.widthSq(exactGridX, exactGridZ);
            final Flow flow = minEdge.fractal().calculateFlow(exactGridX, exactGridZ);

            // minDist is in grid^2, convert it to block^2
            minDist *= Units.GRID_WIDTH_IN_BLOCK * Units.GRID_WIDTH_IN_BLOCK;

            return new RiverInfo(minEdge, flow, minDist, realWidth);
        }
        return null;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** @return {@code true} if no column of the chunk has a river within 50 blocks. */
    public boolean isEmpty() {
        return empty;
    }

    /** @return the river of a column ({@code 0..15} local coordinates), or null. */
    public RiverInfo info(int localX, int localZ) {
        return infos[localX + 16 * localZ];
    }

    /** @return the flow quart at {@code (quartX, quartZ)}, both in {@code 0..4}. */
    public Flow quartFlow(int quartX, int quartZ) {
        return flows[quartX + 5 * quartZ];
    }

    /**
     * TFC's {@code calculateFlowAt}: the flow of a column, interpolated between the four quart flows of its 4 x 4 noise cell.
     * A column whose biome has no rivers has no flow (the caller checks {@link RiverBiomes#hasRivers}).
     */
    public Flow flow(int localX, int localZ) {
        final int cellX = localX >> 2;
        final int cellZ = localZ >> 2;
        final double cellDeltaX = (double) (localX & 3) / 4;
        final double cellDeltaZ = (double) (localZ & 3) / 4;
        final Flow flow00 = flows[cellX + 5 * cellZ];
        final Flow flow10 = flows[cellX + 5 * (cellZ + 1)];
        final Flow flow01 = flows[(cellX + 1) + 5 * cellZ];
        final Flow flow11 = flows[(cellX + 1) + 5 * (cellZ + 1)];
        return Flow.lerp(flow00, flow01, flow10, flow11, (float) cellDeltaX, (float) cellDeltaZ);
    }
}
