/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (the SourceNode
 *       of the constructor: ofAxis connections, always the positive direction; onLoadAdditional ADD_SOURCE,
 *       onUnloadAdditional REMOVE)
 */
package dev.tfp.paper.windmill;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.world.BlockPos;
import java.util.UUID;

/**
 * Where a windmill reports itself to the rotation network: a source node on both faces of its axis, rotating on the positive
 * face at the windmill's speed ({@link dev.tfp.core.power.Transmission#sourceNode}). The rotation module provides this
 * service through {@code ModuleContext.services()} (PAR-MECH-17); until it does, a windmill turns but drives nothing.
 *
 * <p>Any thread: implementations only enqueue.
 */
public interface WindmillNetwork {
    /** Reports nothing. */
    WindmillNetwork NONE = new WindmillNetwork() {
        @Override
        public void source(UUID world, BlockPos pos, ShaftAxis axis, float speed) {
        }

        @Override
        public void remove(UUID world, BlockPos pos) {
        }
    };

    /** The windmill at {@code pos} turns at {@code speed} radians per tick. */
    void source(UUID world, BlockPos pos, ShaftAxis axis, float speed);

    /** The windmill at {@code pos} is gone or unloaded. */
    void remove(UUID world, BlockPos pos);
}
