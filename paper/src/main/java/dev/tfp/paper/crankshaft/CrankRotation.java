/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java @ c3b4d9536 (the SinkNode's
 *       rotation(): the rotation the base receives on its sink face, null while it is in no network)
 */
package dev.tfp.paper.crankshaft;

import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import java.util.Optional;
import java.util.UUID;

/**
 * The rotation a crankshaft base receives from the network on its sink face ({@code CrankshaftRules.sinkFace}). The rotation
 * module provides it through {@code ModuleContext.services()} once the crankshaft is a network sink (PAR-MECH-21); until then
 * every crank reads as not in a network.
 *
 * <p>Any thread, never blocks: the latest rotation the network decided.
 */
@FunctionalInterface
public interface CrankRotation {
    /** No network knows a crankshaft. */
    CrankRotation NONE = (world, base) -> Optional.empty();

    /** The rotation of the base at {@code base}, empty when it is in no network. */
    Optional<Rotation> rotation(UUID world, BlockPos base);
}
