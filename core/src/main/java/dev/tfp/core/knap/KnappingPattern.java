/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/KnappingPattern.java @ c3b4d9536 (the bitset, the parsing rules, matches and its mirror)
 */
package dev.tfp.core.knap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A knapping pattern, or the knapped grid itself. A cell is <strong>on</strong> ({@code true}) when it is not clicked off.
 * A recipe pattern writes an on cell as any character other than a space, and an off cell as a space: the cells a player must
 * click are the off ones. The grid is 5x5 and starts with every cell on. A pattern may be smaller than 5x5 and then declares its
 * {@code defaultOn} value, which every grid cell outside the pattern must equal.
 *
 * <p>Cells are indexed {@code x + y * width}, so the button id of the 5x5 grid is {@code x + 5 * y}.
 */
public final class KnappingPattern {
    public static final int MAX_WIDTH = 5;
    public static final int MAX_HEIGHT = 5;

    private final int width;
    private final int height;
    private final boolean defaultOn;
    /** One bit per cell, bit {@code x + y * width} set when the cell is on. */
    private int data;

    /** The 5x5 knapping grid with every cell on and default off, as TFC's {@code new KnappingPattern()}. */
    public KnappingPattern() {
        this(MAX_WIDTH, MAX_HEIGHT, false);
    }

    public KnappingPattern(int width, int height, boolean defaultOn) {
        checkSize(width, height);
        this.width = width;
        this.height = height;
        this.defaultOn = defaultOn;
        this.data = mask(width, height);
    }

    /**
     * Parses recipe rows, as TFC's {@code KnappingPattern.CODEC}. Every row has the same width, the rows are at most 5 and the
     * columns are at most 5, and a pattern that is not 5x5 must give {@code defaultOn}.
     *
     * @param rows      the rows, one character per cell, a space is off and anything else is on
     * @param defaultOn the default for cells outside a smaller pattern, empty for a 5x5 pattern
     */
    public static KnappingPattern of(List<String> rows, Optional<Boolean> defaultOn) {
        final int height = rows.size();
        if (height == 0 || height > MAX_HEIGHT) throw new IllegalArgumentException("pattern must have [1, " + MAX_HEIGHT + "] rows");
        final int width = rows.get(0).length();
        if (width == 0 || width > MAX_WIDTH) throw new IllegalArgumentException("pattern must have [1, " + MAX_WIDTH + "] columns");
        if ((height != MAX_HEIGHT || width != MAX_WIDTH) && defaultOn.isEmpty()) {
            throw new IllegalArgumentException("default_on is required if the pattern is not " + MAX_WIDTH + "x" + MAX_HEIGHT);
        }
        final KnappingPattern pattern = new KnappingPattern(width, height, defaultOn.orElse(false));
        pattern.data = 0;
        for (int r = 0; r < height; r++) {
            final String row = rows.get(r);
            if (row.length() != width) throw new IllegalArgumentException("each row must be the same width");
            for (int c = 0; c < width; c++) {
                pattern.set(r * width + c, row.charAt(c) != ' ');
            }
        }
        return pattern;
    }

    /**
     * Parses a 5x5 knapped grid written one character per cell, row by row: {@code #} is on, {@code .} is off.
     * Used by the golden data, which writes grids in this form.
     */
    public static KnappingPattern grid(String cells) {
        if (cells.length() != MAX_WIDTH * MAX_HEIGHT) throw new IllegalArgumentException("a grid has 25 cells");
        final KnappingPattern grid = new KnappingPattern();
        grid.data = 0;
        for (int i = 0; i < cells.length(); i++) {
            final char c = cells.charAt(i);
            if (c != '#' && c != '.') throw new IllegalArgumentException("grid cells are # or .");
            grid.set(i, c == '#');
        }
        return grid;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean defaultIsOn() {
        return defaultOn;
    }

    public void setAll(boolean value) {
        data = value ? mask(width, height) : 0;
    }

    public void set(int x, int y, boolean value) {
        set(x + y * width, value);
    }

    public void set(int index, boolean value) {
        checkIndex(index);
        if (value) {
            data |= 1 << index;
        } else {
            data &= ~(1 << index);
        }
    }

    /** Clicks a cell off, as TFC's {@code onButtonPress} does. Clicking an off cell again changes nothing. */
    public void click(int index) {
        set(index, false);
    }

    public boolean get(int x, int y) {
        return get(x + y * width);
    }

    public boolean get(int index) {
        checkIndex(index);
        return ((data >> index) & 0b1) == 1;
    }

    /**
     * TFC {@code KnappingPattern#matches(other)}, with this as the grid: whether {@code other} (a recipe pattern) is found somewhere
     * in this grid, at any offset, either as written or mirrored left to right. Cells of this grid outside {@code other} must equal
     * {@code other.defaultIsOn()}. Does not change either pattern.
     */
    public boolean matches(KnappingPattern other) {
        for (int dx = 0; dx <= this.width - other.width; dx++) {
            for (int dy = 0; dy <= this.height - other.height; dy++) {
                if (matchesAt(other, dx, dy, false) || matchesAt(other, dx, dy, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(KnappingPattern other, int startX, int startY, boolean mirror) {
        for (int x = 0; x < this.width; x++) {
            for (int y = 0; y < this.height; y++) {
                final int gridIndex = y * width + x;
                if (x < startX || y < startY || x - startX >= other.width || y - startY >= other.height) {
                    if (get(gridIndex) != other.defaultOn) {
                        return false;
                    }
                } else {
                    final int otherIndex = mirror
                        ? (y - startY) * other.width + (other.width - 1 - (x - startX))
                        : (y - startY) * other.width + (x - startX);
                    if (get(gridIndex) != other.get(otherIndex)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The rows as written in recipe JSON: {@code #} on, space off. */
    public List<String> rows() {
        final List<String> rows = new ArrayList<>(height);
        for (int r = 0; r < height; r++) {
            final StringBuilder row = new StringBuilder(width);
            for (int c = 0; c < width; c++) {
                row.append(get(c, r) ? '#' : ' ');
            }
            rows.add(row.toString());
        }
        return rows;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnappingPattern p)) return false;
        final int m = mask(width, height);
        return width == p.width && height == p.height && defaultOn == p.defaultOn && (data & m) == (p.data & m);
    }

    @Override
    public int hashCode() {
        return ((width * 31 + height) * 31 + (defaultOn ? 1 : 0)) * 31 + (data & mask(width, height));
    }

    @Override
    public String toString() {
        return "KnappingPattern" + rows() + (defaultOn ? " default on" : "");
    }

    private static void checkSize(int width, int height) {
        if (width < 1 || width > MAX_WIDTH || height < 1 || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("pattern size must be within " + MAX_WIDTH + "x" + MAX_HEIGHT);
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= width * height) throw new IndexOutOfBoundsException("cell " + index);
    }

    private static int mask(int width, int height) {
        return (1 << (width * height)) - 1;
    }
}
