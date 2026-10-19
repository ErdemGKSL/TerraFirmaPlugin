/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Node.java @ c3b4d9536 (ofAxis), and the AXIS property used by the water wheel,
 *       windmill and axle block entities (common/blockentities/rotation/)
 */
package dev.tfp.core.power;

import dev.tfp.core.world.Direction;
import java.util.Set;

/**
 * The horizontal axis a shaft-like block turns on: {@code X} for a shaft running east-west, {@code Z} for north-south. TFC's
 * {@code Direction.Axis} is only ever {@code X} or {@code Z} for these blocks.
 */
public enum ShaftAxis {
    X,
    Z;

    /** TFC {@code Node.ofAxis}: both faces of the axis, the only faces a shaft connects on. */
    public Set<Direction> faces() {
        return this == X ? Set.of(Direction.EAST, Direction.WEST) : Set.of(Direction.NORTH, Direction.SOUTH);
    }

    /** TFC {@code Direction.fromAxisAndDirection(axis, POSITIVE)}: the face a source's rotation is reported on. */
    public Direction positiveFace() {
        return this == X ? Direction.EAST : Direction.SOUTH;
    }
}
