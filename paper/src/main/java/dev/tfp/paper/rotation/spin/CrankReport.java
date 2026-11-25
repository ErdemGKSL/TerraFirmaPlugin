/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Objects;

/**
 * What the crank feed reads about one crankshaft base (PAR-MECH-26): its facing, whether its shaft stands in front of it (and
 * a steel pump of the same facing in front of the shaft: TFC draws a brass attachment on the piston then), the
 * rotation it receives on its sink face (TFC {@code getRotationNode().rotation()}: the direction and the source's signed speed;
 * {@code connected} false while it is in no network, then it rests) and the axle item of the stub (TFC draws the extension
 * while an axle is next to the sink face and the base is connected), or null.
 */
public record CrankReport(BlockPos base, Direction facing, boolean shaft, boolean pump, boolean connected, Direction direction,
        float speed, String stub) {
    public CrankReport {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(direction, "direction");
        pump = pump && shaft;
        if (!connected) {
            speed = 0f;
            stub = null;
        }
    }

    /** A base in no network. */
    public static CrankReport resting(BlockPos base, Direction facing, boolean shaft, boolean pump) {
        return new CrankReport(base, facing, shaft, pump, false, Direction.UP, 0f, null);
    }
}
