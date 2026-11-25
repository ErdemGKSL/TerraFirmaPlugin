/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Objects;

/**
 * What the rotation network actor reports about one axle shaft (PAR-MECH-12): its axis, the hand it turns in (TFC's incoming
 * rotation direction), the source's signed speed in radians per tick and its network ({@link #NO_NETWORK} when disconnected,
 * then it rests).
 */
public record ShaftReport(BlockPos pos, RotationAxis axis, Direction direction, float speed, long network) {
    public static final long NO_NETWORK = -1L;

    public ShaftReport {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(direction, "direction");
    }

    /** A shaft that turns with no network: at rest, its hand the positive end of its axis. */
    public static ShaftReport resting(BlockPos pos, RotationAxis axis) {
        return new ShaftReport(pos, axis, axis.positive(), 0f, NO_NETWORK);
    }
}
