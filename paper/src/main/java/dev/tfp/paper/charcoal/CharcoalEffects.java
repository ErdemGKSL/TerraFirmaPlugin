/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoal;

import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The world writes of the log pile and the charcoal pit. The deferred writes ({@link #show}, {@link #place}, {@link #air},
 * {@link #fire}) run as region tasks after the current device callback, in the order they were asked for, because a
 * device callback must not replace its own block while the router still holds its record. Each re-checks the block
 * before it writes (an expected block id), so a block broken meanwhile is left alone.
 */
public interface CharcoalEffects {
    /** The sounds. */
    enum Sound {
        /** TFC {@code playPlaceSound} of the log pile (wood). */
        LOG_PLACE,
        /** TFC {@code lightLogPile}: {@code BLAZE_SHOOT}. */
        LIGHT,
        /** The charcoal pile's place sound (TFC {@code BlockItemPlacement.postPlacement}). */
        CHARCOAL_PLACE,
        /** The charcoal pile's break sound, for a layer taken off. */
        CHARCOAL_BREAK
    }

    /** Deferred: sets the state of the custom block at {@code block} (no physics) while it is still {@code state.id()}. */
    void show(Block block, BlockStateRef state);

    /**
     * Deferred: places {@code state} as a new custom block at {@code block} (replacing what is there when its id is
     * {@code replaceId}, or when it is air for a {@code null} {@code replaceId}), then runs {@code then} on the same
     * region thread when it was placed.
     */
    void place(Block block, BlockStateRef state, String replaceId, Runnable then);

    /** Deferred: turns the custom block {@code expectedId} at {@code block} into air (TFC {@code setBlock(AIR)}, no drops). */
    void air(Block block, String expectedId);

    /**
     * Deferred: places vanilla fire at {@code block} when it is air ({@code replaceId == null}) or the custom block
     * {@code replaceId}.
     */
    void fire(Block block, String replaceId);

    /** Runs {@code task} on the block's region thread {@code ticks} server ticks later (TFC {@code scheduleTick}). */
    void later(Block block, long ticks, Runnable task);

    /** Breaks the custom block at {@code block} now, as a removal (its drops run). */
    void breakBlock(Block block);

    /** Now: places {@code state} as a new custom block (a player's placement). Returns whether it was placed. */
    boolean placeNow(Block block, BlockStateRef state);

    /** Now: changes the state of the custom block at {@code block} with neighbour updates (a player's click). */
    void setNow(Block block, BlockStateRef state);

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    void give(Player player, ItemStack stack);

    /** TFC {@code Helpers.spawnItem} at the block centre. */
    void spawn(Block block, ItemStack stack);

    void sound(Block block, Sound sound);
}
