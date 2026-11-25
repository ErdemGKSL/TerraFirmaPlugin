/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BlastFurnaceContainer.java @ c3b4d9536 (the tuyere slot),
 *       src/main/java/net/dries007/tfc/client/screen/BlastFurnaceScreen.java @ c3b4d9536 (renderBg: the two capacity-limited
 *       meters, the temperature indicator, the output fluid)
 */
package dev.tfp.core.blastfurnace;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.gui.ProgressFrames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PAR-METAL-27: where TFC's blast furnace screen lands in the 4-row chest that carries it. TFC's window is 176 x 186 (the
 * player inventory 20 lower); its one slot (152, 17) is a pixel above a chest's (152, 18), so the art above the player
 * inventory moves down {@link #DY} = 1 and the player inventory (TFC 104, chest 103) up one (see the datagen
 * {@code BlastFurnaceScreenArt}, which reads these numbers).
 *
 * <pre>
 *  col:   0        2              3 4 5            6     8
 *  row 0  (therm)  (ore bar)                       (fuel) [tuyere]
 *  row 1  (therm)  (ore bar)                       (fuel)
 *  row 2  (therm)  (ore bar)  (tank) (tank) (tank) (fuel)
 *  row 3  (therm)  (ore bar)  (tank) (tank) (tank) (fuel)
 * </pre>
 *
 * The columns marked in brackets are buttons carrying TFC's tooltips; the bars and the tank are drawn by the art layers.
 * Pure arithmetic, no platform types.
 */
