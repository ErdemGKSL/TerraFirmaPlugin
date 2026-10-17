/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AnnotateBaseLandHeight.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.IntDeque;
import java.util.BitSet;

/**
 * The base land height: a rough inland-ness from the distance to the ocean and to the cell edge. It is used to trace
 * mountain ranges along contours. Also sets the distance to land, which grows from the coast (and from island seeds)
 * with occasional random shortcuts so the contours are not perfect rings.
 */
enum AnnotateBaseLandHeight implements RegionTask {
    INSTANCE;

    private static final int ISLAND_SEED_DEPTH = 3;

    @Override
    public void apply(RegionGenerator.Context context) {
        final Region region = context.region;
        final BitSet explored = new BitSet(region.size());
        final IntDeque queue = new IntDeque();
        final IntDeque islandQueue = new IntDeque();
        final var random = context.random;

        // Annotate base land height using min(noise, distance from ocean)
        // Also seeds a queue of shore points for base ocean height, and island points
        for (final var point : region.points()) {
            if (point.land()) {
                // Base land height is a simple approximation of inland-ness, but with influence from distance to the edge
                // We use this to place mountains along contours
                point.baseLandHeight = point.distanceToOcean;
                if (point.baseLandHeight > point.distanceToEdge) {
                    point.baseLandHeight = (byte) (0.3f * point.baseLandHeight + 0.7f * point.distanceToEdge);
                }

                explored.set(point.index);

                if (point.island()) {
                    point.distanceToLand = ISLAND_SEED_DEPTH;
                    islandQueue.enqueue(point.index);
                } else {
                    point.distanceToLand = 0;
                    queue.enqueue(point.index);
                }
            }
        }

        while (!queue.isEmpty()) {
            final int last = queue.dequeueInt();
            final Region.Point lastPoint = region.atIndex(last);
            final int nextDepth = lastPoint.distanceToLand + 1;

            if (nextDepth == ISLAND_SEED_DEPTH && !islandQueue.isEmpty()) {
                // Seed with island queue points now - this is a hack to avoid using a priority queue
                for (int i = 0; i < islandQueue.size(); i++) {
                    queue.enqueue(islandQueue.get(i));
                }
                islandQueue.clear();
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Region.Point point = region.atOffset(last, dx, dz);
                    if (point != null && !point.land() && point.distanceToLand == 0) {
                        if (!explored.get(point.index)) {
                            if (random.nextInt(15) == 0) {
                                // Not a true BFS, we have some 'cheat' points
                                // To preserve the nature of the BFS we enqueueFirst for these points, so they stay in the right batch
                                point.distanceToLand = lastPoint.distanceToLand;
                                queue.enqueueFirst(point.index);
                            } else {
                                point.distanceToLand = (byte) nextDepth;
                                queue.enqueue(point.index);
                            }
                        }
                        explored.set(point.index);
                    }
                }
            }
        }
    }
}
