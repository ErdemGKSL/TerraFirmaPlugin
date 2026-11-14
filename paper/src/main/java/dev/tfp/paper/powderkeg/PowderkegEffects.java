/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.powderkeg.PowderkegState;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-PROC-18: what the powderkeg does to the world around it. The device handler decides; these apply, on the block's
 * region (state writes and the explosion run as their own region task after the callback stored the record).
 */
public interface PowderkegEffects {
    /** The sounds TFC plays. */
    enum Sound {
        /** TFC {@code setLit(true)}: {@code SoundEvents.TNT_PRIMED}. */
        PRIMED,
        /** TFC {@code setLit(false)}: {@code TFCSounds.ITEM_COOL}. */
        COOL,
        /** TFC {@code SoundEvents.FIRE_EXTINGUISH} (sneak click on a lit keg, unsealing a lit keg). */
        EXTINGUISH,
        /** TFC {@code Helpers.playPlaceSound}: the seal toggled. */
        PLACE
    }

    /** Sets the block state's {@code sealed} and {@code lit} to {@code state}'s, next tick. */
    void show(Block block, PowderkegState state);

    void sound(Block block, Sound sound);

    /** TFC {@code displayClientMessage("tfc.tooltip.powderkeg.disabled", true)}. */
    void disabled(Player player);

    /** Calls the keg back (its neighbour update) in {@code ticks} ticks: the fuse's end. */
    void fuse(Block block, long ticks);

    /**
     * TFC {@code serverTick} at the fuse's end: the keg becomes air (no drop of the keg) and a powderkeg explosion of
     * {@code strength} goes off at the block's corner, as TFC's {@code PowderKegExplosion}.
     */
    void explode(Block block, int strength);
}
