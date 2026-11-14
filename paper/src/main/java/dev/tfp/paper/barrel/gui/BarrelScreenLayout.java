/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BarrelContainer.java @ c3b4d9536 (slots (35, 20), (35, 54), (89, 37)),
 * file: src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java @ c3b4d9536 (the tank at (7, 19), 50 pixels of fluid),
 * file: src/main/java/net/dries007/tfc/client/screen/button/BarrelSealButton.java @ c3b4d9536 (the button at (123, 35))
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.List;
import java.util.Map;

/**
 * PAR-PROC-09: where TFC's barrel screen lands in a 3-row chest (catalogue {@code m3-proc-03-barrel}). The art is written
 * by datagen ({@code dev.tfp.datagen.barrelrack.BarrelScreenArt}); the numbers here must equal its images
 * ({@code BarrelScreenLayoutContentTest} compares them with the generated YAML).
 *
 * <pre>
 *  col 0   1   2   3   4   5   6   7   8
 *  [tank]     [in]
 *  [TANK]                 [item]    [seal]
 *  [tank]     [out]
 * </pre>
 *
 * TFC's slots are off the chest grid (x 35 and 89), so everything right of TFC's tank moves 9 pixels right; the tank
 * column is decoration that shows the fluid's amount as its tooltip, and its middle slot holds the tank item that draws
 * the fluid ({@link BarrelTank}).
 */
public final class BarrelScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(3);
    /** TFC {@code BarrelBlockEntity.SLOT_FLUID_CONTAINER_IN}, {@code _OUT}, {@code SLOT_ITEM}. */
    public static final int TFC_FLUID_IN = 0;
    public static final int TFC_FLUID_OUT = 1;
    public static final int TFC_ITEM = 2;
    public static final int TFC_SLOTS = 3;
    /** The chest slots of TFC's slots, in TFC order: column 2 row 0, column 2 row 2, column 5 row 1. */
    public static final List<Integer> CHEST_SLOTS = List.of(2, 20, 14);
    /** The seal button: column 7, row 1 (TFC's 20 x 20 button moved to (132, 34), centred on it). */
    public static final int SEAL_SLOT = 16;
    /** The tank column: the middle slot holds the tank item; all three show the amount on hover. */
    public static final int TANK_SLOT = 9;
    public static final List<Integer> TANK_SLOTS = List.of(0, 9, 18);
    /** TFC's tank holds 50 pixels of fluid. */
    public static final int TANK_PIXELS = 50;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/barrel", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage SEAL = new GuiImage("tfp:gui/barrel_seal", 132, 34, 20, 20, 2, 1);
    public static final GuiImage DISABLED = GuiImage.of("tfp:gui/barrel_disabled", 44, 18, 70, 52);
    public static final String SEAL_LAYER = "seal";
    public static final String DISABLED_LAYER = "disabled";
    /** {@link #SEAL} frames (TFC's sprite v 0 when sealed, v 20 when not). */
    public static final int FRAME_SEALED = 0;
    public static final int FRAME_UNSEALED = 1;
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(
        new GuiLayers.Layer(DISABLED_LAYER, 5, DISABLED),
        new GuiLayers.Layer(SEAL_LAYER, 10, SEAL)));

    /** The tank item's model (datagen {@code items/gui/barrel_tank.json}). */
    public static final String TANK_ITEM_MODEL = "tfp:gui/barrel_tank";

    private BarrelScreenLayout() {
    }

    /** The chest slot of TFC slot {@code tfcSlot}. */
    public static int chestSlot(int tfcSlot) {
        return CHEST_SLOTS.get(tfcSlot);
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1. */
    public static int tfcSlot(int chestSlot) {
        return CHEST_SLOTS.indexOf(chestSlot);
    }

    /** The overlay frames: the seal button's state, and TFC's {@code drawDisabled} highlight while sealed. */
    public static Map<String, Integer> frames(boolean sealed) {
        return Map.of(SEAL_LAYER, sealed ? FRAME_SEALED : FRAME_UNSEALED, DISABLED_LAYER, sealed ? 0 : -1);
    }
}
