/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/BookshelfBlock.java @ c3b4d9536 (extends vanilla ChiseledBookShelfBlock, getEnchantPower)
 * vanilla net.minecraft.world.level.block.ChiseledBookShelfBlock (getHitSlot, useItemOn, useWithoutItem)
 */
package dev.tfp.core.rack;

import dev.tfp.core.chest.ChestSide;
import java.util.OptionalInt;

/**
 * PAR-DECO-05: the pure rules of TFC's bookshelf, which is vanilla's chiseled bookshelf: six slots (three above three), books
 * go in and out on the front face only.
 */
public final class BookshelfRules {
    public static final int SLOTS = 6;
    /** TFC {@code BookshelfBlock.getEnchantPower}: each occupied slot adds half a point. */
    public static final double POWER_PER_BOOK = 0.5;

    private BookshelfRules() {
    }

    /**
     * Vanilla {@code getHitSlot}: empty unless the clicked face is the shelf's facing; else the slot of the hit point,
     * {@code column + 3 * (y >= 0.5 ? 0 : 1)}, the columns split at 0.375 and 0.6875 of the face seen from the front.
     *
     * @param x the hit inside the block, 0 to 1
     */
    public static OptionalInt slot(ChestSide facing, ChestSide clickedFace, double x, double y, double z) {
        if (clickedFace != facing) {
            return OptionalInt.empty();
        }
        double across = switch (facing) {
            case NORTH -> 1.0 - x;
            case SOUTH -> x;
            case WEST -> z;
            case EAST -> 1.0 - z;
        };
        int row = y >= 0.5 ? 0 : 1;
        return OptionalInt.of(section(across) + row * 3);
    }

    private static int section(double f) {
        if (f < 0.375) {
            return 0;
        }
        return f < 0.6875 ? 1 : 2;
    }

    /** What a right click on a slot does. */
    public enum Click {
        /** The click goes on to the default interaction. */
        PASS,
        /** The held book goes into the empty slot. */
        ADD,
        /** The slot's book goes to the player. */
        REMOVE,
        /** The click is handled and nothing changes (an empty slot, no book in hand). */
        CONSUME
    }

    /**
     * Vanilla {@code useItemOn} then {@code useWithoutItem}: a book (the {@code bookshelf_books} tag) in hand goes into an
     * empty slot; into an occupied one it falls through to the empty-hand rule, which takes the book out.
     *
     * @param slotHit      whether the click hit a slot (the front face)
     * @param heldIsBook   whether the held item is a book of the tag
     * @param slotOccupied whether the hit slot holds a book
     */
    public static Click click(boolean slotHit, boolean heldIsBook, boolean slotOccupied) {
        if (!slotHit) {
            return heldIsBook ? Click.CONSUME : Click.PASS;
        }
        if (heldIsBook && !slotOccupied) {
            return Click.ADD;
        }
        return slotOccupied ? Click.REMOVE : Click.CONSUME;
    }

    /** TFC {@code getEnchantPower}: half a point per occupied slot. */
    public static double enchantPower(int occupied) {
        return occupied * POWER_PER_BOOK;
    }
}
