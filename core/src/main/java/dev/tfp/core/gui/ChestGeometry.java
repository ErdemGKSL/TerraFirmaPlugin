/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

/**
 * PAR-INFRA-03 {@code fw-alignment}: the vanilla client's layout of a 9 x N chest screen ({@code ContainerScreen} with
 * {@code ChestMenu}), in GUI pixels relative to the window's top-left corner ({@code leftPos, topPos}). Every position is
 * independent of the GUI scale: the client multiplies GUI pixels by the scale (1 to 4), so a background that lines up at
 * scale 1 lines up at every scale. These numbers are the base of every textured chest GUI; the art and the title glyphs
 * are placed with them.
 *
 * <p>Slot positions are item positions (the 16 x 16 interior where the item is drawn); the slot frame starts one pixel up
 * and left of it ({@link #FRAME_INSET}) and is 18 x 18.
 *
 * @param rows container rows, 1 to 6
 */
public record ChestGeometry(int rows) {
    public static final int COLUMNS = 9;
    public static final int WIDTH = 176;
    /** {@code AbstractContainerScreen.titleLabelX}: where the pen starts for the title text. */
    public static final int TITLE_X = 8;
    /** {@code AbstractContainerScreen.titleLabelY}. */
    public static final int TITLE_Y = 6;
    public static final int SLOT = 18;
    public static final int FRAME_INSET = 1;
    /** x of the first container and player slot. */
    public static final int SLOT_X0 = 8;
    /** y of the first container slot row. */
    public static final int SLOT_Y0 = 18;

    public ChestGeometry {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("a chest has 1 to 6 rows, not " + rows);
        }
    }

    /** {@code imageHeight = 114 + rows * 18} ({@code ContainerScreen}). */
    public int height() {
        return 114 + rows * SLOT;
    }

    /** Container slots, {@code 9 * rows}. */
    public int size() {
        return COLUMNS * rows;
    }

    /** {@code inventoryLabelY = imageHeight - 94}. */
    public int inventoryLabelY() {
        return height() - 94;
    }

    public int slotX(int index) {
        checkIndex(index);
        return SLOT_X0 + SLOT * (index % COLUMNS);
    }

    public int slotY(int index) {
        checkIndex(index);
        return SLOT_Y0 + SLOT * (index / COLUMNS);
    }

    /** The container slot whose item position is {@code (x, y)}, or -1 when none is. */
    public int slotAt(int x, int y) {
        int dx = x - SLOT_X0;
        int dy = y - SLOT_Y0;
        if (dx < 0 || dy < 0 || dx % SLOT != 0 || dy % SLOT != 0) {
            return -1;
        }
        int c = dx / SLOT;
        int r = dy / SLOT;
        return c < COLUMNS && r < rows ? r * COLUMNS + c : -1;
    }

    /** y of player inventory row {@code row} (0 to 2): {@code 31 + 18 * rows + 18 * row} ({@code ChestMenu}). */
    public int playerRowY(int row) {
        if (row < 0 || row > 2) {
            throw new IllegalArgumentException("player inventory rows are 0 to 2, not " + row);
        }
        return 31 + SLOT * rows + SLOT * row;
    }

    /** y of the hotbar: {@code 89 + 18 * rows}. */
    public int hotbarY() {
        return 89 + SLOT * rows;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("slot " + index + " of a " + rows + "-row chest");
        }
    }
}
