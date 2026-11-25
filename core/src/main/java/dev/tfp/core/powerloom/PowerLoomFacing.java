/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (the constructor: the
 *       SinkNode's connection is FACING.getClockWise()),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PowerLoomBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.core.powerloom;

import dev.tfp.core.world.Direction;
import java.util.Optional;

/** The power loom's facing and the face its rotation comes in on (PAR-MECH-08). */
public final class PowerLoomFacing {
    private PowerLoomFacing() {}

    /** {@code HorizontalDirectionalBlock.FACING} by its name, empty for anything that is not horizontal. */
    public static Optional<Direction> facing(String name) {
        return switch (name == null ? "" : name) {
            case "north" -> Optional.of(Direction.NORTH);
            case "east" -> Optional.of(Direction.EAST);
            case "south" -> Optional.of(Direction.SOUTH);
            case "west" -> Optional.of(Direction.WEST);
            default -> Optional.empty();
        };
    }

    /** {@code Direction.getClockWise()} seen from above: north to east to south to west to north. */
    public static Direction clockWise(Direction horizontal) {
        return switch (horizontal) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> throw new IllegalArgumentException("not horizontal: " + horizontal);
        };
    }

    /** The face of the loom that connects to the network: the sink node's connection, {@code FACING.getClockWise()}. */
    public static Optional<Direction> sinkFace(String facing) {
        return facing(facing).map(PowerLoomFacing::clockWise);
    }
}
