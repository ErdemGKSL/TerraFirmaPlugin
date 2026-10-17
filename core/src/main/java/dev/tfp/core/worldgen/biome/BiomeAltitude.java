/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AnnotateBiomeAltitude.java @ c3b4d9536 (the expressions and their order
 * are TFC's; the random draws are in the same order, so the region stream after this task matches TFC)
 */
package dev.tfp.core.worldgen.biome;

import dev.tfp.core.worldgen.IntDeque;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.region.Region;
import java.util.BitSet;

/**
 * The biome altitude of each land point, from 0 (low) to {@code 3 * WIDTH} (mountain), in steps of {@link #WIDTH}. A
 * breadth-first walk steps down from the mountain blobs, with a small random chance to keep the level of the point it came
 * from. A second pass raises low land with a base height of 4 or more to the mid level, and mid land with a base height of
 * 11 or more to the high level. The discrete altitude (0 to 3) picks the biome lists in {@link BiomeChoice}.
 */
public final class BiomeAltitude {
    /** The altitude of one discrete level. */
    public static final int WIDTH = 4;

    private BiomeAltitude() {}

    /**
     * Writes {@link Region.Point#biomeAltitude} for the land points of the region.
     *
     * @param region The region, annotated up to the islands and the base land height.
     * @param random The region's random stream. The draws are part of TFC's stream, so the later tasks depend on them.
     */
    public static void annotate(Region region, XoroshiroRandomSource random) {
        // Begin a basic BFS out from mountain blobs, stepping down
        final BitSet explored = new BitSet(region.size());
        final IntDeque queue = new IntDeque();

        for (final var point : region.points()) {
            if (point.land() && point.mountain()) {
                point.biomeAltitude = (byte) (3 * WIDTH);
                queue.enqueue(point.index);
                explored.set(point.index);
            }
        }

        while (!queue.isEmpty()) {
            final int last = queue.dequeueInt();
            final Region.Point lastPoint = region.atIndex(last);
            final int nextAltitude = lastPoint.biomeAltitude - 1;
            if (nextAltitude < 0) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Region.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.land() && point.biomeAltitude == 0 && !explored.get(point.index)) {
                        // Minor non-uniformity, makes regions a bit messier
                        if (random.nextInt(13) == 0 && lastPoint.biomeAltitude != 3 * WIDTH) {
                            point.biomeAltitude = lastPoint.biomeAltitude;
                            queue.enqueueFirst(point.index);
                        } else {
                            point.biomeAltitude = (byte) nextAltitude;
                            queue.enqueue(point.index);
                        }
                        explored.set(point.index);
                    }
                }
            }
        }

        // Run another pass over the entire region, this time raising land from low -> mid
        for (final var point : region.points()) {
            if (point.land() && point.discreteBiomeAltitude() == 0 && point.baseLandHeight >= 4) {
                if (point.discreteBiomeAltitude() == 0 && point.baseLandHeight >= 4) {
                    point.biomeAltitude = (byte) WIDTH;
                }
                if (point.discreteBiomeAltitude() == 1 && point.baseLandHeight >= 11) {
                    point.biomeAltitude = (byte) (2 * WIDTH);
                }
            }
        }
    }
}
