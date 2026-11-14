/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/FirepitContainer.java @ c3b4d9536 (addContainerSlots: fuel
 *       (8, 70 - 18i), input (80, 29), outputs (71, 57) and (89, 57); init(playerInv, 20)),
 * file: src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java @ c3b4d9536 (imageHeight + 20; the thermometer
 *       at (30, 76 - min(51, temp)), its tooltip over (30, 25, 15, 51)),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536
 *       (getSlotStackLimit, cascadeFuelSlots)
 */
package dev.tfp.paper.firepit.gui;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.core.gui.SlotLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-07: where TFC's firepit screen lands in a 4-row chest (catalogue {@code m3-heat-03-firepit}). TFC's screen is
 * 176 x 186 ({@code imageHeight + 20}), exactly a 4-row chest; the art moves down {@link #DY} = 2 pixels so the fuel
 * column (8, 70 - 18i) sits on chest column 0, rows 3 to 0.
 *
 * <pre>
 *  col 0    1     2   3     4     5
 *  [fuel3] (therm)          [in]
 *  [fuel2] (therm)
 *  [fuel1] (therm)    [out1]    [out2]
 *  [fuel0] (therm)
 * </pre>
 *
 * TFC's input (80, 29) and outputs (71, 57), (89, 57) are off the chest grid once moved: the input goes up to row 0 of its
 * own column, the outputs to row 2, one column each side of the input (so they stay centred under it). The thermometer
 * column (x 30) is decoration: its four slots are buttons whose tooltip is the firepit's temperature (TFC's tooltip over
 * the thermometer). The background and thermometer art ({@link #BACKGROUND}, {@link #THERMOMETER}) are written by
 * datagen in follow-up PAR-HEAT-20.
 */
public final class FirepitScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(4);
    /** TFC's screen height: {@code AbstractContainerScreen} 166 + 20. */
    public static final int TFC_HEIGHT = 186;
    /** TFC {@code FirepitContainer.addContainerSlots}, in block entity slot order (fuel 0..3, input 4, outputs 5, 6). */
    public static final List<SlotLayout.Point> TFC_SLOTS = List.of(
        new SlotLayout.Point(8, 70), new SlotLayout.Point(8, 52), new SlotLayout.Point(8, 34), new SlotLayout.Point(8, 16),
        new SlotLayout.Point(80, 29), new SlotLayout.Point(71, 57), new SlotLayout.Point(89, 57));
    /** The art's vertical move: fuel slot 3 (y 16) onto chest row 0. */
    public static final int DY = SlotLayout.dyFor(TFC_SLOTS.get(FirepitSim.SLOT_FUEL_INPUT), 0);
    /** Chest slot of each TFC slot, in TFC order. */
    public static final List<Integer> CHEST_SLOTS = List.of(27, 18, 9, 0, 4, 21, 23);
    /** The thermometer column (chest column 1): buttons showing the temperature. */
    public static final List<Integer> THERMOMETER_SLOTS = List.of(1, 10, 19, 28);

    /** TFC's thermometer sprite: x 30, its travel from {@code 76 - 51} down to {@code 76 + 5}. */
    public static final int TFC_THERMOMETER_X = 30;
    public static final int TFC_THERMOMETER_BOTTOM = 76;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/firepit", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    /** The 52-frame thermometer strip ({@link ProgressFrames#thermometer}) moved by {@link #DY}. */
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/firepit_thermometer", TFC_THERMOMETER_X,
        TFC_THERMOMETER_BOTTOM - ProgressFrames.THERMOMETER_TRAVEL + DY, ProgressFrames.THERMOMETER_WIDTH,
        ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);
    public static final String THERMOMETER_LAYER = "thermometer";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER)));

    private FirepitScreenLayout() {
    }

    /** The chest slot of TFC slot {@code tfcSlot}. */
    public static int chestSlot(int tfcSlot) {
        return CHEST_SLOTS.get(tfcSlot);
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1. */
    public static int tfcSlot(int chestSlot) {
        return CHEST_SLOTS.indexOf(chestSlot);
    }

    /** TFC {@code getSlotStackLimit}: the outputs hold full stacks, the rest one item. */
    public static int limit(int tfcSlot) {
        return FirepitSim.slotLimit(tfcSlot);
    }

    /** The thermometer frame for {@code temperature}: TFC draws nothing at 0 and the indicator k pixels up otherwise. */
    public static Map<String, Integer> frames(float temperature) {
        return Map.of(THERMOMETER_LAYER, ProgressFrames.thermometer(temperature));
    }

    /**
     * TFC {@code cascadeFuelSlots}: the non-empty fuel slots move down to the lowest open slots, in order. Returns a new
     * list of all seven slots.
     */
    public static List<ItemStack> cascade(List<ItemStack> slots) {
        List<ItemStack> out = new ArrayList<>(slots);
        int lowest = FirepitSim.SLOT_FUEL_CONSUME;
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            ItemStack s = out.get(i);
            if (s != null && !s.isEmpty()) {
                if (i > lowest) {
                    out.set(lowest, s);
                    out.set(i, ItemStack.empty());
                }
                lowest++;
            }
        }
        return out;
    }
}
