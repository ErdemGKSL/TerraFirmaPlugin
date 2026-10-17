/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * The surface of one block column: TFC's {@code SurfaceManager} and {@code SurfaceBuilderContext.buildSurface}, for one column.
 * The pass starts above the highest block of the column (the top of the water, if there is any) and runs down to at most
 * twenty blocks below the ocean floor, as TFC does. Pure: the result depends only on the inputs.
 */
public final class ColumnSurface {
    /** TFC's {@code iterate down to at least the ocean floor and below}: the depth below the floor the pass reaches. */
    private static final int DEPTH_BELOW_FLOOR = 20;

    private final int endY;
    private final SurfaceKind[] kinds;

    private ColumnSurface(int endY, SurfaceKind[] kinds) {
        this.endY = endY;
        this.kinds = kinds;
    }

    /**
     * Builds the surface of a column.
     *
     * @param builder the builder of the column's biome
     * @param column  the column's inputs
     * @param topY    the highest rock block of the column, the surface of the rock
     * @param minY    the lowest height of the world; the pass does not go below it
     */
    public static ColumnSurface build(SurfaceBuilder builder, SurfaceColumn column, int topY, int minY) {
        final int seaLevel = column.seaLevel();
        // WORLD_SURFACE_WG counts water, so over ocean it is the water top; OCEAN_FLOOR_WG is the rock top. Both are
        // heightmap values (the first block above the top), and the pass starts one block above them, as TFC does.
        final int worldSurface = Math.max(topY, seaLevel) + 1;
        final int oceanFloor = topY + 1;
        final int startY = worldSurface + 1;
        final int endY = Math.max(minY, Math.min(startY, oceanFloor) - DEPTH_BELOW_FLOOR);
        final SurfacePass pass = new SurfacePass(column, topY, oceanFloor, startY, endY);
        builder.build(pass, startY, endY);
        return new ColumnSurface(endY, pass.kinds());
    }

    /** @return The kind written at {@code y}, or null where the surface leaves the block as the rock layer put it. */
    public SurfaceKind kind(int y) {
        if (y < endY || y - endY >= kinds.length) {
            return null;
        }
        return kinds[y - endY];
    }

    /** @return The lowest height the pass visited. */
    public int endY() {
        return endY;
    }
}
