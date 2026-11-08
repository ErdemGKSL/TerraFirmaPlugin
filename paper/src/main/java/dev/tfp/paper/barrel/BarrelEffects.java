/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.integration.BlockStateRef;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * What the barrel does to the world around its record (PAR-PROC-08): the block state that shows the seal, sounds, and items
 * spawned at the block. Called from device callbacks on the block's region thread; an implementation queues block writes
 * for a later region task (a device handler never changes blocks itself).
 */
public interface BarrelEffects {
    /** The seal changed: the block state follows ({@code wanted}), with TFC's open or close sounds. */
    void sealed(Block block, BlockStateRef wanted, boolean sealed);

    /** A recipe's completion sound (TFC {@code getCompleteSound}). */
    void recipe(Block block, String sound);

    /** TFC {@code FluidHelpers.playTransferSound}: a fluid went into ({@code intoItem}) or out of a held container. */
    void transfer(Block block, FluidAmount fluid, boolean intoItem);

    /** TFC {@code Helpers.spawnItem} at the block. */
    void spawn(Block block, ItemStack stack);

    /** TFC {@code BarrelBlock.attack} with a wooden rod: the bass drum note of the fill level. */
    void drum(Block block, float pitch);

    /** TFC {@code ejectInventory}: an unsealed barrel holding fluid was broken (the splash). */
    void spilled(Block block, FluidAmount fluid);

    /** Effects that do nothing (tests). */
    BarrelEffects NONE = new BarrelEffects() {
        @Override
        public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
        }

        @Override
        public void recipe(Block block, String sound) {
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }
    };
}
