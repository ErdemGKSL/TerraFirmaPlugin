/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Node.java @ c3b4d9536 (ofAxis)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** A block axis ({@code axis=x|y|z}, vanilla's {@code Direction.Axis}). */
public enum RotationAxis {
    X(Direction.WEST, Direction.EAST),
    Y(Direction.DOWN, Direction.UP),
    Z(Direction.NORTH, Direction.SOUTH);

    private final Direction negative;
    private final Direction positive;

    RotationAxis(Direction negative, Direction positive) {
        this.negative = negative;
        this.positive = positive;
    }

    /** TFC {@code Node.ofAxis(axis)}: the two faces on this axis. */
    public Set<Direction> faces() {
        return EnumSet.of(negative, positive);
    }

    /** Vanilla {@code Direction.fromAxisAndDirection(axis, POSITIVE)}. */
    public Direction positive() {
        return positive;
    }

    public Direction negative() {
        return negative;
    }

    /** {@code fromAxisAndDirection(axis, positive ? POSITIVE : NEGATIVE)}. */
    public Direction direction(boolean positive) {
        return positive ? this.positive : negative;
    }

    /** The axis of a face. */
    public static RotationAxis of(Direction face) {
        return switch (face) {
            case WEST, EAST -> X;
            case DOWN, UP -> Y;
            case NORTH, SOUTH -> Z;
        };
    }

    /** True for the faces that point along the positive axis (up, south, east). */
    public static boolean isPositive(Direction face) {
        return face.dx() + face.dy() + face.dz() > 0;
    }

    /** The state property value ({@code x}). */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The axis of a property value; {@link IllegalArgumentException} for anything else. */
    public static RotationAxis parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
