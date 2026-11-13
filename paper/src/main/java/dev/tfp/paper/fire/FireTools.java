/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fire;

import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The player-side effects of the fire starting tools ({@link PaperFireTools} on a server, a recorder in tests). Thread of
 * the player.
 */
public interface FireTools {
    /** A targeted block face. */
    record Target(Block block, BlockFace face) {
    }

    /** TFC {@code getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE)}: the block the player looks at. */
    Optional<Target> target(Player player);

    /** Damages the tool in {@code hand} by one (breaking it at its maximum). */
    void damage(Player player, EquipmentSlot hand);

    /** Puts the off-hand item on cooldown (TFC, when a firestarter breaks: 20 ticks). */
    void offhandCooldown(Player player, int ticks);

    /** The flint and pyrite strike: vanilla's flint and steel sound at the block, pitch {@code 0.8 + 0.4 x random}. */
    void strikeSound(Block block);
}
