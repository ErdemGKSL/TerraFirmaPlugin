/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.device.automation;

import java.util.Locale;
import java.util.Optional;

/**
 * A block face in Minecraft's {@code Direction} order (DOWN, UP, NORTH, SOUTH, WEST, EAST), as TFC's
 * {@code SidedHandler} indexes its sides. Platform-free; the Paper side maps {@code BlockFace} onto it.
 */
public enum Face {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    /** Minecraft {@code Direction.Axis}. */
    public enum Axis {
        X, Y, Z
    }

    private final int dx;
    private final int dy;
    private final int dz;

    Face(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public Axis axis() {
        return dx != 0 ? Axis.X : dy != 0 ? Axis.Y : Axis.Z;
    }

    /** {@code Direction.Plane.HORIZONTAL}. */
    public boolean horizontal() {
        return dy == 0;
    }

    public Face opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** The face pointing along the unit offset, empty for anything that is not one of the six neighbours. */
    public static Optional<Face> of(int dx, int dy, int dz) {
        for (Face f : values()) {
            if (f.dx == dx && f.dy == dy && f.dz == dz) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    /** A block state value ({@code "up"}, {@code "north"}, ...), empty when unknown. */
    public static Optional<Face> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
