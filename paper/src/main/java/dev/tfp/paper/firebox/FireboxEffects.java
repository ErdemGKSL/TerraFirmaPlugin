/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firebox;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** PAR-HEAT-12: what the firebox does to the world, behind an interface for the tests. Region thread of the block. */
public interface FireboxEffects {
    /** What a held item does as a douser (TFC {@code SHOVEL_DOUSE}, our water bucket as for the firepit). */
    enum Douse {
        NONE,
        SHOVEL,
        WATER
    }

    /** Shows the {@code lit} state of the firebox block (TFC {@code setBlockAndUpdate(LIT)}). */
    void show(Block block, boolean lit);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}. */
    void give(Player player, ItemStack stack);

    /** Uses up the douser in {@code hand} (the water bucket empties, a shovel takes 1 damage). */
    void useDouse(Player player, EquipmentSlot hand, Douse douse);

    /** Queues the device router's update for the block in {@code ticks} ticks (only the latest per block runs). */
    void callbackIn(Block block, long ticks);
}
