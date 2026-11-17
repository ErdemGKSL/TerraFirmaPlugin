/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/FireboxContainer.java @ c3b4d9536 (addContainerSlots: slot i at
 *       (62 + 18 (i % 4), 19 + 18 (i / 4)); init(playerInventory, 36)),
 *       src/main/java/net/dries007/tfc/client/screen/FireboxScreen.java @ c3b4d9536 (imageHeight + 36; the thermometer
 *       sprite at (7, 16), 17 x 74; the indicator at (8, 76 - temperature); the tooltip over (8, 25, 15, 51); the status
 *       line at (20, 95))
 */
package dev.tfp.core.firebox;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.ProgressFrames;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PAR-HEAT-12: where TFC's firebox screen ({@code textures/gui/firebox.png}, 176 x 202) lands in a 5-row chest (176 x
 * 204). The datagen ({@code dev.tfp.datagen.firebox.FireboxScreenArt}) draws the art from these numbers and the plugin's
 * screen uses the same ones.
 *
 * <pre>
 *  col 0       1   2   3   4   5   6   7   8
 *  (therm)            [0] [1] [2] [3]
 *  (therm)            [4] [5] [6] [7]
 *  (therm)            [8] [9] [10][11]
 *  (therm)            [12][13][14][15]
 *  (status)
 * </pre>
 *
 * TFC's slots sit at x 62 + 18 col (chest columns 3 to 6) and y 19 + 18 row, one pixel below the chest grid, so the
 * content moves up by {@link #DY} = -1 and the player inventory down by {@link #DY_PLAYER} = 1. The thermometer column
 * (chest column 0, rows 0 to 3) and the row under the slots (TFC's status line) are buttons whose tooltip is the
 * temperature and the kiln status.
 */
public final class FireboxScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(5);
    /** TFC's screen height: {@code AbstractContainerScreen} 166 + 36. */
    public static final int TFC_HEIGHT = 202;
    /** TFC {@code init(playerInventory, 36)}: the player inventory moves 36 down from y 84. */
    public static final int TFC_INVENTORY_OFFSET = 36;
    /** TFC {@code FireboxContainer}: the first slot's item position and the grid. */
    public static final int TFC_SLOT_X = 62;
    public static final int TFC_SLOT_Y = 19;
    public static final int COLUMNS = 4;
    public static final int ROWS = 4;
    /** The content's vertical move onto the chest grid. */
    public static final int DY = ChestGeometry.SLOT_Y0 - TFC_SLOT_Y;
    /** The player inventory's move: TFC's y 120 onto the 5-row chest's {@code playerRowY(0)}. */
    public static final int DY_PLAYER = CHEST.playerRowY(0) - (84 + TFC_INVENTORY_OFFSET);
    /** TFC's thermometer sprite ({@code container/thermometer}, 17 x 74) and indicator travel. */
    public static final int TFC_THERMOMETER_X = 7;
    public static final int TFC_THERMOMETER_Y = 16;
    public static final int THERMOMETER_WIDTH = 17;
    public static final int THERMOMETER_HEIGHT = 74;
    public static final int TFC_INDICATOR_X = 8;
    public static final int TFC_INDICATOR_BOTTOM = 76;
    /** TFC's status line position. */
    public static final int TFC_STATUS_X = 20;
    public static final int TFC_STATUS_Y = 95;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/firebox", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    /** The 52-frame indicator strip ({@link ProgressFrames#thermometer}) moved by {@link #DY}. */
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/firebox_thermometer", TFC_INDICATOR_X,
        TFC_INDICATOR_BOTTOM - ProgressFrames.THERMOMETER_TRAVEL + DY, ProgressFrames.THERMOMETER_WIDTH,
        ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);
    public static final String THERMOMETER_LAYER = "thermometer";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND,
        List.of(new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER)));

    /** Chest slot of each TFC slot, in TFC order. */
    public static final List<Integer> CHEST_SLOTS = chestSlots();
    /** The thermometer column (chest column 0, rows 0 to 3). */
    public static final List<Integer> THERMOMETER_SLOTS = List.of(0, 9, 18, 27);
    /** The status row (chest row 4): TFC draws the kiln status there. */
    public static final List<Integer> STATUS_SLOTS = List.of(36, 37, 38, 39, 40, 41, 42, 43, 44);

    private FireboxScreenLayout() {
    }

    private static List<Integer> chestSlots() {
        List<Integer> out = new ArrayList<>(FireboxSim.SLOTS);
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            int slot = CHEST.slotAt(TFC_SLOT_X + ChestGeometry.SLOT * (i % COLUMNS),
                TFC_SLOT_Y + DY + ChestGeometry.SLOT * (i / COLUMNS));
            if (slot < 0) {
                throw new IllegalStateException("firebox slot " + i + " is off the chest grid");
            }
            out.add(slot);
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

    /** The thermometer frame for {@code temperature}: TFC draws nothing at 0 and the indicator k pixels up otherwise. */
    public static Map<String, Integer> frames(float temperature) {
        return Map.of(THERMOMETER_LAYER, ProgressFrames.thermometer(temperature));
    }

    /** What TFC's status line says. */
    public enum Status {
        /** Cold: TFC draws nothing. */
        NONE,
        /** {@code tfc.tooltip.firebox.no_heat}. */
        NO_HEAT,
        /** {@code tfc.tooltip.firebox.time_to_heat}: heating {@code count} blocks in {@code left}. */
        TIME_TO_HEAT,
        /** {@code tfc.tooltip.firebox.heated}. */
        HEATED
    }

    /** TFC {@code FireboxScreen.renderBg}'s choice of status line. */
    public static Status status(FireboxData data, long now) {
        if (ProgressFrames.thermometer(data.temperature()) <= 0) {
            return Status.NONE;
        }
        long left = FireboxSim.timeLeft(data, now);
        if (left == -1 || data.heatingCount() == 0) {
            return Status.NO_HEAT;
        }
        return left > 0 ? Status.TIME_TO_HEAT : Status.HEATED;
    }

    /**
     * TFC {@code cascadeFuelSlots}: the non-empty slots move down to the lowest open slots, in order. Returns a new list.
     *
     * @param <T>   the slot content
     * @param empty whether a slot is empty
     * @param none  the empty value
     */
    public static <T> List<T> cascade(List<T> slots, java.util.function.Predicate<T> empty, T none) {
        List<T> out = new ArrayList<>(slots);
        int lowest = 0;
        for (int i = 0; i < out.size(); i++) {
            T s = out.get(i);
            if (!empty.test(s)) {
                if (i > lowest) {
                    out.set(lowest, s);
                    out.set(i, none);
                }
                lowest++;
            }
        }
        return out;
    }
}
