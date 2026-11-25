/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java @ c3b4d9536 (attemptToRing: pitch, hammer, volume 2)
 */
package dev.tfp.paper.bell;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/** PAR-METAL-18: what a ring does in the world. Region thread of the bell. */
public interface BellEffects {
    /** TFC {@code attemptToRing}: the bell sound (vanilla {@code block.bell.use}) at {@code pitch} (volume {@link BellRules#VOLUME}) and the block change game event. */
    void ring(Block bell, Entity by, float pitch);
}
