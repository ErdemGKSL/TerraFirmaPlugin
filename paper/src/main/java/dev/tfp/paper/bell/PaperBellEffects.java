/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java @ c3b4d9536 (attemptToRing: pitch, hammer, volume 2)
 */
package dev.tfp.paper.bell;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.GameEvent;
import org.bukkit.Location;

/** PAR-METAL-18: {@link BellEffects} on Paper. */
public final class PaperBellEffects implements BellEffects {
    @Override
    public void ring(Block bell, Entity by, float pitch) {
        Location at = bell.getLocation().add(0.5, 0.5, 0.5);
        bell.getWorld().playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, BellRules.VOLUME, pitch);
        bell.getWorld().sendGameEvent(by, GameEvent.BLOCK_CHANGE, at.toVector());
    }
}
