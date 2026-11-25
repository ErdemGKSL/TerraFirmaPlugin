/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.rotation;

import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.rotation.RotationAxis;
import java.util.UUID;

/**
 * Where the spin visuals are sent: the packet layer of the platform ({@code ADD_ENTITY} + metadata of an {@code item_display},
 * metadata with {@code transformation_interpolation_duration/delay}, destroy). The service decides what and when; the sink only
 * encodes and sends. The integration module holds no packet library, so the sender lives in the paper module (PAR-MECH-12).
 */
public interface SpinSink {

    /**
     * Shows a spinning shaft to {@code viewer}: spawns its display and sends the first segment.
     *
     * @param entityId the display entity id, unique for this (viewer, shaft)
     */
    void spawn(UUID viewer, UUID world, BlockPos pos, RotationAxis axis, int entityId, RotationAnglesSpin.Update first);

    /** A later segment (or a speed change): one metadata packet, interpolation delay 0. */
    void update(UUID viewer, int entityId, RotationAnglesSpin.Update update);

    /** Removes the display from the viewer. */
    void remove(UUID viewer, int entityId);
}
