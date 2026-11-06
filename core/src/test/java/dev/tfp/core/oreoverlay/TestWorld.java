/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.util.HashMap;
import java.util.Map;

/** A small world for the ore-overlay tests: cells default to {@code fill}, heightmap per column optional. */
final class TestWorld implements CellLookup {
    static final int MIN_Y = 0;
    static final int MAX_Y = 32;

    private final Map<Long, Integer> cells = new HashMap<>();
    private final Map<Long, Integer> surface = new HashMap<>();
    private final int fill;

    TestWorld(int fill) {
        this.fill = fill;
    }

    TestWorld set(int x, int y, int z, int code) {
        cells.put(OverlayKeys.block(x, y, z), code);
        return this;
    }

    TestWorld surface(int x, int z, int y) {
        surface.put(OverlayKeys.chunk(x, z), y);
        return this;
    }

    @Override
    public int code(int x, int y, int z) {
        if (y < MIN_Y) {
            return ExposureRule.SOLID;
        }
        if (y >= MAX_Y) {
            return ExposureRule.OPEN;
        }
        return cells.getOrDefault(OverlayKeys.block(x, y, z), fill);
    }

    @Override
    public int surface(int x, int z) {
        return surface.getOrDefault(OverlayKeys.chunk(x, z), NO_SURFACE);
    }

    /** The snapshot of one chunk (its own cells only). */
    OreSnapshot snapshot(int chunkX, int chunkZ) {
        ArrayOreSnapshot.Builder b = ArrayOreSnapshot.builder(chunkX, chunkZ, MIN_Y, MAX_Y, fill);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (chunkX << 4) | lx;
                int z = (chunkZ << 4) | lz;
                b.surface(x, z, surface(x, z));
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    Integer c = cells.get(OverlayKeys.block(x, y, z));
                    if (c != null) {
                        b.set(x, y, z, c);
                    }
                }
            }
        }
        return b.build();
    }

    /** The full table of a chunk, the border read from this world. */
    OreOverlayTable table(int chunkX, int chunkZ) {
        return ExposedFaces.compute(snapshot(chunkX, chunkZ), this);
    }
}
