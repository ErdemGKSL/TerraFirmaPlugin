/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AnnotateDistanceToDeepOcean.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.IntDeque;
import java.util.BitSet;

/** Distance from each point at or below the continental shelf to the nearest deep ocean point, in grid points. */
enum AnnotateDistanceToDeepOcean implements RegionTask {
    INSTANCE;

    @Override
    public void apply(RegionGenerator.Context context) {
        final Region region = context.region;
        final BitSet explored = new BitSet(region.size());
        final IntDeque queue = new IntDeque();

        for (final var point : region.points()) {
            if (point != null && point.oceanDepth > 2) {
                point.distanceToDeepOcean = -1;
                queue.enqueue(point.index);
                explored.set(point.index);
            }
        }

        while (!queue.isEmpty()) {
            final int last = queue.dequeueInt();
            final Region.Point lastPoint = region.atIndex(last);
            final int nextDistance = lastPoint.distanceToDeepOcean + 1;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Region.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.oceanDepth <= 2 && point.distanceToDeepOcean == 0) {
                        if (!explored.get(point.index)) {
                            point.distanceToDeepOcean = (byte) nextDistance;
                            queue.enqueue(point.index);
                        }
                        explored.set(point.index);
                    }
                }
            }
        }
    }
}
