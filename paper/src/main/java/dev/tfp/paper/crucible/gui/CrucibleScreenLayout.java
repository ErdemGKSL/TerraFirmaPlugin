/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CrucibleContainer.java @ c3b4d9536 (addContainerSlots),
 *       src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java @ c3b4d9536 (renderBg: the thermometer and the
 *       alloy fill)
 */
package dev.tfp.paper.crucible.gui;

import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.ProgressFrames;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PAR-METAL-12: where TFC's crucible screen lands in a 6-row chest. TFC's screen is 176 x 221, a 6-row chest is 222; the
 * art under the text panel moves up {@link #DY} = -10 so the inputs (x 26 + 18 column, y 82 + 18 line) sit on chest
 * columns 1 to 3, rows 3 to 5, and the output (152, 100) on column 8, row 4. The numbers equal the datagen's
 * ({@code CrucibleScreenArt}); {@code CrucibleScreenArtTest} checks them.
 *
 * <pre>
 *  rows 0-2: the text panel: info buttons (tooltip: the alloy and its metals, TFC's text lines)
 *  row 3:  (therm) [in0] [in1] [in2]        (fluid) (fluid)       .
 *  row 4:  (therm) [in3] [in4] [in5]        (fluid) (fluid)     [out]
 *  row 5:  (therm) [in6] [in7] [in8]
 * </pre>
 *
 * The thermometer column (TFC x 7) is three buttons whose tooltip is the temperature; the fluid area (TFC x 97 to 133)
 * four buttons whose tooltip is the amount. TFC's scroll bar and its text lines are replaced by the info tooltip.
 */
public final class CrucibleScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(6);
    /** The art's move under the text panel. */
    public static final int DY = -10;
    /** TFC {@code CrucibleBlockEntity.SLOTS}: nine inputs and the output. */
    public static final int SLOTS = CrucibleRules.INPUT_SLOTS + 1;
    public static final int SLOT_OUTPUT = CrucibleRules.INPUT_SLOTS;
    /** Chest slot of each TFC slot: inputs {@code (3 + slot / 3) * 9 + 1 + slot % 3}, the output 44. */
    public static final List<Integer> CHEST_SLOTS = chestSlots();
    public static final List<Integer> THERMOMETER_SLOTS = List.of(27, 36, 45);
    public static final List<Integer> FLUID_SLOTS = List.of(32, 33, 41, 42);
    /** The text panel, rows 0 to 2. */
    public static final List<Integer> INFO_SLOTS = infoSlots();

    public static final int TFC_THERMOMETER_X = 7;
    public static final int TFC_THERMOMETER_BOTTOM = 131;
    public static final int TFC_FLUID_X = 97;
    public static final int TFC_FLUID_BOTTOM = 124;
    public static final int FLUID_WIDTH = 36;
    public static final int FLUID_HEIGHT = 31;
    public static final int FLUID_FRAMES = FLUID_HEIGHT + 1;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/crucible", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/crucible_thermometer", TFC_THERMOMETER_X,
        TFC_THERMOMETER_BOTTOM - ProgressFrames.THERMOMETER_TRAVEL + DY, ProgressFrames.THERMOMETER_WIDTH,
        ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);
    public static final GuiImage FLUID = new GuiImage("tfp:gui/crucible_fluid", TFC_FLUID_X,
        TFC_FLUID_BOTTOM - FLUID_HEIGHT + DY, FLUID_WIDTH, FLUID_HEIGHT, 4, 8);
    public static final String THERMOMETER_LAYER = "thermometer";
    public static final String FLUID_LAYER = "fluid";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(
        new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER), new GuiLayers.Layer(FLUID_LAYER, 5, FLUID)));

    private CrucibleScreenLayout() {
    }

    private static List<Integer> chestSlots() {
        List<Integer> out = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < CrucibleRules.INPUT_SLOTS; slot++) {
            out.add((3 + slot / 3) * 9 + 1 + slot % 3);
        }
        out.add(4 * 9 + 8);
        return List.copyOf(out);
    }

    private static List<Integer> infoSlots() {
        List<Integer> out = new ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            out.add(i);
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

    public static boolean isInput(int tfcSlot) {
        return tfcSlot >= 0 && tfcSlot < CrucibleRules.INPUT_SLOTS;
    }

    /** TFC {@code getSlotStackLimit}: every slot holds one item. */
    public static int limit(int tfcSlot) {
        return 1;
    }

    /** TFC's fill: {@code ceil(31 x amount / capacity)} pixels, none for an empty crucible. */
    public static int fluidFrame(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        return Math.min(FLUID_HEIGHT, (int) Math.ceil((float) FLUID_HEIGHT * amount / capacity));
    }

    public static Map<String, Integer> frames(float temperature, int amount, int capacity) {
        return Map.of(THERMOMETER_LAYER, ProgressFrames.thermometer(temperature), FLUID_LAYER, fluidFrame(amount, capacity));
    }
}
