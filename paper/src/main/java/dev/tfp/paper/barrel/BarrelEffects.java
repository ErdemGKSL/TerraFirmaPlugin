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

    /**
     * PAR-PROC-35, TFC {@code BarrelBlockEntityRenderer}: the fluid surface shows {@code fluid} ({@link FluidAmount#EMPTY}
     * hides it: an empty, sealed or removed barrel) in a tank of {@code capacity} mB.
     */
    default void surface(Block block, FluidAmount fluid, int capacity) {
    }

    /**
     * PAR-PROC-38: TFC {@code setBlockAndUpdate(pos, state.setValue(RACK, ..).setValue(FACING, ..))}: the properties of
     * {@code wanted} are written over the barrel's current state by a later region task, with TFC's sound (racking: the
     * block's place sound, {@code Helpers.playPlaceSound}; unracking: {@code WOOD_PLACE} at pitch 0.85).
     */
    default void racked(Block block, BlockStateRef wanted, boolean racked) {
    }

    /**
     * PAR-PROC-38: the placement facing (TFC {@code getStateForPlacement}): the properties of {@code wanted} written over the
     * barrel's current state by a later region task, silently.
     */
    default void state(Block block, BlockStateRef wanted) {
    }

    /** PAR-PROC-38: TFC's {@code BARREL_DRIP} sound, once per settle that moved fluid through the tap. */
    default void drip(Block block) {
    }

    /**
     * PAR-PROC-38: TFC {@code updateShape}: a barrel on its side lost the sturdy face below and goes; {@code rack} is the rack
     * item TFC's {@code onRemove} spawns for a racked barrel (empty for none).
     */
    default void collapse(Block block, ItemStack rack) {
    }

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