public final class BlastFurnaceScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(4);
    public static final int DY = 1;
    /** The tuyere's chest slot: TFC (152, 17), chest row 0 column 8. */
    public static final int TUYERE_SLOT = 8;
    public static final List<Integer> THERMOMETER_SLOTS = List.of(0, 9, 18, 27);
    public static final List<Integer> ORE_SLOTS = List.of(2, 11, 20, 29);
    public static final List<Integer> FUEL_SLOTS = List.of(6, 15, 24, 33);
    public static final List<Integer> TANK_SLOTS = List.of(21, 22, 23, 30, 31, 32);

    // TFC's blits
    public static final int TFC_THERMOMETER_X = 8;
    public static final int TFC_THERMOMETER_BOTTOM = 76;
    public static final int THERMOMETER_U = 176;
    public static final int THERMOMETER_V = 0;
    public static final int ORE_X = 42;
    public static final int FUEL_X = 124;
    public static final int METER_Y = 22;
    public static final int METER_WIDTH = 10;
    public static final int METER_HEIGHT = 66;
    /** The usable height of a meter: {@code 64 x content / maximum}. */
    public static final int METER_SPAN = 64;
    public static final int DOTTED_U = 246;
    public static final int EMPTY_U = 236;
    public static final int ORE_FILL_LIT_U = 216;
    public static final int ORE_FILL_UNLIT_U = 226;
    public static final int FUEL_FILL_U = 206;
    public static final int FILL_V = 1;
    public static final int TFC_FLUID_X = 70;
    public static final int TFC_FLUID_BOTTOM = 84;
    public static final int FLUID_WIDTH = 36;
    public static final int FLUID_HEIGHT = 31;
    public static final int FLUID_FRAMES = FLUID_HEIGHT + 1;
    /** Frames of a bar: the dotted full region, then one per empty height 0 to 64. */
    public static final int BAR_FRAMES = METER_SPAN + 2;
    /** Frames of a fill: one per height 1 to 64 (and 64 more for the lit ore fill). */
    public static final int FILL_FRAMES = METER_SPAN;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/blast_furnace", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/blast_furnace_thermometer", TFC_THERMOMETER_X,
        TFC_THERMOMETER_BOTTOM - ProgressFrames.THERMOMETER_TRAVEL + DY, ProgressFrames.THERMOMETER_WIDTH,
        ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);
    public static final GuiImage ORE_BAR = bar("tfp:gui/blast_furnace_ore_bar", ORE_X);
    public static final GuiImage FUEL_BAR = bar("tfp:gui/blast_furnace_fuel_bar", FUEL_X);
    public static final GuiImage ORE_FILL = new GuiImage("tfp:gui/blast_furnace_ore_fill", ORE_X, METER_Y + DY, METER_WIDTH,
        METER_HEIGHT, 8, 16);
    public static final GuiImage FUEL_FILL = new GuiImage("tfp:gui/blast_furnace_fuel_fill", FUEL_X, METER_Y + DY, METER_WIDTH,
        METER_HEIGHT, 8, 8);
    public static final GuiImage FLUID = new GuiImage("tfp:gui/blast_furnace_fluid", TFC_FLUID_X,
        TFC_FLUID_BOTTOM - FLUID_HEIGHT + DY, FLUID_WIDTH, FLUID_HEIGHT, 4, 8);

    public static final String THERMOMETER_LAYER = "thermometer";
    public static final String ORE_BAR_LAYER = "ore_bar";
    public static final String ORE_FILL_LAYER = "ore_fill";
    public static final String FUEL_BAR_LAYER = "fuel_bar";
    public static final String FUEL_FILL_LAYER = "fuel_fill";
    public static final String FLUID_LAYER = "fluid";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(
        new GuiLayers.Layer(ORE_BAR_LAYER, 1, ORE_BAR), new GuiLayers.Layer(FUEL_BAR_LAYER, 2, FUEL_BAR),
        new GuiLayers.Layer(ORE_FILL_LAYER, 3, ORE_FILL), new GuiLayers.Layer(FUEL_FILL_LAYER, 4, FUEL_FILL),
        new GuiLayers.Layer(FLUID_LAYER, 5, FLUID), new GuiLayers.Layer(THERMOMETER_LAYER, 10, THERMOMETER)));

    private BlastFurnaceScreenLayout() {
    }

    private static GuiImage bar(String id, int x) {
        return new GuiImage(id, x, METER_Y + DY, METER_WIDTH, METER_HEIGHT, 6, 11);
    }

    /** What the screen shows. */
    public record View(float temperature, boolean lit, int capacity, int maximum, int inputs, int fuel, int fluid,
            int fluidCapacity) {
    }

    /**
     * TFC {@code renderCapacityLimitedFillMeter}: the bar frame, no capacity the dotted region (frame 0), otherwise the top
     * {@code 1 + emptyHeight} rows of the empty bar (frame {@code 1 + emptyHeight}).
     */
    public static int barFrame(int capacity, int maximum) {
        if (capacity <= 0 || maximum <= 0) {
            return 0;
        }
        return 1 + emptyHeight(capacity, maximum);
    }

    /** {@code (64 x capacity) / maximum}, at most 64. */
    public static int emptyHeight(int capacity, int maximum) {
        return (int) Math.min(METER_SPAN, (long) METER_SPAN * capacity / maximum);
    }

    /** {@code (64 x content) / maximum} pixels of fill, 0 to 64. */
    public static int fillHeight(int content, int maximum) {
        if (content <= 0 || maximum <= 0) {
            return 0;
        }
        return (int) Math.min(METER_SPAN, (long) METER_SPAN * content / maximum);
    }

    /** The fill frame of {@code height} pixels (1 to 64) in a grid, or -1 for none; {@code lit} shifts the ore fill. */
    public static int fillFrame(int height, boolean litShift) {
        return height <= 0 ? -1 : height - 1 + (litShift ? FILL_FRAMES : 0);
    }

    /** TFC's fill: {@code ceil(31 x amount / capacity)} pixels, none for an empty tank. */
    public static int fluidFrame(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        return Math.min(FLUID_HEIGHT, (int) Math.ceil((float) FLUID_HEIGHT * amount / capacity));
    }

    public static Map<String, Integer> frames(View v) {
        Map<String, Integer> out = new HashMap<>();
        out.put(THERMOMETER_LAYER, ProgressFrames.thermometer(v.temperature()));
        out.put(ORE_BAR_LAYER, barFrame(v.capacity(), v.maximum()));
        out.put(FUEL_BAR_LAYER, barFrame(v.capacity(), v.maximum()));
        // an unlit furnace draws the unlit ore fill (u 226), a lit one u 216: the second half of the grid
        out.put(ORE_FILL_LAYER, v.capacity() <= 0 ? -1 : fillFrame(fillHeight(v.inputs(), v.maximum()), v.lit()));
        out.put(FUEL_FILL_LAYER, v.capacity() <= 0 ? -1 : fillFrame(fillHeight(v.fuel(), v.maximum()), false));
        out.put(FLUID_LAYER, fluidFrame(v.fluid(), v.fluidCapacity()));
        return Map.copyOf(out);
    }
}
