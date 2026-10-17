/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/BranchDirection.java @ c3b4d9536 (the values, mirror and rotate;
 * the connection offsets and the natural flag are not needed by worldgen)
 */
package dev.tfp.core.worldgen.tree;

import java.util.Locale;

/**
 * The {@code branch_direction} of a TFC log or wood block: which neighbour the branch connects to, or {@link #NONE} for a
 * placed log. Templates are mirrored and rotated, and their branch directions with them.
 */
public enum BranchDirection {
    NONE,
    NORTH_WEST, NORTH, NORTH_EAST, WEST, EAST, SOUTH_WEST, SOUTH, SOUTH_EAST,
    DOWN_NORTH_WEST, DOWN_NORTH, DOWN_NORTH_EAST, DOWN_WEST, DOWN, DOWN_EAST, DOWN_SOUTH_WEST, DOWN_SOUTH, DOWN_SOUTH_EAST,
    UP_NORTH_WEST, UP_NORTH, UP_NORTH_EAST, UP_WEST, UP, UP_EAST, UP_SOUTH_WEST, UP_SOUTH, UP_SOUTH_EAST,
    TRUNK_NORTH_WEST, TRUNK_NORTH_EAST, TRUNK_SOUTH_WEST, TRUNK_SOUTH_EAST;

    /** Mirror over the Z axis (so flip the X axis). */
    private static final BranchDirection[] MIRROR = {
        NONE,
        NORTH_EAST, NORTH, NORTH_WEST, EAST, WEST, SOUTH_EAST, SOUTH, SOUTH_WEST,
        DOWN_NORTH_EAST, DOWN_NORTH, DOWN_NORTH_WEST, DOWN_EAST, DOWN, DOWN_WEST, DOWN_SOUTH_EAST, DOWN_SOUTH, DOWN_SOUTH_WEST,
        UP_NORTH_EAST, UP_NORTH, UP_NORTH_WEST, UP_EAST, UP, UP_WEST, UP_SOUTH_EAST, UP_SOUTH, UP_SOUTH_WEST,
        TRUNK_NORTH_EAST, TRUNK_NORTH_WEST, TRUNK_SOUTH_EAST, TRUNK_SOUTH_WEST
    };

    /** Rotate 90 degrees clockwise. */
    private static final BranchDirection[] ROTATE = {
        NONE,
        NORTH_EAST, EAST, SOUTH_EAST, NORTH, SOUTH, NORTH_WEST, WEST, SOUTH_WEST,
        DOWN_NORTH_EAST, DOWN_EAST, DOWN_SOUTH_EAST, DOWN_NORTH, DOWN, DOWN_SOUTH, DOWN_NORTH_WEST, DOWN_WEST, DOWN_SOUTH_WEST,
        UP_NORTH_EAST, UP_EAST, UP_SOUTH_EAST, UP_NORTH, UP, UP_SOUTH, UP_NORTH_WEST, UP_WEST, UP_SOUTH_WEST,
        TRUNK_NORTH_EAST, TRUNK_SOUTH_EAST, TRUNK_NORTH_WEST, TRUNK_SOUTH_WEST
    };

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    public String serializedName() {
        return serializedName;
    }

    public static BranchDirection byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }

    /** {@code rotate(Rotation)}: the direction after the template rotation. */
    public BranchDirection rotate(Rotation rotation) {
        return switch (rotation) {
            case NONE -> this;
            case CLOCKWISE_90 -> rotate();
            case CLOCKWISE_180 -> rotate().rotate();
            case COUNTERCLOCKWISE_90 -> rotate().rotate().rotate();
        };
    }

    /** {@code mirror(Mirror)}: the direction after the template mirror. */
    public BranchDirection mirror(Mirror mirror) {
        return switch (mirror) {
            case NONE -> this;
            case LEFT_RIGHT -> mirror().rotate().rotate();
            case FRONT_BACK -> mirror();
        };
    }

    private BranchDirection rotate() {
        return ROTATE[ordinal()];
    }

    private BranchDirection mirror() {
        return MIRROR[ordinal()];
    }

    /** Vanilla's {@code Rotation}, in its order (the template settings draw {@code nextInt(4)} over these values). */
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }

    /** Vanilla's {@code Mirror}, in its order (the template settings draw {@code nextInt(3)} over these values). */
    public enum Mirror { NONE, LEFT_RIGHT, FRONT_BACK }
}
