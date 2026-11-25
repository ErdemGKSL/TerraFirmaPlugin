/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/SewingTableScreen.java @ c3b4d9536 (8 x 4 cloth squares and 9 x 5
 *       stitch points, the four material buttons),
 *       src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java @ c3b4d9536 (slot order: yarn, tool, two
 *       inputs, result)
 */
package dev.tfp.core.sewing;

import dev.tfp.core.craft.SewingRecipe;
import dev.tfp.core.gui.ChestGeometry;

/**
 * PAR-PROC-07: the sewing table in a 6-row chest. TFC draws one free canvas of 8 x 4 cloth squares and 9 x 5 stitch
 * points on top of each other; a vanilla client only clicks chest slots, so the canvas is two views of the same grid of
 * slots. The squares view puts square {@code (x, y)} on chest slot {@code 9y + x} (columns 0 to 7, rows 0 to 3); the
 * stitches view puts stitch {@code (x, y)} on chest slot {@code 9y + x} (columns 0 to 8, rows 0 to 4). The last row holds
 * the five item slots and the four material buttons.
 */
public final class SewingScreen {
    public static final ChestGeometry CHEST = new ChestGeometry(6);
    public static final int SQUARE_COLUMNS = 8;
    public static final int SQUARE_ROWS = 4;
    public static final int STITCH_COLUMNS = 9;
    public static final int STITCH_ROWS = 5;

    /** TFC {@code SewingTableContainer.SLOT_*}, on the last chest row. */
    public static final int SLOT_YARN = 45;
    public static final int SLOT_TOOL = 46;
    public static final int SLOT_INPUT_1 = 47;
    public static final int SLOT_INPUT_2 = 48;
    public static final int SLOT_RESULT = 49;

    /** The material buttons (TFC {@code BURLAP_ID}, {@code WOOL_ID}, {@code REMOVE_ID}, {@code NEEDLE_ID}). */
    public static final int BUTTON_BURLAP = 50;
    public static final int BUTTON_WOOL = 51;
    public static final int BUTTON_REMOVE = 52;
    public static final int BUTTON_NEEDLE = 53;

    private SewingScreen() {}

    /** The square under chest slot {@code slot} in the squares view, or -1 for any other slot. */
    public static int squareAt(int slot) {
        if (slot < 0 || slot >= STITCH_COLUMNS * SQUARE_ROWS) return -1;
        final int x = slot % STITCH_COLUMNS;
        return x >= SQUARE_COLUMNS ? -1 : (slot / STITCH_COLUMNS) * SQUARE_COLUMNS + x;
    }

    /** The stitch point under chest slot {@code slot} in the stitches view, or -1 for any other slot. */
    public static int stitchAt(int slot) {
        return slot >= 0 && slot < SewingRecipe.STITCHES ? slot : -1;
    }

    /** The chest slot of a square in the squares view. */
    public static int squareSlot(int square) {
        if (square < 0 || square >= SewingRecipe.SQUARES) throw new IllegalArgumentException("square out of range: " + square);
        return (square / SQUARE_COLUMNS) * STITCH_COLUMNS + square % SQUARE_COLUMNS;
    }

    /** The chest slot of a stitch point in the stitches view. */
    public static int stitchSlot(int stitch) {
        if (stitch < 0 || stitch >= SewingRecipe.STITCHES) throw new IllegalArgumentException("stitch out of range: " + stitch);
        return stitch;
    }

    /** True for the four item slots a player may put items into. */
    public static boolean isInput(int slot) {
        return slot >= SLOT_YARN && slot <= SLOT_INPUT_2;
    }
}
