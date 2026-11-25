/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * The state of one column while a surface builder runs: what each height holds before the surface (rock, water or air),
 * the blocks written so far, the slope (a builder may scale it), and the chunk's random source. TFC's
 * {@code SurfaceBuilderContext} cursor and its {@code getBlockState} / {@code setBlockState}, for one column. Not
 * thread-safe; one per column.
 */
public final class SurfacePass {
    /**
     * What the column holds at a height: air, water, the raw rock of a rock type (TFC's {@code isDefaultBlock}), or another
     * block that a builder wrote there.
     */
    public enum Fill { AIR, WATER, ROCK, OTHER }

    private final SurfaceColumn column;
    private final int topY;
    private final int oceanFloorY;
    private final int endY;
    private final int minY;
    private final XoroshiroRandomSource random;
    private final IntFunction<Fill> open;
    private double slope;
    private int baseY;
    private SurfaceBlock[] out;

    /**
     * @param column      the column
     * @param topY        the highest rock block of the column; rock fills every height from the floor to here
     * @param oceanFloorY the ocean floor height of the column, {@code topY} (TFC's {@code getHeight(OCEAN_FLOOR_WG)})
     * @param startY      the highest height the pass visits
     * @param endY        the lowest height the pass visits
     * @param minY        the lowest height of the world
     * @param random      the random source of the chunk, shared by its columns in TFC's order (x outer, z inner); null for
     *                    builders that draw nothing
     * @param open        what a height at or below {@code topY} that the terrain density or the density caves opened holds
     *                    ({@link Fill#AIR} or {@link Fill#WATER}, or {@link Fill#OTHER} for a block that is neither air nor
     *                    rock), or null where it holds rock; the function itself may be null when nothing is open
     */
    SurfacePass(SurfaceColumn column, int topY, int oceanFloorY, int startY, int endY, int minY, XoroshiroRandomSource random,
                IntFunction<Fill> open) {
        this.open = open;
        this.column = column;
        this.topY = topY;
        this.oceanFloorY = oceanFloorY;
        this.endY = endY;
        this.minY = minY;
        this.random = random;
        this.slope = column.slope();
        this.baseY = endY;
        this.out = new SurfaceBlock[startY - endY + 1];
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

    /** TFC's {@code getSlope()}: the column's slope, or what a builder set it to. */
    public double slope() {
        return slope;
    }

    /** TFC's {@code setSlope(slope)}; the builders that follow in the same pass see it. */
    public void setSlope(double slope) {
        this.slope = slope;
    }

    /** TFC's {@code context.random()}: the chunk's random source. */
    public XoroshiroRandomSource random() {
        if (random == null) {
            throw new IllegalStateException("this surface builder draws from the chunk random; build the column with one");
        }
        return random;
    }

    /**
     * What the column holds at {@code y}: a block this pass wrote, else rock up to the top, then water up to the block below
     * sea level, then air. Heights below the world are air, as a chunk returns void air there (and drops writes).
     */
    public Fill fill(int y) {
        if (y < minY) {
            return Fill.AIR;
        }
        final SurfaceBlock written = block(y);
        if (written != null) {
            return written.isRawRock() ? Fill.ROCK : Fill.OTHER;
        }
        if (y <= topY) {
            final Fill opened = open == null ? null : open.apply(y);
            return opened == null ? Fill.ROCK : opened;
        }
        return y <= column.seaLevel() - 1 ? Fill.WATER : Fill.AIR;
    }

    /** Writes the block at {@code y}; a chunk drops writes below the world, and so does the pass. */
    public void set(int y, SurfaceBlock block) {
        if (y < minY) {
            return;
        }
        if (y < baseY) {
            final SurfaceBlock[] grown = new SurfaceBlock[out.length + (baseY - y)];
            System.arraycopy(out, 0, grown, baseY - y, out.length);
            out = grown;
            baseY = y;
        } else if (y - baseY >= out.length) {
            out = Arrays.copyOf(out, y - baseY + 1);
        }
        out[y - baseY] = block;
    }

    /** Writes the block of a rule at {@code y}: TFC's {@code setBlockState(y, SurfaceState)}. */
    public void set(int y, SurfaceRule rule) {
        set(y, rule.block(column, y));
    }

    /** @return The block written at {@code y}, or null. */
    SurfaceBlock block(int y) {
        final int i = y - baseY;
        return i < 0 || i >= out.length ? null : out[i];
    }

    /** @return The lowest height the blocks array starts at. */
    int baseY() {
        return baseY;
    }

    /** @return The blocks written, indexed {@code y - baseY()}; null where the pass wrote nothing. */
    SurfaceBlock[] blocks() {
        return out;
    }

    /** @return The lowest height of the pass. */
    int endY() {
        return endY;
    }
}
