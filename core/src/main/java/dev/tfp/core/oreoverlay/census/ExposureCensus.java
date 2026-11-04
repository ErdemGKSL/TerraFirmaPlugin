/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

/**
 * Counts the exposed faces of target blocks in one chunk (PAR-WORLD-17, spike S1). Pure and thread-safe: run it on the CPU
 * pool over a {@link ChunkView} built from snapshots. A face of a target block is exposed when the neighbour cell across it
 * {@link ExposureRule#exposes exposes} it; neighbours across the chunk edge come from the view's border, so every face is
 * counted exactly once, by the chunk that owns the block.
 */
public final class ExposureCensus {
    /** Depth of the surface band below a column's {@link ChunkView#surfaceY}. */
    public static final int SURFACE_DEPTH = 8;

    private static final int[][] OFFSETS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private ExposureCensus() {
    }

    /**
     * @param view    the chunk and its border
     * @param counted per target index, whether it counts towards the totals (every target is in the per-target arrays); its
     *                length is the number of targets
     * @throws IllegalArgumentException if a cell holds a target index outside {@code counted}
     */
    public static ChunkCensus count(ChunkView view, boolean[] counted) {
        final int targets = counted.length;
        final int[] targetBlocks = new int[targets];
        final int[] targetExposed = new int[targets];
        final int[] targetFaces = new int[targets];
        final int[] targetFacesSurface = new int[targets];
        final int[] byDirection = new int[OFFSETS.length];
        int blocks = 0;
        int exposed = 0;
        int exposedSurface = 0;
        int faces = 0;
        int facesSurface = 0;
        int example = -1;
        final int minY = view.minY();
        final int maxY = minY + view.height();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    final int target = view.cell(x, y, z);
                    if (target < 0) {
                        continue;
                    }
                    if (target >= targets) {
                        throw new IllegalArgumentException("target index " + target + " at " + x + "," + y + "," + z
                                + " is outside the " + targets + " targets");
                    }
                    final boolean isCounted = counted[target];
                    targetBlocks[target]++;
                    int open = 0;
                    int openSurface = 0;
                    for (int d = 0; d < OFFSETS.length; d++) {
                        final int nx = x + OFFSETS[d][0];
                        final int ny = y + OFFSETS[d][1];
                        final int nz = z + OFFSETS[d][2];
                        final int neighbour = ny < minY ? ExposureRule.SOLID : ny >= maxY ? ExposureRule.OPEN : view.cell(nx, ny, nz);
                        if (!ExposureRule.exposes(neighbour)) {
                            continue;
                        }
                        open++;
                        if (ny >= view.surfaceY(nx, nz) - SURFACE_DEPTH) {
                            openSurface++;
                        }
                        if (isCounted) {
                            byDirection[d]++;
                        }
                    }
                    if (open > 0) {
                        targetExposed[target]++;
                        targetFaces[target] += open;
                        targetFacesSurface[target] += openSurface;
                    }
                    if (isCounted) {
                        blocks++;
                        if (open > 0) {
                            exposed++;
                            faces += open;
                            facesSurface += openSurface;
                            if (openSurface > 0) {
                                exposedSurface++;
                                if (example < 0) {
                                    example = ChunkCensus.pack(x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }
        return new ChunkCensus(blocks, exposed, exposedSurface, faces, facesSurface, byDirection, targetBlocks, targetExposed,
                targetFaces, targetFacesSurface, example);
    }
}
