/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/GrillContainer.java @ c3b4d9536 (addContainerSlots: fuel
 *       (8, 70 - 18i), grill inputs (62 + 18k, 20); init(playerInv, 20)),
 * file: src/main/java/net/dries007/tfc/client/screen/GrillScreen.java @ c3b4d9536 (imageHeight + 20; the thermometer at
 *       (30, 76 - min(51, temp)), its tooltip over (30, 25, 15, 51)),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (cascadeFuelSlots)
 */
package dev.tfp.paper.grill.gui;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.SlotLayout;
import dev.tfp.paper.firepit.gui.FirepitScreenLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-08: where TFC's grill screen lands in a 4-row chest (catalogue {@code m3-heat-03-grill}). TFC's screen is
 * 176 x 186, a 4-row chest; as the firepit screen the art moves down {@link #DY} = 2 pixels so the fuel column
 * (8, 70 - 18i) sits on chest column 0, rows 3 to 0.
 *
 * <pre>
 *  col 0    1     2   3     4     5     6     7
 *  [fuel3] (therm)    [g4]  [g5]  [g6]  [g7]  [g8]
 *  [fuel2] (therm)
 *  [fuel1] (therm)
 *  [fuel0] (therm)
 * </pre>
 *
 * TFC's grill inputs (62 + 18k, 20) are chest columns 3 to 7; moved down they sit 4 pixels under row 0, so they go up to
 * row 0 (ledger divergence). The thermometer column (x 30) is the firepit's: the same strip in the same place, so the
 * screen reuses {@link FirepitScreenLayout#THERMOMETER}; its four slots are buttons whose tooltip is the temperature. The
 * background {@link #BACKGROUND} is written by datagen in the follow-up PAR-HEAT-24.
 */
public final class GrillScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(4);
    /** TFC {@code GrillContainer.addContainerSlots}, in block entity slot order (fuel 0..3, grill 4..8). */
    public static final List<SlotLayout.Point> TFC_SLOTS = List.of(
        new SlotLayout.Point(8, 70), new SlotLayout.Point(8, 52), new SlotLayout.Point(8, 34), new SlotLayout.Point(8, 16),
        new SlotLayout.Point(62, 20), new SlotLayout.Point(80, 20), new SlotLayout.Point(98, 20), new SlotLayout.Point(116, 20),
        new SlotLayout.Point(134, 20));
    /** The art's vertical move: fuel slot 3 (y 16) onto chest row 0. */
    public static final int DY = SlotLayout.dyFor(TFC_SLOTS.get(FirepitSim.SLOT_FUEL_INPUT), 0);
    /** Chest slot of each TFC slot, in TFC order. */
    public static final List<Integer> CHEST_SLOTS = List.of(27, 18, 9, 0, 3, 4, 5, 6, 7);
    /** The thermometer column (chest column 1): buttons showing the temperature. */
    public static final List<Integer> THERMOMETER_SLOTS = FirepitScreenLayout.THERMOMETER_SLOTS;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/grill", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage THERMOMETER = FirepitScreenLayout.THERMOMETER;
    public static final String THERMOMETER_LAYER = FirepitScreenLayout.THERMOMETER_LAYER;
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND,
        List.of(new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER)));

    private GrillScreenLayout() {
    }

    public static int chestSlot(int tfcSlot) {
        return CHEST_SLOTS.get(tfcSlot);
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1. */
    public static int tfcSlot(int chestSlot) {
        return CHEST_SLOTS.indexOf(chestSlot);
    }

    /** TFC {@code GrillBlockEntity.getSlotStackLimit}: one item everywhere. */
    public static int limit(int tfcSlot) {
        return GrillSim.slotLimit(tfcSlot);
    }

    /** The thermometer frame, as the firepit's. */
    public static Map<String, Integer> frames(float temperature) {
        return FirepitScreenLayout.frames(temperature);
    }

    /** TFC {@code cascadeFuelSlots} over the nine slots: the non-empty fuel slots move down, in order. */
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
