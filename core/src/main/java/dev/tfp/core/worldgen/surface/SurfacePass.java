/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * The state of one column while a surface builder runs: what each height holds before the surface (rock, water or air),
 * the surface kinds written so far, and the lowest height the pass may write. TFC's {@code SurfaceBuilderContext} cursor
 * and its {@code getBlockState} / {@code setBlockState}, for one column. Not thread-safe; one per column.
 */
public final class SurfacePass {
    /** What the column holds at a height before the surface rules run. */
    public enum Fill { AIR, WATER, ROCK }

    private final SurfaceColumn column;
    private final int topY;
    private final int oceanFloorY;
    private final int endY;
    private final SurfaceKind[] out;

    /**
     * @param column      the column
     * @param topY        the highest rock block of the column; rock fills every height from the floor to here
     * @param oceanFloorY the ocean floor heightmap of the column, {@code topY + 1} (TFC's {@code OCEAN_FLOOR_WG})
     * @param endY        the lowest height the pass visits
     * @param startY      the highest height the pass visits
     */
    SurfacePass(SurfaceColumn column, int topY, int oceanFloorY, int startY, int endY) {
        this.column = column;
        this.topY = topY;
        this.oceanFloorY = oceanFloorY;
        this.endY = endY;
        this.out = new SurfaceKind[startY - endY + 1];
    }

    public SurfaceColumn column() {
        return column;
    }

    /** The sea level of the column. */
    public int seaLevel() {
        return column.seaLevel();
    }

    /** The ocean floor heightmap of the column (see the constructor). */
    public int oceanFloorY() {
        return oceanFloorY;
    }

    /** What the column holds at {@code y}: rock up to the top, then water up to sea level, then air. */
    public Fill fill(int y) {
        if (y <= topY) {
            return Fill.ROCK;
        }
        return y <= column.seaLevel() ? Fill.WATER : Fill.AIR;
    }

    /** Writes the kind at {@code y}. Each height is written at most once per pass. */
    public void set(int y, SurfaceKind kind) {
        out[y - endY] = kind;
    }

    /** @return The kinds written, indexed {@code y - endY}; null where the pass wrote nothing. */
    SurfaceKind[] kinds() {
        return out;
    }

    /** @return The lowest height of the pass. */
    int endY() {
        return endY;
    }
}
