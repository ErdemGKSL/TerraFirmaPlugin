/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java @ c3b4d9536 (getSlotFromPos, useItemOn, getStateForPlacement, canSurvive)
 * src/main/java/net/dries007/tfc/common/blockentities/ToolRackBlockEntity.java @ c3b4d9536 (4 slots, one item each)
 */
package dev.tfp.core.rack;

import dev.tfp.core.chest.ChestSide;
import java.util.List;
import java.util.function.Predicate;

/**
 * PAR-DECO-05: the pure rules of the tool rack and the shelf: which of the four slots a click hit, what a click does, which side
 * the block is attached to and which facing a placement takes.
 */
public final class ToolRackRules {
    /** TFC {@code ToolRackBlockEntity}: {@code defaultInventory(4)}. */
    public static final int SLOTS = 4;
    /** TFC {@code getSlotStackLimit}. */
    public static final int SLOT_LIMIT = 1;

    private ToolRackRules() {
    }

    /**
     * TFC {@code getSlotFromPos}: the click position inside the block (0 to 1) as the slot {@code (horizontal > 0.5 ? 1 : 0) +
     * (y < 0.5 ? 2 : 0)}, where horizontal is x for a north or south facing and z for east or west.
     */
    public static int slot(ChestSide facing, double x, double y, double z) {
        boolean zAxis = facing == ChestSide.NORTH || facing == ChestSide.SOUTH;
        double horizontal = zAxis ? x : z;
        return (horizontal > 0.5 ? 1 : 0) + (y < 0.5 ? 2 : 0);
    }

    /** What a right click does to one slot. */
    public enum Click {
        /** Nothing is done (the held item is no tool and the slot is empty): the click goes on to the default interaction. */
        PASS,
        /** The slot's tool goes to the player, which put no tool in (an empty or no-tool hand). */
        TAKE,
        /** The tool in hand goes in the empty slot. */
        INSERT,
        /** The tool in hand goes in, the slot's tool goes to the player. */
        SWAP
    }

    /**
     * TFC {@code useItemOn}: {@code canInsert = isItemValid(slot, held)}; no insert and an empty slot passes; otherwise the slot's
     * tool is extracted, the held tool inserted (one item) and the extracted one handed back.
     */
    public static Click click(boolean slotEmpty, boolean heldIsTool) {
        if (!heldIsTool && slotEmpty) {
            return Click.PASS;
        }
        if (heldIsTool) {
            return slotEmpty ? Click.INSERT : Click.SWAP;
        }
        return Click.TAKE;
    }

    /** TFC {@code canSurvive}: the rack hangs on the block behind it, the one on the side opposite its facing. */
    public static ChestSide rackAttached(ChestSide facing) {
        return facing.opposite();
    }

    /** TFC {@code ShelfBlock.canSurvive}: the shelf needs the block on the side it faces ({@code pos.relative(facing)}). */
    public static ChestSide shelfAttached(ChestSide facing) {
        return facing;
    }

    /**
     * TFC {@code ToolRackBlock.getStateForPlacement}: for each horizontal looking direction, nearest first, the rack faces the
     * opposite of it and is placed when the block on that looking side is sturdy.
     *
     * @param looking the nearest-first horizontal directions of the placer ({@link LookingOrder})
     * @param sturdy  whether the block on a side of the new rack carries it
     * @return the facing, or null when no side carries it (nothing is placed)
     */
    public static ChestSide rackFacing(List<ChestSide> looking, Predicate<ChestSide> sturdy) {
        for (ChestSide direction : looking) {
            ChestSide facing = direction.opposite();
            if (sturdy.test(rackAttached(facing))) {
                return facing;
            }
        }
        return null;
    }

    /**
     * TFC {@code Helpers.getSupportedDirectionalStateForPlacement(shelf, context, true)}: the facing is the opposite of a
     * looking direction and the block on the shelf's {@code facing} side must be sturdy.
     */
    public static ChestSide shelfFacing(List<ChestSide> looking, Predicate<ChestSide> sturdy) {
        for (ChestSide direction : looking) {
            ChestSide facing = direction.opposite();
            if (sturdy.test(shelfAttached(facing))) {
                return facing;
            }
        }
        return null;
    }

    /**
     * TFC {@code getStateForPlacement}: a rack is not placed against the face of a rack that faces that face (placing on the
     * clicked face of a rack would stack two at one wall).
     *
     * @param clickedRackFacing the facing of the rack the player clicked, or null when the clicked block is none
     * @param clickedFace       the clicked face
     */
    public static boolean stacksOnRack(ChestSide clickedRackFacing, ChestSide clickedFace) {
        return clickedRackFacing != null && clickedRackFacing == clickedFace;
    }
}
