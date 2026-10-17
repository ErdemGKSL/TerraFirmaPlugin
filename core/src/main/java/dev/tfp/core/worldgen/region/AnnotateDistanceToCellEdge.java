/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AnnotateDistanceToCellEdge.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.IntDeque;
import java.util.BitSet;

/** Distance, in grid points, from each point to the region's edge or to a point outside the cell. */
enum AnnotateDistanceToCellEdge implements RegionTask {
    INSTANCE;

    @Override
    public void apply(RegionGenerator.Context context) {
        final Region region = context.region;
        final BitSet explored = new BitSet(region.size());
        final IntDeque queue = new IntDeque();

        for (int dx = 0; dx < region.sizeX(); dx++) {
            for (int dz = 0; dz < region.sizeZ(); dz++) {
                final int index = dx + region.sizeX() * dz;
                final Region.Point point = region.at(dx + region.minX(), dz + region.minZ());
                if (point == null || isUnbounded(region, dx, dz)) {
                    explored.set(index);
                    queue.enqueue(index);
                    if (point != null) {
                        point.distanceToEdge = -1;
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            final int last = queue.dequeueInt();
            final Region.Point lastPoint = region.atIndex(last);
            final int nextDistance = lastPoint == null ? 0 : lastPoint.distanceToEdge + 1;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Region.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.distanceToEdge == 0) {
                        if (!explored.get(point.index)) {
                            point.distanceToEdge = (byte) nextDistance;
                            explored.set(point.index);
                            queue.enqueue(point.index);
                        }
                        explored.set(point.index);
                    }
                }
            }
        }
    }

    private static boolean isUnbounded(Region region, int dx, int dz) {
        return dx == 0 || dz == 0 || dx == region.sizeX() - 1 || dz == region.sizeZ() - 1;
    }
}
