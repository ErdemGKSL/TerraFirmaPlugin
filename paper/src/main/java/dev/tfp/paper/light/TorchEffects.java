/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * What the torch handlers ask of the world (PAR-HEAT-10). Handlers never write blocks: a burnt-out torch is handed to the
 * burnout pipeline ({@link #burnout}), which re-reads it on its region and swaps it under the commit budget.
 */
public interface TorchEffects {
    /** Queues a burnout check of the torch at {@code block} (decide async, apply sync). Region thread of the block. */
    void burnout(Block block);

    /**
     * Calls {@link #burnout} again in {@code ticks} server ticks while the chunk stays loaded (the burn time of a lit torch;
     * a newer request for the same block replaces the queued one). Region thread of the block.
     */
    void burnoutIn(Block block, long ticks);

    /** TFC {@code held.shrink(1)} on the player's hand. */
    void takeOne(Player player, EquipmentSlot hand);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    void give(Player player, ItemStack stack);
}
