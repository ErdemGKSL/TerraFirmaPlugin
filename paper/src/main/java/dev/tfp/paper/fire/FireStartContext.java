/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (the event's fields)
 */
package dev.tfp.paper.fire;

import dev.tfp.core.fuel.FireStart;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * One fire start as TFC's {@code StartFireEvent} carries it: the targeted block, the targeted face, the player (absent
 * for dispensers, projectiles and burning torches), a copy of the stack that started it and the strength.
 *
 * @param block    the targeted block ({@code event.getPos()}); read it on its region thread
 * @param face     the targeted face ({@code event.getTargetedFace()})
 * @param player   the player, or {@code null}
 * @param stack    a copy of the starting stack (empty when none)
 * @param strength STRONG may light destructive things (fire blocks, log piles); WEAK only harmless ones
 */
public record FireStartContext(Block block, BlockFace face, Player player, ItemStack stack,
                               FireStart.Strength strength) {
    /** TFC {@code isStrong()}. */
    public boolean strong() {
        return strength == FireStart.Strength.STRONG;
    }
}
