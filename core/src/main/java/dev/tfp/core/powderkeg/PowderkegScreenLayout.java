/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/PowderkegContainer.java @ c3b4d9536 (addContainerSlots: slot
 *       x * 3 + y at (25 + 18x, 19 + 18y); moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/button/PowderkegSealButton.java @ c3b4d9536 (the button at
 *       (123, 35), 20 x 20, sprite u 236, v 0 sealed / 20 unsealed),
 *       src/main/java/net/dries007/tfc/client/screen/PowderkegScreen.java @ c3b4d9536 (drawDisabled(0, SLOTS - 1) while
 *       sealed)
 */
package dev.tfp.core.powderkeg;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PAR-PROC-18: where TFC's powderkeg screen ({@code textures/gui/powderkeg.png}, 176 x 166) lands in a 3-row chest. The
 * datagen ({@code dev.tfp.datagen.powderkeg.PowderkegScreenArt}) draws the art from these numbers and the plugin's screen
 * uses the same ones.
 *
 * <pre>
 *  col 0   1   2   3   4   5   6   7   8
 *         [0] [3] [6] [9]
 *         [1] [4] [7] [10]        [seal]
 *         [2] [5] [8] [11]
 * </pre>
 *
 * TFC's slots sit at x 25 + 18 col, y 19 + 18 row, one pixel left of and below the chest grid, so the slot block moves
 * by ({@link #DX_SLOTS}, {@link #DY_SLOTS}); the seal button moves onto chest slot 16 like the barrel's. TFC numbers its
 * slots down the columns ({@code x * 3 + y}); that order is kept.
 */
public final class PowderkegScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(3);
    /** TFC {@code PowderkegContainer}: the first slot's item position and the column / row count. */
    public static final int TFC_SLOT_X = 25;
    public static final int TFC_SLOT_Y = 19;
    public static final int COLUMNS = 4;
    public static final int ROWS = 3;
    /** The slot block's move onto the chest grid. */
    public static final int DX_SLOTS = 1;
    public static final int DY_SLOTS = -1;
    /** TFC's seal button and its sprites. */
    public static final int TFC_BUTTON_X = 123;
    public static final int TFC_BUTTON_Y = 35;
    public static final int BUTTON_SIZE = 20;
    public static final int BUTTON_U = 236;
    public static final int BUTTON_V_SEALED = 0;
    public static final int BUTTON_V_UNSEALED = 20;
    /** The button's move: centred on chest slot 16 (item position 134, 36). */
    public static final int DX_BUTTON = 9;
    public static final int DY_BUTTON = -1;
    public static final int SEAL_SLOT = 16;
    /** TFC's screen height; the player inventory starts at y 84 there and at {@code CHEST.playerRowY(0)} here. */
    public static final int TFC_HEIGHT = 166;
    public static final int DY_PLAYER = CHEST.playerRowY(0) - 84;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/powderkeg", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage SEAL = new GuiImage("tfp:gui/powderkeg_seal", TFC_BUTTON_X + DX_BUTTON,
        TFC_BUTTON_Y + DY_BUTTON, BUTTON_SIZE, BUTTON_SIZE, 2, 1);
    /** TFC's {@code drawDisabled} highlight over the twelve slot interiors. */
    public static final GuiImage DISABLED = GuiImage.of("tfp:gui/powderkeg_disabled", TFC_SLOT_X + DX_SLOTS,
        TFC_SLOT_Y + DY_SLOTS, (COLUMNS - 1) * ChestGeometry.SLOT + 16, (ROWS - 1) * ChestGeometry.SLOT + 16);
    public static final String SEAL_LAYER = "seal";
    public static final String DISABLED_LAYER = "disabled";
    public static final int FRAME_SEALED = 0;
    public static final int FRAME_UNSEALED = 1;
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(
        new GuiLayers.Layer(DISABLED_LAYER, 5, DISABLED),
        new GuiLayers.Layer(SEAL_LAYER, 10, SEAL)));

    /** Chest slot of each TFC slot, in TFC order. */
    public static final List<Integer> CHEST_SLOTS = chestSlots();

    private PowderkegScreenLayout() {
    }

    private static List<Integer> chestSlots() {
        List<Integer> out = new ArrayList<>(PowderkegRules.SLOTS);
        for (int i = 0; i < PowderkegRules.SLOTS; i++) {
            out.add(-1);
        }
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                int slot = CHEST.slotAt(TFC_SLOT_X + DX_SLOTS + x * ChestGeometry.SLOT,
                    TFC_SLOT_Y + DY_SLOTS + y * ChestGeometry.SLOT);
                if (slot < 0) {
                    throw new IllegalStateException("powderkeg slot (" + x + ", " + y + ") is off the chest grid");
                }
                out.set(x * ROWS + y, slot);
            }
        }
        return List.copyOf(out);
    }

    public static int chestSlot(int tfcSlot) {
        return CHEST_SLOTS.get(tfcSlot);
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1. */
    public static int tfcSlot(int chestSlot) {
        return CHEST_SLOTS.indexOf(chestSlot);
    }

    /** The overlay frames: the seal button's state and the disabled highlight while sealed. */
    public static Map<String, Integer> frames(boolean sealed) {
        return Map.of(SEAL_LAYER, sealed ? FRAME_SEALED : FRAME_UNSEALED, DISABLED_LAYER, sealed ? 0 : -1);
    }
}
