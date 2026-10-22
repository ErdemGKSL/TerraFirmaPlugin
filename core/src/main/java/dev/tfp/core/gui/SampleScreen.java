/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/LargeVesselContainer.java @ c3b4d9536 (addContainerSlots: the
 *       nine slot positions; moveStack: shift-click order),
 *       src/main/java/net/dries007/tfc/client/screen/button/VesselSealButton.java @ c3b4d9536 (button rectangle, the
 *       sealed/unsealed sprites at u 236, v 0 / 20),
 *       src/main/java/net/dries007/tfc/client/screen/LargeVesselScreen.java @ c3b4d9536 (button at guiLeft + 9, guiTop)
 */
package dev.tfp.core.gui;

import java.util.List;
import java.util.Map;

/**
 * PAR-INFRA-03: the sample screen behind {@code /tfp debug gui sample}, a 9-slot storage that proves the alignment. It is
 * TFC's large vessel screen ({@code textures/gui/large_vessel.png}, 176 x 166) in a 3-row chest (176 x 168): the art moves
 * up one pixel ({@link #DY}) so TFC's slots at y 19, 37, 55 sit on the chest rows at 18, 36, 54, and the player inventory
 * part moves down to the chest's (datagen {@code ChestRelayout}). The seal button is a button slot under TFC's 20 x 20
 * button sprite, which is a two-frame overlay.
 *
 * <p>The large vessel itself (sealing a block, pests, drops) is PAR-POT items; this class only holds the layout.
 */
public final class SampleScreen {
    public static final ChestGeometry CHEST = new ChestGeometry(3);
    /** TFC's screen size ({@code AbstractContainerScreen} defaults). */
    public static final int TFC_WIDTH = 176;
    public static final int TFC_HEIGHT = 166;
    /** TFC {@code LargeVesselContainer.addContainerSlots}, in slot order. */
    public static final List<SlotLayout.Point> TFC_SLOTS = List.of(
        new SlotLayout.Point(62, 19), new SlotLayout.Point(80, 19), new SlotLayout.Point(98, 19),
        new SlotLayout.Point(62, 37), new SlotLayout.Point(80, 37), new SlotLayout.Point(98, 37),
        new SlotLayout.Point(62, 55), new SlotLayout.Point(80, 55), new SlotLayout.Point(98, 55));
    public static final int DY = SlotLayout.dyFor(TFC_SLOTS.getFirst(), 0);
    public static final SlotLayout SLOTS = SlotLayout.of(CHEST, DY, TFC_SLOTS);

    /** The seal button: {@code LargeVesselScreen} puts it at {@code guiLeft + 9}, and the button adds 123 and 35. */
    public static final int TFC_BUTTON_X = 9 + 123;
    public static final int TFC_BUTTON_Y = 35;
    public static final int BUTTON_SIZE = 20;
    /** The button sprites in {@code large_vessel.png}: u 236, v 0 when sealed, v 20 when not. */
    public static final int BUTTON_U = 236;
    public static final int BUTTON_V_SEALED = 0;
    public static final int BUTTON_V_UNSEALED = 20;
    public static final int FRAME_SEALED = 0;
    public static final int FRAME_UNSEALED = 1;
    public static final int BUTTON_SLOT = SLOTS.slotUnder(TFC_BUTTON_X, TFC_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE);

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/large_vessel", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage SEAL = new GuiImage("tfp:gui/large_vessel_seal", TFC_BUTTON_X, TFC_BUTTON_Y + DY,
        BUTTON_SIZE, BUTTON_SIZE, 2, 1);
    public static final String SEAL_LAYER = "seal";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(new GuiLayers.Layer(SEAL_LAYER, 10, SEAL)));

    private SampleScreen() {
    }

    /** The title glyphs for the seal state. */
    public static TitleGlyphs glyphs(boolean sealed) {
        return LAYERS.compose(Map.of(SEAL_LAYER, sealed ? FRAME_SEALED : FRAME_UNSEALED));
    }
}
