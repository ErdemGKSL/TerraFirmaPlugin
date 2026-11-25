/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import java.util.List;
import java.util.random.RandomGenerator;

/** PAR-FARM-29: Minecraft's {@code Direction}s in its {@code get3DDataValue} order (down, up, north, south, west, east). */
public enum Face {
    DOWN(0, -1, 0, "down"), UP(0, 1, 0, "up"), NORTH(0, 0, -1, "north"), SOUTH(0, 0, 1, "south"), WEST(-1, 0, 0, "west"),
    EAST(1, 0, 0, "east");

    /** {@code Direction.Plane.HORIZONTAL}: north, east, south, west. */
    public static final List<Face> HORIZONTAL = List.of(NORTH, EAST, SOUTH, WEST);
    private static final Face[] ALL = values();

    public final int dx;
    public final int dy;
    public final int dz;
    /** The block state property of this face and the name of the direction. */
    public final String key;

    Face(int dx, int dy, int dz, String key) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.key = key;
    }

    public Face opposite() {
        return ALL[ordinal() ^ 1];
    }

    public boolean horizontal() {
        return this != DOWN && this != UP;
    }

    /** {@code Direction.getClockWise} of a horizontal face (north, east, south, west). */
    public Face clockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException("not horizontal: " + this);
        };
    }

    public Face counterClockWise() {
        return clockWise().opposite();
    }

    /** {@code Direction.getRandom}: one draw over the six directions in {@code get3DDataValue} order. */
    public static Face random(RandomGenerator random) {
        return ALL[random.nextInt(ALL.length)];
    }

    /** {@code Direction.Plane.HORIZONTAL.getRandomDirection}: one draw over north, east, south, west. */
    public static Face randomHorizontal(RandomGenerator random) {
        return HORIZONTAL.get(random.nextInt(4));
    }
}
