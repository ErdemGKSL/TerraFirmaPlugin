/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/Units.java @ c3b4d9536 (the constants and the conversions the in-scope tasks use)
 */
package dev.tfp.core.worldgen.region;

/**
 * Coordinate scales of the region system. A <strong>grid</strong> point is the smallest generated unit, 128 blocks
 * wide. A <strong>cell</strong> is the area of one region: 96 grid points. Only the constants and conversions that the
 * ported tasks use are here (partition coordinates belong to rivers, which are not ported yet).
 */
public final class Units {
    public static final int PARTITION_BITS = 5;
    public static final int PARTITION_WIDTH_IN_GRID = 3;

    public static final int CELL_WIDTH_IN_PARTITION = (1 << PARTITION_BITS);
    public static final int CELL_WIDTH_IN_GRID = CELL_WIDTH_IN_PARTITION * PARTITION_WIDTH_IN_GRID;

    public static final int REGION_RADIUS_IN_GRID = CELL_WIDTH_IN_GRID + 5;

    public static final int GRID_BITS = 7;
    public static final int GRID_WIDTH_IN_BLOCK = (1 << GRID_BITS);

    /** {@code grid << 7}: the block coordinate of a grid point's origin. */
    public static int gridToBlock(int grid) {
        return grid << GRID_BITS;
    }

    private Units() {}
}
