/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light.candle;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * What the candle side asks of the world (PAR-HEAT-11). Device handlers never write blocks themselves for a burnout: they
 * hand it to the pipeline ({@link #burnout}); the writes below run on the block's region thread, in the apply step of a
 * decision or of a player's own action.
 */
public interface CandleEffects {
    /** The sounds this side plays (vanilla's). */
    enum Sound {
        /** {@code SoundEvents.CANDLE_EXTINGUISH} (vanilla {@code AbstractCandleBlock.extinguish}). */
        EXTINGUISH,
        /** {@code SoundEvents.CAKE_ADD_CANDLE} (TFC {@code CandleBlockItem.useOn}). */
        ADD_CANDLE,
        /** {@code SoundEvents.PUMPKIN_CARVE} (TFC {@code TFCPumpkinBlock.useItemOn}). */
        CARVE
    }

    /** Queues a burnout decision for the block at {@code block} (decide async, apply sync). Region thread. */
    void burnout(Block block);

    /** Calls the burnout check of {@code block} again in {@code ticks} server ticks while its chunk stays loaded. */
    void burnoutIn(Block block, long ticks);

    /**
     * Changes the state of our block at {@code block} keeping the block (and its counter): {@code lit}, nothing else.
     * Returns whether it was written.
     */
    boolean setState(Block block, BlockStateRef state);

    /** Places {@code state} as a new block at {@code block} (another block id: candle cake, jack-o'-lantern, cake). */
    boolean place(Block block, BlockStateRef state);

    /** Places vanilla's carved pumpkin facing {@code facing} (north/east/south/west) at {@code block}. */
    void placeCarvedPumpkin(Block block, String facing);

    /** The facing of the vanilla carved pumpkin at {@code block}, empty for any other block. */
    Optional<String> carvedPumpkin(Block block);

    /** Plays {@code sound} at the block. */
    void sound(Block block, Sound sound);

    /** TFC {@code held.shrink(1)} on the player's hand. */
    void takeOne(Player player, EquipmentSlot hand);

    /** TFC {@code Helpers.damageItem(held, player, hand)}: one point of wear on the stack in the hand. */
    void damageHeld(Player player, EquipmentSlot hand);

    /** Drops {@code stack} at the block's centre ({@code Block.dropResources}). */
    void drop(Block block, ItemStack stack);
}
