/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import java.util.UUID;

/**
 * The packets of the spinning shafts (PAR-MECH-12): the wire of one viewer. {@link SpinShafts} decides what and when;
 * {@code PacketSpinWire} encodes PacketEvents packets. Implementations must be thread-safe (the shafts call it under their own
 * lock from the actor, Netty and global-scheduler threads).
 */
public interface SpinWire {

    /**
     * Spawns the shaft's {@code item_display} at the block centre showing {@code halfAngle} (left and right rotation each turn
     * that much about the axis), with no interpolation.
     */
    void spawn(UUID viewer, int entityId, BlockPos pos, String item, RotationAxis axis, double halfAngle);

    /** Sends the next segment: both rotations at {@code halfAngle}, interpolation delay 0 and {@code duration} ticks. */
    void turn(UUID viewer, int entityId, RotationAxis axis, double halfAngle, int duration);

    /** Destroys the viewer's displays. */
    void remove(UUID viewer, int... entityIds);

    /** The viewer left: forget its connection. */
    default void forget(UUID viewer) {
    }
}
