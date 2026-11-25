/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.blastfurnace;

import static dev.tfp.core.blastfurnace.BlastFurnaceScreenLayout.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.blastfurnace.BlastFurnaceScreenLayout.View;
import dev.tfp.core.gui.GuiImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-27: the blast furnace screen's slots, hit areas and frames. */
class BlastFurnaceScreenLayoutTest {
    private static View view(float temp, boolean lit, int cap, int inputs, int fuel, int fluid) {
        return new View(temp, lit, cap, 20, inputs, fuel, fluid, 10_000);
    }

    @Test
    void tuyereSlotIsTfcsAtOnePixelBelow() {
        assertThat(CHEST.slotX(TUYERE_SLOT)).isEqualTo(152);
        assertThat(CHEST.slotY(TUYERE_SLOT)).isEqualTo(17 + DY);
    }

    @Test
    void buttonsAreDistinctAndOffTheTuyere() {
        Set<Integer> all = new HashSet<>();
        for (List<Integer> l : List.of(THERMOMETER_SLOTS, ORE_SLOTS, FUEL_SLOTS, TANK_SLOTS)) {
            for (int s : l) {
                assertThat(all.add(s)).as("slot %d", s).isTrue();
                assertThat(s).isBetween(0, CHEST.size() - 1).isNotEqualTo(TUYERE_SLOT);
            }
        }
    }

    @Test
    void buttonsCoverTheirArt() {
        // each art rectangle (window pixels) lies within the union of the 18 x 18 frames of its buttons
        assertCovered(THERMOMETER_SLOTS, THERMOMETER.x(), 76 - 51 + DY, 15, 51);
        assertCovered(ORE_SLOTS, ORE_X, METER_Y + DY, METER_WIDTH, METER_HEIGHT);
        assertCovered(FUEL_SLOTS, FUEL_X, METER_Y + DY, METER_WIDTH, METER_HEIGHT);
        assertCovered(TANK_SLOTS, FLUID.x(), FLUID.y() + 1, FLUID_WIDTH, FLUID_HEIGHT - 1);
    }

    private static void assertCovered(List<Integer> slots, int x, int y, int w, int h) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                boolean in = false;
                for (int s : slots) {
                    int sx = CHEST.slotX(s) - 1;
                    int sy = CHEST.slotY(s) - 1;
                    in |= i >= sx && i < sx + 18 && j >= sy && j < sy + 18;
                }
                assertThat(in).as("pixel %d,%d", i, j).isTrue();
            }
        }
    }

    @Test
    void imagesFitTheirGrids() {
        assertThat(THERMOMETER.frames()).isEqualTo(52);
        assertThat(ORE_BAR.frames()).isEqualTo(BAR_FRAMES);
        assertThat(FUEL_BAR.frames()).isEqualTo(BAR_FRAMES);
        assertThat(ORE_FILL.frames()).isEqualTo(2 * FILL_FRAMES);
        assertThat(FUEL_FILL.frames()).isEqualTo(FILL_FRAMES);
        assertThat(FLUID.frames()).isEqualTo(FLUID_FRAMES);
        for (GuiImage i : LAYERS.images()) {
            assertThat(i.id()).startsWith("tfp:gui/blast_furnace");
        }
    }

    @Test
    void barFrameIsTfcsEmptyHeight() {
        assertThat(barFrame(0, 20)).isZero(); // dotted: no capacity
        assertThat(barFrame(20, 20)).isEqualTo(65); // full chimney: 1 + 64
        assertThat(barFrame(4, 20)).isEqualTo(1 + 64 * 4 / 20);
        assertThat(barFrame(99, 20)).isEqualTo(65); // clamped
        assertThat(emptyHeight(1, 3)).isEqualTo(21);
    }

    @Test
    void fillFollowsTfcIntegerMath() {
        assertThat(fillHeight(0, 20)).isZero();
        assertThat(fillHeight(1, 20)).isEqualTo(3);
        assertThat(fillHeight(20, 20)).isEqualTo(64);
        assertThat(fillFrame(0, false)).isEqualTo(-1);
        assertThat(fillFrame(1, false)).isZero();
        assertThat(fillFrame(64, false)).isEqualTo(63);
        assertThat(fillFrame(1, true)).isEqualTo(64);
        assertThat(fillFrame(64, true)).isEqualTo(127);
    }

    @Test
    void fluidIsCeilOf31() {
        assertThat(fluidFrame(0, 10_000)).isZero();
        assertThat(fluidFrame(1, 10_000)).isEqualTo(1);
        assertThat(fluidFrame(5_000, 10_000)).isEqualTo(16);
        assertThat(fluidFrame(10_000, 10_000)).isEqualTo(31);
        assertThat(fluidFrame(99_999, 10_000)).isEqualTo(31);
    }

    @Test
    void framesOfAnEmptyAndAHotFurnace() {
        Map<String, Integer> cold = frames(view(0f, false, 0, 0, 0, 0));
        assertThat(cold).containsEntry(THERMOMETER_LAYER, 0).containsEntry(ORE_BAR_LAYER, 0).containsEntry(ORE_FILL_LAYER, -1)
            .containsEntry(FUEL_FILL_LAYER, -1).containsEntry(FLUID_LAYER, 0);
        Map<String, Integer> hot = frames(view(1300f, true, 20, 10, 20, 5_000));
        assertThat(hot.get(THERMOMETER_LAYER)).isEqualTo(51);
        assertThat(hot).containsEntry(ORE_BAR_LAYER, 65).containsEntry(FUEL_BAR_LAYER, 65)
            .containsEntry(ORE_FILL_LAYER, 32 - 1 + 64) // lit: second half
            .containsEntry(FUEL_FILL_LAYER, 63).containsEntry(FLUID_LAYER, 16);
        assertThat(LAYERS.compose(hot)).isNotNull();
        assertThat(LAYERS.compose(cold)).isNotNull();
        // capacity 0 with content draws no fill (TFC draws only the dotted region)
        assertThat(frames(view(0f, false, 0, 5, 5, 0))).containsEntry(ORE_FILL_LAYER, -1).containsEntry(FUEL_FILL_LAYER, -1);
    }
}
