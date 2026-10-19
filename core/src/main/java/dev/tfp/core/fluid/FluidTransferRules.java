/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferBetweenWorldAndItem,
 *       transferBetweenBlockEntityAndItem, transferBetweenBlockHandlerAndItem: the order of the attempts and the direction)
 */
package dev.tfp.core.fluid;

/**
 * The decision table of a container item used on a block, as TFC's {@code FluidHelpers} orders it. The world and the block
 * handler do the actual move; these rules only pick what to try first.
 *
 * <p>On a block position the item tries, in order:
 * <ol>
 *   <li>the world: a pickup when the item is empty, or a placement when it holds fluid and the placement is allowed;</li>
 *   <li>the block entity's tank, if the world attempt did not move anything. Its direction is {@link #blockTransfer}.</li>
 * </ol>
 */
public final class FluidTransferRules {

    /** The first attempt on the world at a position. */
    public enum WorldAttempt {
        /** Take a fluid block into the empty item. */
        PICKUP,
        /** Place the item's fluid into the world. */
        PLACE,
        /** No world attempt. The block entity is tried next. */
        NONE
    }

    /** The direction of a move between an item and a block's tank. */
    public enum BlockTransfer {
        /** The block's tank gives to the item. */
        FILL_ITEM,
        /** The item gives to the block's tank. */
        DRAIN_ITEM
    }

    private FluidTransferRules() {
    }

    /**
     * The first world attempt.
     *
     * @param itemEmpty                   whether the item holds no fluid
     * @param allowPlacingAnyLiquidBlocks whether a non-empty item may place fluid into the world
     * @return {@link WorldAttempt#PICKUP} when empty, {@link WorldAttempt#PLACE} when it holds fluid and placing is allowed,
     *         otherwise {@link WorldAttempt#NONE}
     */
    public static WorldAttempt worldAttempt(boolean itemEmpty, boolean allowPlacingAnyLiquidBlocks) {
        if (itemEmpty) {
            return WorldAttempt.PICKUP;
        }
        return allowPlacingAnyLiquidBlocks ? WorldAttempt.PLACE : WorldAttempt.NONE;
    }

    /**
     * The direction of a move with a block's tank.
     *
     * <p>TFC fills the item when the item is empty, or when the block accepts none of the item's fluid. Otherwise the item
     * drains into the block.
     *
     * @param itemEmpty          whether the item holds no fluid
     * @param blockFillSimulated how much the block's tank would accept of the item's fluid, {@code >= 0}
     * @return the direction
     */
    public static BlockTransfer blockTransfer(boolean itemEmpty, int blockFillSimulated) {
        if (blockFillSimulated < 0) {
            throw new IllegalArgumentException("blockFillSimulated must be >= 0: " + blockFillSimulated);
        }
        return itemEmpty || blockFillSimulated == 0 ? BlockTransfer.FILL_ITEM : BlockTransfer.DRAIN_ITEM;
    }
}
