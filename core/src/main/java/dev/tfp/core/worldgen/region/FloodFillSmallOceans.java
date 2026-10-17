/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/FloodFillSmallOceans.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.IntDeque;
import java.util.BitSet;

/**
 * Turns small enclosed oceans into land (lakes of the continent scale are not wanted), and the same for small enclosed
 * continental shelf depressions. An ocean that reaches the region's edge is never filled.
 */
enum FloodFillSmallOceans implements RegionTask {
    INSTANCE;

    private static final int SMALL_OCEAN_FILL_THRESHOLD = 180;

    @Override
    public void apply(RegionGenerator.Context context) {
        final Region region = context.region;
        final BitSet explored = new BitSet(region.size());

        for (final var point : region.points()) {
            if (point != null && !point.land() && !explored.get(point.index)) {
                floodFillSmallOcean(explored, point.index, region);
            }
        }

        final BitSet exploredForShelf = new BitSet(region.size());
        for (final var point : region.points()) {
            if (point != null && !point.land() && point.oceanDepth != 2 && !exploredForShelf.get(point.index)) {
                floodFillContinentalShelfDepression(exploredForShelf, point.index, region);
            }
        }
    }

    private void floodFillSmallOcean(BitSet explored, int index, Region region) {
        // A set, as in TFC: the start point can be queued again by its own neighbourhood, and only counts once.
        final BitSet values = new BitSet(region.size());
        final IntDeque queue = new IntDeque();

        queue.enqueue(index);
        values.set(index);

        boolean unbounded = false;
        while (!queue.isEmpty()) {
            final int last = queue.dequeueInt();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Region.Point point = region.atOffset(last, dx, dz);
                    if (point == null) {
                        unbounded = true;
                        continue;
                    }
                    if (point.land() || explored.get(point.index)) {
                        continue;
                    }

                    explored.set(point.index);
                    queue.enqueue(point.index);
                    values.set(point.index);
                }
            }
        }

        if (values.cardinality() < SMALL_OCEAN_FILL_THRESHOLD && !unbounded) {
            for (int i = values.nextSetBit(0); i >= 0; i = values.nextSetBit(i + 1)) {
                region.atIndex(i).setLand();
            }
        }
    }

    private void floodFillContinentalShelfDepression(BitSet explored, int index, Region region) {
        final BitSet values = new BitSet(region.size());
        final IntDeque queue = new IntDeque();

        queue.enqueue(index);
        values.set(index);

        boolean unbounded = false;
        while (!queue.isEmpty()) {
            final int last = queue.dequeueInt();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Region.Point point = region.atOffset(last, dx, dz);
                    if (point == null) {
                        unbounded = true;
                        continue;
                    }
                    if (point.oceanDepth == 2 || explored.get(point.index)) {
                        continue;
                    }

                    explored.set(point.index);
                    queue.enqueue(point.index);
                    values.set(point.index);
                }
            }
        }

        if (values.cardinality() < SMALL_OCEAN_FILL_THRESHOLD && !unbounded) {
            for (int i = values.nextSetBit(0); i >= 0; i = values.nextSetBit(i + 1)) {
                region.atIndex(i).setLand();
            }
        }
    }
}
