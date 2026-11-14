/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (FACING, RACK; updateShape and
 * getStateForPlacement: a sturdy CENTER top face below; useItemOn: the hit above 0.875 of the block)
 */
package dev.tfp.paper.barrel;

import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-38: what the barrel reads of the world around it for the rack, the side states and the tap. Region thread of the
 * barrel for every call.
 */
public interface BarrelRackWorld {
    /** TFC {@code FACING_NOT_DOWN} of the barrel at {@code block}: {@link BlockFace#UP} or a horizontal face. */
    BlockFace facing(Block block);

    /** TFC {@code RACK} of the barrel at {@code block}. */
    boolean racked(Block block);

    /**
     * TFC {@code isFaceSturdy(level, below, UP, SupportType.CENTER)} of the block below {@code block} (a sideways barrel
     * needs it, and so does every placement not on a rack).
     */
    boolean supported(Block block);

    /** Whether {@code stack} is the barrel rack's item. */
    boolean isRackItem(ItemStack stack);

    /** One barrel rack item (TFC {@code new ItemStack(TFCBlocks.BARREL_RACK)}), if the item exists. */
    Optional<ItemStack> rackItem();

    /**
     * The height of the player's hit on {@code block} above the block's bottom (TFC {@code hitResult.getLocation().y -
     * pos.getY()}), or {@link Double#NaN} when the player's look does not hit the block.
     */
    double hitHeight(Player player, Block block);

    /**
     * Settles the taps around {@code block} (its own, and those of barrels pouring into it) before an access reads its
     * record ({@link BarrelRackTap#settleAround}).
     */
    default void tap(Block block) {
    }

    /** Every barrel upright and supported, no rack item, no tap (tests, the rack absent). */
    BarrelRackWorld NONE = new BarrelRackWorld() {
        @Override
        public BlockFace facing(Block block) {
            return BlockFace.UP;
        }

        @Override
        public boolean racked(Block block) {
            return false;
        }

        @Override
        public boolean supported(Block block) {
            return true;
        }

        @Override
        public boolean isRackItem(ItemStack stack) {
            return false;
        }

        @Override
        public Optional<ItemStack> rackItem() {
            return Optional.empty();
        }

        @Override
        public double hitHeight(Player player, Block block) {
            return Double.NaN;
        }
    };
}
