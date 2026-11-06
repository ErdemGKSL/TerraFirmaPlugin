/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import dev.tfp.core.oreoverlay.census.ExposureRule;

/**
 * Cells of the world as the ore-overlay rules see them (PAR-INFRA-16), read from a snapshot.
 *
 * <p>{@link #code} is {@link ExposureRule#SOLID} (an occluding block that is not an ore), {@link ExposureRule#OPEN} (air, a
 * fluid or a non-occluding state: exposes the face next to it), a non-negative ore kind (an index into the overlay list;
 * ores occlude) or {@link #UNKNOWN} (not in the snapshot). {@link #surface} is the column's {@code WORLD_SURFACE}
 * heightmap (the first y above the highest non-air block) or {@link #NO_SURFACE}.
 */
public interface CellLookup {
    /** The cell is not in this snapshot. */
    int UNKNOWN = -3;
    /** No heightmap value for the column. */
    int NO_SURFACE = Integer.MIN_VALUE;

    /** A lookup that knows nothing. */
    CellLookup NONE = new CellLookup() {
        @Override
        public int code(int x, int y, int z) {
            return UNKNOWN;
        }

        @Override
        public int surface(int x, int z) {
            return NO_SURFACE;
        }
    };

    int code(int x, int y, int z);

    int surface(int x, int z);

    /** True if a cell code exposes the face of the ore next to it. */
    static boolean exposes(int code) {
        return ExposureRule.exposes(code);
    }

    /** True if the code is an ore kind. */
    static boolean isOre(int code) {
        return code >= 0;
    }
}
