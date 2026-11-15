/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pot;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What the pot does to the world around its record (PAR-HEAT-09). The handler never changes blocks itself: the block state
 * write, the swap back to a firepit and the timed callback are queued here. Region thread of the block; tests record the
 * calls.
 */
public interface PotEffects {
    /** The sounds TFC plays. */
    enum Sound {
        /** {@code SoundEvents.FIRE_EXTINGUISH} ({@code extinguish}). */
        EXTINGUISH,
        /** {@code playPlaceSound(SoundType.WOOD)} ({@code tryInsertLog}). */
        LOG,
        /** {@code SoundEvents.SAND_BREAK} (taking the ash). */
        ASH,
        /** {@code TFCSounds.ITEM_COOL} (taking a lit pot off with a bare hand). */
        COOL
    }

    /** Writes the custom block state (lit, smoke level) on the block, next tick, if it is still a pot. */
    void show(Block block, BlockStateRef state);

    void sound(Block block, Sound sound);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    void give(Player player, ItemStack stack);

    /** TFC {@code Helpers.spawnItem} at the block. */
    void spawn(Block block, ItemStack stack);

    /** The bucket sound of a fluid moved between the pot and a held container. */
    void transfer(Block block, FluidAmount fluid, boolean intoItem);

    /** TFC {@code TFCDamageTypes.pot(player, amount)}: {@code tfp:pot} damage. */
    void hurt(Player player, float amount);

    /** Calls the pot back (a neighbour update) in {@code ticks} server ticks while the chunk stays loaded. */
    void callbackIn(Block block, long ticks);

    /** Removes the pot (it lost its floor), next tick. */
    void breakBlock(Block block);

    /**
     * TFC {@code AbstractFirepitBlockEntity.convertTo(FIREPIT)}: the pot turns back into a firepit, next tick. Only when
     * the conversion succeeds, {@code atBlock} runs on the block's region thread, then {@code atPlayer} on
     * {@code player}'s thread, or {@code playerGone} on the block's region thread when the player has left by then; a
     * refused conversion (the firepit cannot be placed, the pot is gone) runs none of them, so no pot is given twice.
     */
    void toFirepit(Block block, Player player, Runnable atBlock, Runnable atPlayer, Runnable playerGone);

    /** Whether a swap back to a firepit is already queued for {@code block} (a second click gives no second pot). */
    default boolean converting(Block block) {
        return false;
    }

    /** TFC {@code openMenu}: the pot screen for {@code player}, next tick (after the click's record is stored). */
    void openScreen(Player player, Block block);
}
