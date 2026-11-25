/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pot.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-09: the pot screen's chest slots follow TFC's PotContainer points, and TFC's bubble frames. */
class PotScreenLayoutTest {
    @Test
    void everyTfcSlotLandsOnTheNearestChestSlotOnce() {
        assertThat(PotScreenLayout.DY).isEqualTo(2);
        assertThat(PotScreenLayout.TFC_HEIGHT).isEqualTo(PotScreenLayout.CHEST.height());
        Set<Integer> used = new HashSet<>();
        for (int tfc = 0; tfc < PotScreenLayout.TFC_SLOTS.size(); tfc++) {
            int chest = PotScreenLayout.chestSlot(tfc);
            assertThat(used.add(chest)).as("slot %d", tfc).isTrue();
            assertThat(PotScreenLayout.tfcSlot(chest)).isEqualTo(tfc);
            int x = PotScreenLayout.TFC_SLOTS.get(tfc).x();
            int y = PotScreenLayout.TFC_SLOTS.get(tfc).y() + PotScreenLayout.DY;
            int col = chest % ChestGeometry.COLUMNS;
            int row = chest / ChestGeometry.COLUMNS;
            assertThat(Math.abs(ChestGeometry.SLOT_X0 + ChestGeometry.SLOT * col - x)).as("x of slot %d", tfc)
                .isLessThanOrEqualTo(ChestGeometry.SLOT / 2);
            assertThat(Math.abs(ChestGeometry.SLOT_Y0 + ChestGeometry.SLOT * row - y)).as("y of slot %d", tfc)
                .isLessThanOrEqualTo(ChestGeometry.SLOT / 2);
        }
        for (int b : PotScreenLayout.THERMOMETER_SLOTS) {
            assertThat(used).doesNotContain(b);
        }
        assertThat(used).doesNotContain(PotScreenLayout.FLUID_SLOT, PotScreenLayout.STATUS_SLOT);
    }

    @Test
    void bubblesFollowTfcsThirtyFiveTickCycle() {
        assertThat(PotScreenLayout.boilingFrame(0)).isZero();
        assertThat(PotScreenLayout.boilingFrame(1)).isEqualTo(1); // ceil(1 / 35 * 20)
        assertThat(PotScreenLayout.boilingFrame(17)).isEqualTo(10);
        assertThat(PotScreenLayout.boilingFrame(34)).isEqualTo(20);
        assertThat(PotScreenLayout.boilingFrame(35)).isZero();
        assertThat(PotScreenLayout.BOILING.frames()).isEqualTo(PotScreenLayout.BOILING_FRAMES);
        assertThat(PotScreenLayout.frames(0f, 0)).containsEntry(PotScreenLayout.THERMOMETER_LAYER, 0);
    }

    /** PAR-HEAT-25: the image rects are the numbers {@code potgui/PotScreenArt} draws (its test pins the same ones). */
    @Test
    void imageRectsEqualTheDatagenArt() {
        assertThat(PotScreenLayout.BACKGROUND).isEqualTo(GuiImage.of("tfp:gui/pot", 0, 0, 176, 186));
        assertThat(PotScreenLayout.THERMOMETER).isEqualTo(new GuiImage("tfp:gui/pot_thermometer", 30, 27, 15, 56, 4, 13));
        assertThat(PotScreenLayout.BOILING).isEqualTo(new GuiImage("tfp:gui/pot_boiling", 131, 12, 24, 20, 3, 7));
        assertThat(PotScreenLayout.FLAMES).isEqualTo(new GuiImage("tfp:gui/pot_flames", 121, 60, 43, 13, 1, 2));
        assertThat(PotScreenLayout.CHEST_SLOTS).containsExactly(27, 18, 9, 0, 3, 4, 12, 13, 14);
        assertThat(PotScreenLayout.DY).isEqualTo(2);
    }

    /** TFC draws the three flames whenever the pot's temperature is above 0. */
    @Test
    void flamesShowWhileThePotIsHot() {
        assertThat(PotScreenLayout.frames(0f, 0)).containsEntry(PotScreenLayout.FLAMES_LAYER, 0);
        assertThat(PotScreenLayout.frames(0.5f, 0)).containsEntry(PotScreenLayout.FLAMES_LAYER, 1);
        assertThat(PotScreenLayout.frames(200f, 12)).containsEntry(PotScreenLayout.FLAMES_LAYER, 1);
        assertThat(PotScreenLayout.LAYERS.compose(PotScreenLayout.frames(200f, 12)).tokens()).isNotEmpty();
        assertThat(PotScreenLayout.LAYERS.images()).extracting(GuiImage::id).containsExactly("tfp:gui/pot",
            "tfp:gui/pot_thermometer", "tfp:gui/pot_flames", "tfp:gui/pot_boiling");
    }
}
