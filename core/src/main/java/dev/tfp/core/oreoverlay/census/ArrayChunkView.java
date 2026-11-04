/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

import java.util.Arrays;

/**
 * A {@link ChunkView} backed by arrays: filled once by one thread (the builder), then read by the census. Every cell starts as
 * {@link ExposureRule#SOLID} and every column's surface at {@link #minY()}. Target indices must fit in a {@code short}.
 */
public final class ArrayChunkView implements ChunkView {
    /** Width of the view including the border (x and z from -1 to 16). */
    public static final int SPAN = 18;

    private final int minY;
    private final int height;
    private final short[] cells;
    private final int[] surface;

    public ArrayChunkView(int minY, int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive, got " + height);
        }
        this.minY = minY;
        this.height = height;
        this.cells = new short[SPAN * SPAN * height];
        Arrays.fill(cells, (short) ExposureRule.SOLID);
        this.surface = new int[SPAN * SPAN];
        Arrays.fill(surface, minY);
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int cell(int x, int y, int z) {
        return cells[index(x, y, z)];
    }

    @Override
    public int surfaceY(int x, int z) {
        return surface[column(x, z)];
    }

    /** Sets one cell ({@link ExposureRule#SOLID}, {@link ExposureRule#OPEN} or a target index up to {@link Short#MAX_VALUE}). */
    public ArrayChunkView set(int x, int y, int z, int cell) {
        if (cell < ExposureRule.OPEN || cell > Short.MAX_VALUE) {
            throw new IllegalArgumentException("cell code out of range: " + cell);
        }
        cells[index(x, y, z)] = (short) cell;
        return this;
    }

    /** Sets a column's surface ({@link ChunkView#surfaceY}). */
    public ArrayChunkView surface(int x, int z, int surfaceY) {
        surface[column(x, z)] = surfaceY;
        return this;
    }

    /** Fills a box (inclusive bounds, local coordinates) with one cell code. */
    public ArrayChunkView fill(int x0, int y0, int z0, int x1, int y1, int z1, int cell) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    set(x, y, z, cell);
                }
            }
        }
        return this;
    }

    private int column(int x, int z) {
        if (x < -1 || x > 16 || z < -1 || z > 16) {
            throw new IndexOutOfBoundsException("local x/z out of [-1, 16]: " + x + ", " + z);
        }
        return (x + 1) * SPAN + (z + 1);
    }

    private int index(int x, int y, int z) {
        final int dy = y - minY;
        if (dy < 0 || dy >= height) {
            throw new IndexOutOfBoundsException("y out of [" + minY + ", " + (minY + height) + "): " + y);
        }
        return column(x, z) * height + dy;
    }
}
