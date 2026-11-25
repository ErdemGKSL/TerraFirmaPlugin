/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java @ c3b4d9536 (getCrankShaftAt)
 */
package dev.tfp.paper.pump;

import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import org.bukkit.block.Block;

/**
 * The crankshaft a pump reads (TFC {@code CrankshaftBlockEntity.getCrankShaftAt(level, pos, face.getOpposite())}: a shaft part
 * next to the pump and its base two blocks away, both facing the pump). The crankshaft (PAR-MECH-05) provides this service
 * through {@code ModuleContext.services()}; until it does no pump is powered.
 */
@FunctionalInterface
public interface ShaftSource {
    /**
     * The rotation of the crankshaft behind a pump.
     *
     * @param pump the pump block
     * @param side the pump's face the shaft is on (the opposite of the pump's facing)
     * @return the rotation of the crankshaft's node, or {@code null} when there is no crankshaft there or it is not in a network
     */
    Rotation rotationAt(Block pump, Direction side);
}
