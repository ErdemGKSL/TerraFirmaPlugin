/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.chest;

import java.util.Locale;

/**
 * PAR-DECO-04: a horizontal direction in vanilla's order (north, east, south, west, clockwise seen from above), with the
 * quarter turns {@code ChestBlock} uses ({@code Direction.getClockWise}, {@code getCounterClockWise}).
 */
public enum ChestSide {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    private final int dx;
    private final int dz;

    ChestSide(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public ChestSide clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public ChestSide counterClockwise() {
        return values()[(ordinal() + 3) % 4];
    }

    public ChestSide opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    /** True when both are on the same axis (north/south or east/west). */
    public boolean sameAxis(ChestSide other) {
        return ordinal() % 2 == other.ordinal() % 2;
    }

    /** The property value, {@code north}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ChestSide of(String id) {
        return switch (id) {
            case "north" -> NORTH;
            case "east" -> EAST;
            case "south" -> SOUTH;
            case "west" -> WEST;
            default -> throw new IllegalArgumentException("not a horizontal direction: " + id);
        };
    }
}
