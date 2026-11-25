/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.world.BlockPos;
import java.util.UUID;

/**
 * The packets of a crankshaft's moving parts (PAR-MECH-26): the wire of one viewer, as {@link SpinWire} is for the axles.
 * {@link SpinCranks} decides what and when; {@code PacketSpinWire} encodes the packets. Thread-safe.
 */
public interface CrankWire {

    /** Spawns an {@code item_display} at the centre of {@code pos} showing {@code pose}, with no interpolation. */
    void spawnPart(UUID viewer, int entityId, BlockPos pos, String item, CrankParts.Pose pose);

    /** Sends the pose the display reaches after {@code duration} ticks (interpolation delay 0; 0 snaps). */
    void movePart(UUID viewer, int entityId, CrankParts.Pose pose, int duration);

    /** Destroys the viewer's displays. */
    void removeParts(UUID viewer, int... entityIds);
}
