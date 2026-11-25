/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536 (getSlotSelected,
 *       onRightClick, insertItem, isEmpty, SLOT_LARGE_ITEM),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (isFullContents,
 *       isSlotSupported)
 */
package dev.tfp.core.placed;

import dev.tfp.core.item.ContainerRules;
import java.util.Optional;

/**
 * TFC's placed item rules as pure functions. A placed item block has four slots, one per quadrant of the top face: slot
 * {@code (x ? 1 : 0) + (z ? 2 : 0)}, where {@code x} / {@code z} is TFC's {@code Math.round(c) < c} of the hit position
 * (true on the low half of the block: slot 0 is the +x +z quadrant, slot 3 the -x -z one). An item no larger than
 * {@code maxPlacedItemSize} takes the targeted slot when it is empty; a larger one, up to
 * {@code maxPlacedLargeItemSize}, takes slot 0 alone ({@link #SLOT_LARGE_ITEM}) when the block is empty and every quadrant
 * is supported. One item per slot ({@code stack.split(1)}); its rotation is the player's yaw rounded to 15 degrees.
 */
public final class PlacedRules {
    /** TFC {@code SLOT_LARGE_ITEM}. */
    public static final int SLOT_LARGE_ITEM = 0;
    /** The four quadrant slots. */
    public static final int SLOTS = 4;
    /** TFC: {@code Math.round(player.getYRot() / 15f) * 15f}. */
    public static final float ROTATION_STEP = 15f;

    private PlacedRules() {
    }

    /** TFC {@code getSlotSelected}: the slot of a hit at world coordinates {@code (x, z)}. */
    public static int slotSelected(double x, double z) {
        return (lowHalf(x) ? 1 : 0) + (lowHalf(z) ? 2 : 0);
    }

    /** TFC {@code Math.round(c) < c}: true when the hit lies in the lower half of its block along that axis. */
    static boolean lowHalf(double c) {
        return Math.round(c) < c;
    }

    /** TFC {@code Math.round(yaw / 15f) * 15f}. */
    public static float rotation(float yaw) {
        return Math.round(yaw / ROTATION_STEP) * ROTATION_STEP;
    }

    /** A successful insertion: the slot the one item goes into and the new state. */
    public record Insert(int slot, PlacedState state) {}

    /**
     * TFC {@code insertItem(player, stack, slot)}.
     *
     * @param state        the current state
     * @param occupied     which of the four slots hold an item
     * @param slot         the targeted slot ({@link #slotSelected})
     * @param size         the item's class against the size limits ({@link ContainerRules#placed})
     * @param fullContents TFC {@code isFullContents}: every quadrant of the block is supported
     * @param yaw          the player's yaw
     * @return the insertion, empty when TFC refuses it
     */
    public static Optional<Insert> insert(PlacedState state, boolean[] occupied, int slot, ContainerRules.PlacedSlot size,
            boolean fullContents, float yaw) {
        checkSlots(occupied);
        checkSlot(slot);
        if (size == ContainerRules.PlacedSlot.GROUP && !state.large()) {
            if (!occupied[slot]) {
                return Optional.of(new Insert(slot, state.withRotation(slot, rotation(yaw))));
            }
        } else if (size == ContainerRules.PlacedSlot.SINGLE && fullContents) {
            if (isEmpty(state, occupied)) {
                return Optional.of(new Insert(SLOT_LARGE_ITEM,
                    state.withLarge(true).withRotation(SLOT_LARGE_ITEM, rotation(yaw))));
            }
        }
        return Optional.empty();
    }

    /** A take-back: the slot whose item goes to the player and the new state ({@code large} is always cleared). */
    public record Take(int slot, PlacedState state) {}

    /**
     * TFC {@code onRightClick} with an empty main hand or while sneaking: the item of the targeted slot (slot 0 for a large
     * item) goes back to the player.
     *
     * @return the take, empty when that slot is empty
     */
    public static Optional<Take> take(PlacedState state, boolean[] occupied, int slot) {
        checkSlots(occupied);
        checkSlot(slot);
        int from = state.large() ? SLOT_LARGE_ITEM : slot;
        if (!occupied[from]) {
            return Optional.empty();
        }
        return Optional.of(new Take(from, state.withLarge(false)));
    }

    /** TFC {@code onRightClick}: an empty main hand or sneaking takes back, anything else inserts. */
    public static boolean takes(boolean mainHandEmpty, boolean sneaking) {
        return mainHandEmpty || sneaking;
    }

    /** TFC {@code isEmpty}: no item left (a large block whose slot 0 is empty counts as empty). */
    public static boolean isEmpty(PlacedState state, boolean[] occupied) {
        checkSlots(occupied);
        if (state.large() && !occupied[SLOT_LARGE_ITEM]) {
            return true;
        }
        for (boolean o : occupied) {
            if (o) {
                return false;
            }
        }
        return true;
    }

    /** TFC {@code isFullContents}: all four quadrants supported. */
    public static boolean isFullContents(boolean[] supported) {
        checkSlots(supported);
        for (boolean s : supported) {
            if (!s) {
                return false;
            }
        }
        return true;
    }

    /** The slots to draw: slot 0 alone for a large item, else every occupied slot. */
    public static boolean[] drawn(PlacedState state, boolean[] occupied) {
        checkSlots(occupied);
        boolean[] out = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            out[i] = occupied[i] && (!state.large() || i == SLOT_LARGE_ITEM);
        }
        return out;
    }

    private static void checkSlots(boolean[] slots) {
        if (slots == null || slots.length != SLOTS) {
            throw new IllegalArgumentException("four slots expected");
        }
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            throw new IllegalArgumentException("slot must be 0..3: " + slot);
        }
    }
}
