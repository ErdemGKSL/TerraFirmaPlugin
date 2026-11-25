/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CharcoalForgeContainer.java @ c3b4d9536 (addContainerSlots, moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/CharcoalForgeScreen.java @ c3b4d9536 (renderBg: the temperature bar;
 *       the tooltip heatTooltipStyle.formatColored(temperature))
 */
package dev.tfp.paper.charcoalforge.gui;

import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.ProgressFrames;
import java.util.List;
import java.util.Map;

/**
 * PAR-METAL-09: where the charcoal forge's fourteen slots and its temperature bar sit in the 4-row chest that carries the
 * screen. TFC's slots are at (x, y) with y 16 + 18 k; the chest's rows are at 18 + 18 k, so the art moves {@link #DY} = 2
 * (see the datagen {@code CharcoalForgeScreenArt}, whose numbers equal these).
 *
 * <p>Chest slot of a TFC slot, {@code row * 9 + (x - 8) / 18} with {@code row = (y - 16) / 18}: fuel 0 to 4 at
 * (80,70) (98,52) (62,52) (116,34) (44,34), inputs 5 to 9 at (80,52) (98,34) (62,34) (116,16) (44,16), extras 10 to 13 at
 * (152, 16 + 18 i).
 */
public final class ForgeScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(4);
    /** The art's move onto the chest grid. */
    public static final int DY = 2;
    public static final int SLOTS = CharcoalForgeSim.SLOTS;
    /** The chest slot of each TFC slot, in TFC slot order. */
    public static final List<Integer> CHEST_SLOTS = List.of(31, 23, 21, 15, 11, 22, 14, 12, 6, 2, 8, 17, 26, 35);
    /** The chest slots over the temperature bar (column 0, all four rows): they carry TFC's tooltip. */
    public static final List<Integer> THERMOMETER_SLOTS = List.of(0, 9, 18, 27);

    public static final int TFC_THERMOMETER_X = 8;
    public static final int TFC_THERMOMETER_BOTTOM = 76;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/charcoal_forge", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/charcoal_forge_thermometer", TFC_THERMOMETER_X,
        TFC_THERMOMETER_BOTTOM - ProgressFrames.THERMOMETER_TRAVEL + DY, ProgressFrames.THERMOMETER_WIDTH,
        ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);
    public static final String THERMOMETER_LAYER = "thermometer";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND,
        List.of(new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER)));

    private ForgeScreenLayout() {
    }

    public static int chestSlot(int tfcSlot) {
        return CHEST_SLOTS.get(tfcSlot);
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1. */
    public static int tfcSlot(int chestSlot) {
        return CHEST_SLOTS.indexOf(chestSlot);
    }

    /** TFC {@code getSlotStackLimit}: every slot holds one item. */
    public static int limit(int tfcSlot) {
        return CharcoalForgeSim.SLOT_LIMIT;
    }

    public static Map<String, Integer> frames(float temperature) {
        return Map.of(THERMOMETER_LAYER, ProgressFrames.thermometer(temperature));
    }
}
