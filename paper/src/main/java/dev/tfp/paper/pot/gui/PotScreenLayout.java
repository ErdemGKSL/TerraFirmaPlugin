/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/PotContainer.java @ c3b4d9536 (addContainerSlots: fuel
 *       (8, 70 - 18i), the inputs (65, 23), (83, 23), (56, 41), (74, 41), (92, 41)),
 * file: src/main/java/net/dries007/tfc/client/screen/PotScreen.java @ c3b4d9536 (imageHeight + 20; the thermometer at
 *       (30, 76 - min(51, temp)); the boiling bubbles at (131, 10 + 20 - vHeight) and (144, ...), vHeight =
 *       ceil(boilingTicks % 35 / 35 * 20); the fluid tooltip over (121, 30, 41, 28); the label centred on x 118, y 80)
 */
package dev.tfp.paper.pot.gui;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.core.gui.SlotLayout;
import dev.tfp.core.pot.PotSim;
import java.util.List;
import java.util.Map;

/**
 * PAR-HEAT-09: where TFC's pot screen lands in a 4-row chest (catalogue {@code m3-heat-03-pot}). TFC's screen is 176 x 186
 * ({@code imageHeight + 20}), a 4-row chest; the art moves down {@link #DY} = 2 pixels so the fuel column (8, 70 - 18i)
 * sits on chest column 0, rows 3 to 0, as the firepit's.
 *
 * <pre>
 *  col 0    1       2   3    4    5    6   7      8
 *  [fuel3] (therm)      [in] [in]
 *  [fuel2] (therm)      [in] [in] [in]     (fluid)
 *  [fuel1] (therm)                         (status)
 *  [fuel0] (therm)
 * </pre>
 *
 * TFC's five inputs, two over three, are off the chest grid once moved: each goes to the nearest chest column (x 65 and
 * 83 to columns 3 and 4 of row 0; x 56, 74 and 92 to columns 3, 4 and 5 of row 1). The thermometer column (x 30) is
 * decoration: its four slots are buttons whose tooltip is the temperature. TFC's fluid tooltip (over the pot drawing,
 * x 121 to 162) is the button {@link #FLUID_SLOT}; TFC's label under it ("Boiling!", the output, "Finished") the button
 * {@link #STATUS_SLOT}. The background, thermometer and bubble art ({@link #BACKGROUND}, {@link #THERMOMETER},
 * {@link #BOILING}) are written by datagen in follow-up PAR-HEAT-25.
 */
public final class PotScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(4);
    /** TFC's screen height: {@code AbstractContainerScreen} 166 + 20. */
    public static final int TFC_HEIGHT = 186;
    /** TFC {@code PotContainer.addContainerSlots}, in block entity slot order (fuel 0..3, inputs 4..8). */
    public static final List<SlotLayout.Point> TFC_SLOTS = List.of(
        new SlotLayout.Point(8, 70), new SlotLayout.Point(8, 52), new SlotLayout.Point(8, 34), new SlotLayout.Point(8, 16),
        new SlotLayout.Point(65, 23), new SlotLayout.Point(83, 23), new SlotLayout.Point(56, 41),
        new SlotLayout.Point(74, 41), new SlotLayout.Point(92, 41));
    /** The art's vertical move: fuel slot 3 (y 16) onto chest row 0. */
    public static final int DY = SlotLayout.dyFor(TFC_SLOTS.get(FirepitSim.SLOT_FUEL_INPUT), 0);
    /** Chest slot of each TFC slot, in TFC order. */
    public static final List<Integer> CHEST_SLOTS = List.of(27, 18, 9, 0, 3, 4, 12, 13, 14);
    /** The thermometer column (chest column 1): buttons showing the temperature. */
    public static final List<Integer> THERMOMETER_SLOTS = List.of(1, 10, 19, 28);
    /** TFC's fluid tooltip over the pot drawing: the tank in mB. */
    public static final int FLUID_SLOT = 16;
    /** TFC's label at (118, 80): boiling, the output, or finished. */
    public static final int STATUS_SLOT = 25;

    /** TFC's thermometer sprite: x 30, its travel from {@code 76 - 51} down to {@code 76 + 5}. */
    public static final int TFC_THERMOMETER_X = 30;
    public static final int TFC_THERMOMETER_BOTTOM = 76;
    /** TFC's two bubble columns: x 131 and 144, 11 wide, the 20 rows above y 30. */
    public static final int TFC_BOILING_X = 131;
    public static final int TFC_BOILING_Y = 10;
    public static final int BOILING_WIDTH = 24;
    public static final int BOILING_HEIGHT = 20;
    /** TFC's bubble period ({@code boilingTicks % 35}). */
    public static final int BOILING_PERIOD = 35;
    /** Frames of the bubble strip: none, then 1 to 20 rows. */
    public static final int BOILING_FRAMES = BOILING_HEIGHT + 1;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/pot", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    /** The 52-frame thermometer strip ({@link ProgressFrames#thermometer}) moved by {@link #DY}. */
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/pot_thermometer", TFC_THERMOMETER_X,
        TFC_THERMOMETER_BOTTOM - ProgressFrames.THERMOMETER_TRAVEL + DY, ProgressFrames.THERMOMETER_WIDTH,
        ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);
    /** The 21-frame bubble strip moved by {@link #DY}. */
    public static final GuiImage BOILING = new GuiImage("tfp:gui/pot_boiling", TFC_BOILING_X, TFC_BOILING_Y + DY,
        BOILING_WIDTH, BOILING_HEIGHT, 3, 7);
    public static final String THERMOMETER_LAYER = "thermometer";
    public static final String BOILING_LAYER = "boiling";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(
        new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER), new GuiLayers.Layer(BOILING_LAYER, 20, BOILING)));

    private PotScreenLayout() {
    }

    /** The chest slot of TFC slot {@code tfcSlot}. */
    public static int chestSlot(int tfcSlot) {
        return CHEST_SLOTS.get(tfcSlot);
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1. */
    public static int tfcSlot(int chestSlot) {
        return CHEST_SLOTS.indexOf(chestSlot);
    }

    /** TFC {@code getSlotStackLimit}: every pot slot holds one item. */
    public static int limit(int tfcSlot) {
        return PotSim.slotLimit(tfcSlot);
    }

    /** Whether TFC slot {@code tfcSlot} is one of the five ingredients. */
    public static boolean isInput(int tfcSlot) {
        return tfcSlot >= PotSim.SLOT_INPUT_START && tfcSlot <= PotSim.SLOT_INPUT_END;
    }

    /** TFC's bubble rows: {@code ceil(boilingTicks % 35 / 35f * 20f)} while boiling, none otherwise. */
    public static int boilingFrame(int boilingTicks) {
        if (boilingTicks <= 0) {
            return 0;
        }
        int ticks = boilingTicks % BOILING_PERIOD;
        return (int) Math.ceil(ticks / (float) BOILING_PERIOD * BOILING_HEIGHT);
    }

    /** The frames of a pot at {@code temperature} with {@code boilingTicks}. */
    public static Map<String, Integer> frames(float temperature, int boilingTicks) {
        return Map.of(THERMOMETER_LAYER, ProgressFrames.thermometer(temperature), BOILING_LAYER, boilingFrame(boilingTicks));
    }
}
