/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.compost;

import dev.tfp.core.compost.CompostBin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The composter's world effects (PAR-PROC-11), apart so the device logic is tested without block states, sounds, hoppers
 * or schedulers. Every method runs on the region thread that owns the block.
 */
public interface ComposterEffects {
    /** The sounds TFC plays. */
    enum Sound {
        /** TFC {@code SoundEvents.HOE_TILL}: an addition. */
        ADD,
        /** TFC {@code SoundEvents.ROOTED_DIRT_BREAK}: an extraction. */
        EXTRACT
    }

    /** The action bar messages TFC shows ({@code displayClientMessage(..., true)}). */
    enum Message {
        TOO_MANY_GREENS("tfc.composter.too_many_greens"),
        TOO_MANY_BROWNS("tfc.composter.too_many_browns"),
        ROTTEN("tfc.composter.rotten");

        private final String key;

        Message(String key) {
            this.key = key;
        }

        /** TFC's lang key. */
        public String key() {
            return key;
        }
    }

    /** Sets the block state's {@code stage} and {@code type} to match {@code bin} (TFC {@code setState}), next tick. */
    void show(Block block, CompostBin bin);

    /** TFC {@code Helpers.playSound} at the block. */
    void sound(Block block, Sound sound);

    /** TFC {@code player.displayClientMessage(Component.translatable(key), true)}. */
    void message(Player player, Message message);

    /** TFC {@code Helpers.spawnItem(level, pos.above(), stack)} ({@code above}) or at the block centre (the removal drop). */
    void spawn(Block block, ItemStack stack, boolean above);

    /**
     * TFC {@code animateTick}'s compost particles of a ready or rotten bin. TFC draws them every client frame; here they are
     * shown once per interaction (the plugin's explicit-action particle rule).
     */
    void particles(Block block, CompostBin.Type type);

    /** Calls the device back (a neighbour update) in {@code ticks} server ticks: the bin may ripen then. */
    void ripenAt(Block block, long ticks);

    /**
     * How many of {@code stack} the hopper directly below the block takes now (TFC's {@code composterEnableAutomation}:
     * the output slot is extractable from the bottom face). 0 when there is no enabled hopper below or it is full. Read only.
     */
    int hopperRoom(Block block, ItemStack stack);

    /** Puts {@code stack} into the hopper below (next tick, on the region); what no longer fits is spawned on the hopper. */
    void toHopper(Block block, ItemStack stack);
}
