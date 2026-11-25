/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SmallVesselInventoryContainer.java @ c3b4d9536 (addContainerSlots:
 *       slots at (71, 23), (89, 23), (71, 41), (89, 41)),
 *       src/main/java/net/dries007/tfc/client/screen/SmallVesselInventoryScreen.java @ c3b4d9536 (INVENTORY_2x2)
 */
package dev.tfp.core.gui;

import java.util.List;

/**
 * PAR-POT-10: where TFC's small vessel screen ({@code textures/gui/small_inventory.png}, 176 x 166) lands in a 3-row chest.
 * The datagen ({@code dev.tfp.datagen.vesselgui.VesselScreenArt}) draws the art from these numbers and the plugin's
 * {@code VesselScreens} uses the same ones.
 *
 * <pre>
 *  col 0   1   2   3   4   5   6   7   8
 *                 [0] [1]
 *                 [2] [3]
 * </pre>
 *
 * TFC's slots sit at x 71 + 18 col, y 23 + 18 row, half a slot off the chest grid, so the block moves by ({@link #DX_SLOTS},
 * {@link #DY_SLOTS}) onto chest columns 3 and 4 of rows 0 and 1. TFC's slot order (row by row) is kept.
 */
public final class VesselScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(3);
    public static final int TFC_SLOT_X = 71;
    public static final int TFC_SLOT_Y = 23;
    public static final int COLUMNS = 2;
    public static final int ROWS = 2;
    /** TFC's screen height; the player inventory starts at y 84 there and at {@code CHEST.playerRowY(0)} here. */
    public static final int TFC_HEIGHT = 166;
    public static final int DX_SLOTS = ChestGeometry.SLOT_X0 + 3 * ChestGeometry.SLOT - TFC_SLOT_X;
    public static final int DY_SLOTS = ChestGeometry.SLOT_Y0 - TFC_SLOT_Y;
    public static final int DY_PLAYER = CHEST.playerRowY(0) - 84;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/small_vessel", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of());

    /** Chest slot of each vessel slot, in TFC order. */
    public static final List<Integer> CHEST_SLOTS = chestSlots();

    private VesselScreenLayout() {
    }

    private static List<Integer> chestSlots() {
        Integer[] out = new Integer[COLUMNS * ROWS];
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                int slot = CHEST.slotAt(TFC_SLOT_X + DX_SLOTS + x * ChestGeometry.SLOT,
                    TFC_SLOT_Y + DY_SLOTS + y * ChestGeometry.SLOT);
                if (slot < 0) {
                    throw new IllegalStateException("vessel slot (" + x + ", " + y + ") is off the chest grid");
                }
                out[y * COLUMNS + x] = slot;
            }
        }
        return List.of(out);
    }

    public static int chestSlot(int vesselSlot) {
        return CHEST_SLOTS.get(vesselSlot);
    }
}
