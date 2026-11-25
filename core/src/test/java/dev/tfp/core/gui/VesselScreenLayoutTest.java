/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 */
package dev.tfp.core.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-POT-10: the vessel slots against TFC's coordinates. */
class VesselScreenLayoutTest {
    private static final int[][] TFC = {{71, 23}, {89, 23}, {71, 41}, {89, 41}};

    @Test
    void chestSlotsAreTheTfcBlockMovedOntoTheGrid() {
        assertThat(VesselScreenLayout.CHEST_SLOTS).containsExactly(3, 4, 12, 13);
        ChestGeometry chest = VesselScreenLayout.CHEST;
        for (int i = 0; i < TFC.length; i++) {
            int slot = VesselScreenLayout.chestSlot(i);
            assertThat(chest.slotX(slot)).isEqualTo(TFC[i][0] + VesselScreenLayout.DX_SLOTS);
            assertThat(chest.slotY(slot)).isEqualTo(TFC[i][1] + VesselScreenLayout.DY_SLOTS);
        }
    }

    @Test
    void theBackgroundCoversTheThreeRowChest() {
        assertThat(VesselScreenLayout.BACKGROUND.id()).isEqualTo("tfp:gui/small_vessel");
        assertThat(VesselScreenLayout.CHEST.rows()).isEqualTo(3);
    }
}
