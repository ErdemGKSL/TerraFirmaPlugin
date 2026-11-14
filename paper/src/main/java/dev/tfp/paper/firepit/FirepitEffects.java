/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit;

import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * What the firepit does to the world around its record (PAR-HEAT-06). The handler never changes blocks itself: the block
 * state write, the removal of a firepit that lost its floor and the timed callback are queued here. Region thread of the
 * block; tests record the calls.
 */
public interface FirepitEffects {
    /** The sounds TFC plays. */
    enum Sound {
        /** {@code SoundEvents.FIRE_EXTINGUISH} ({@code extinguish}). */
        EXTINGUISH,
        /** {@code playPlaceSound(SoundType.WOOD)} ({@code tryInsertLog}). */
        LOG,
        /** {@code SoundEvents.SAND_BREAK} (taking the ash). */
        ASH
    }

    /** What the held item does to the firepit. */
    enum Douse {
        NONE,
        /** TFC {@code ItemAbilities.SHOVEL_DOUSE}: the shovel takes one damage. */
        SHOVEL,
        /** A water bucket (our divergence): it is emptied. */
        WATER
    }

    /** Writes the custom block state (lit, smoke level) on the block, next tick, if it is still a firepit. */
    void show(Block block, BlockStateRef state);

    void sound(Block block, Sound sound);

    /** The smoke of a lit firepit on an interaction (TFC's client particles; ours only on explicit actions). */
    void smoke(Block block, int smokeLevel);

    /** TFC {@code Helpers.spawnItem} at the block. */
    void spawn(Block block, ItemStack stack);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    void give(Player player, ItemStack stack);

    /** Uses up the douse item in {@code hand} (shovel damage, bucket emptied); nothing in creative. */
    void useDouse(Player player, EquipmentSlot hand, Douse douse);

    /** Calls the firepit back (a neighbour update) in {@code ticks} server ticks while the chunk stays loaded. */
    void callbackIn(Block block, long ticks);

    /** Removes the firepit (TFC {@code updateShape} turns it to air when it loses its floor), next tick. */
    void breakBlock(Block block);
}
