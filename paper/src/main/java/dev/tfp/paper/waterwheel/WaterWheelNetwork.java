/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (the SourceNode
 *       of the constructor: ofAxis connections, the positive direction of the axis; onLoadAdditional ADD_SOURCE,
 *       onUnloadAdditional REMOVE)
 */
package dev.tfp.paper.waterwheel;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.world.BlockPos;
import java.util.UUID;

/**
 * Where a water wheel reports itself to the rotation network: a source node on both faces of its axis, rotating on the
 * positive face at the wheel's speed ({@link dev.tfp.core.power.Transmission#sourceNode}). The rotation module provides this
 * service through {@code ModuleContext.services()} (PAR-MECH-17); until it does, a wheel turns but drives nothing.
 *
 * <p>Any thread: implementations only enqueue.
 */
public interface WaterWheelNetwork {
    /** Reports nothing. */
    WaterWheelNetwork NONE = new WaterWheelNetwork() {
        @Override
        public void source(UUID world, BlockPos pos, ShaftAxis axis, float speed) {
        }

        @Override
        public void remove(UUID world, BlockPos pos) {
        }
    };

    /** The wheel at {@code pos} turns at {@code speed} radians per tick (TFC ADD_SOURCE, then its rotation's speed). */
    void source(UUID world, BlockPos pos, ShaftAxis axis, float speed);

    /** The wheel at {@code pos} is gone or unloaded (TFC REMOVE). */
    void remove(UUID world, BlockPos pos);
}
