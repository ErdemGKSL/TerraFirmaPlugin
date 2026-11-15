/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pot.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
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
}
