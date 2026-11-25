/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoalforge.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.gui.ChestGeometry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-09: the forge screen's slots sit where TFC's {@code CharcoalForgeContainer} puts them, 2 px lower. */
class ForgeScreenLayoutTest {
    /** TFC's (x, y) of each slot, fuel 0 to 4, inputs 5 to 9, extras 10 to 13. */
    private static final int[][] TFC = {
        {80, 70}, {98, 52}, {62, 52}, {116, 34}, {44, 34},
        {80, 52}, {98, 34}, {62, 34}, {116, 16}, {44, 16},
        {152, 16}, {152, 34}, {152, 52}, {152, 70}};

    @Test
    void everyTfcSlotLandsOnTheChestGridTwoPixelsLower() {
        assertThat(ForgeScreenLayout.CHEST_SLOTS).hasSize(CharcoalForgeSim.SLOTS);
        ChestGeometry chest = ForgeScreenLayout.CHEST;
        for (int tfc = 0; tfc < TFC.length; tfc++) {
            int slot = ForgeScreenLayout.chestSlot(tfc);
            assertThat(chest.slotX(slot)).as("x of slot " + tfc).isEqualTo(TFC[tfc][0]);
            assertThat(chest.slotY(slot)).as("y of slot " + tfc).isEqualTo(TFC[tfc][1] + ForgeScreenLayout.DY);
            assertThat(ForgeScreenLayout.tfcSlot(slot)).isEqualTo(tfc);
        }
    }

    @Test
    void slotsAreDistinctAndClearOfTheTemperatureBar() {
        Set<Integer> all = new HashSet<>(ForgeScreenLayout.CHEST_SLOTS);
        assertThat(all).hasSize(CharcoalForgeSim.SLOTS);
        for (int s : ForgeScreenLayout.THERMOMETER_SLOTS) {
            assertThat(all).doesNotContain(s);
            assertThat(ForgeScreenLayout.CHEST.slotX(s)).isEqualTo(8);
        }
        assertThat(ForgeScreenLayout.THERMOMETER_SLOTS).isEqualTo(List.of(0, 9, 18, 27));
    }

    @Test
    void aSlotHoldsOneItemAndTheBarFollowsTfcsTemperatureScale() {
        for (int tfc = 0; tfc < CharcoalForgeSim.SLOTS; tfc++) {
            assertThat(ForgeScreenLayout.limit(tfc)).isEqualTo(1);
        }
        assertThat(ForgeScreenLayout.frames(0f).get(ForgeScreenLayout.THERMOMETER_LAYER)).isZero();
        assertThat(ForgeScreenLayout.frames(100000f).get(ForgeScreenLayout.THERMOMETER_LAYER)).isEqualTo(51);
        // the bar's top pixel row is TFC's 76 - 51 plus the art's move
        assertThat(ForgeScreenLayout.THERMOMETER.y()).isEqualTo(76 - 51 + ForgeScreenLayout.DY);
        assertThat(ForgeScreenLayout.THERMOMETER.x()).isEqualTo(8);
    }
}
