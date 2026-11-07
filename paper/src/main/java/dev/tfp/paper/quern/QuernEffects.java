/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The quern's world effects (PAR-PROC-04), apart so the device logic is tested without displays, sounds or schedulers.
 * Every method runs on the region thread that owns the block.
 */
public interface QuernEffects {
    /**
     * What the quern shows: its three slots and whether a grind runs.
     *
     * @param handstone the handstone slot, empty for none
     * @param input     the input slot
     * @param output    the output slot
     * @param grinding  whether a grind runs (the handstone turns)
     */
    record View(ItemStack handstone, ItemStack input, ItemStack output, boolean grinding) {
    }

    /** Draws {@code view} and sets the block state's {@code has_handstone} to match it (TFC {@code updateHandstone}). */
    void show(Block block, View view);

    /** Removes everything {@link #show} drew (the block is gone). */
    void hide(Block block);

    /** A grind started on {@code input}: TFC's drag sound. */
    void started(Block block, ItemStack input);

    /**
     * Turns the handstone through one grind and calls the device back at the finish tick (TFC's per-tick rotation and
     * {@code serverTick} finish), with grinding particles of the input.
     */
    void spin(Block block);

    /** Calls the device back in {@code ticks} ticks (the grind is not due yet). */
    void finishAt(Block block, long ticks);

    /**
     * A grind finished: TFC's {@code ARMOR_STAND_FALL} sound; when {@code brokenHandstone} is not empty the handstone broke
     * (TFC: stone and item break sounds, 15 particles of the handstone).
     */
    void finished(Block block, ItemStack brokenHandstone);

    /** TFC {@code Helpers.damageItem}: {@code handstone} with one more damage, or an empty stack when it broke. */
    ItemStack damage(ItemStack handstone);

    /** TFC {@code Helpers.spawnItem}: the output that did not fit, at the block centre. */
    void spawn(Block block, ItemStack stack);
}
