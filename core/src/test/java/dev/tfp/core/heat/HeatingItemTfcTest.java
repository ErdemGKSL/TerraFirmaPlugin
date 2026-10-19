/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/item/HeatingItemTest.java @ c3b4d9536 (testCookMeat)
 */
package dev.tfp.core.heat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Port of TFC's {@code HeatingItemTest} (c3b4d9536). The test has one case, {@code testCookMeat}, which needs the
 * Minecraft item registry, the {@code #tfc:raw_meats} tag, {@code HeatCapability} on an item stack and
 * {@code HeatingRecipe.getRecipe}. Those parts are not ported here, because the core has no items or recipes:
 * <ul>
 *   <li>The loop over the non-vanilla raw meats (the tag and the namespace filter): left out, it is item data.</li>
 *   <li>{@code assertNotNull(heat)}: an item with a heat capability is a Paper and item-data check, left out.</li>
 *   <li>{@code HeatingRecipe.getRecipe(stack) != null}: a recipe lookup on a stack, left out until the heating
 *       recipes are ported.</li>
 * </ul>
 * The part that is pure heat arithmetic is ported below: an item heated to {@code Heat.maxVisibleTemperature()} reads
 * back that temperature, and it is the visible top level.
 */
class HeatingItemTfcTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;

    /**
     * TFC {@code HeatingItemTest.testCookMeat} (lines 26-44), pure part: {@code setTemperature(maxVisibleTemperature())}
     * then {@code getTemperature()} gives the same value, with no cooling in the same tick, for a heatable item with a
     * finite capacity and for one with no definition (infinite capacity).
     */
    @Test
    void testCookMeatSetMaxVisibleTemperatureReadsBack() {
        float max = HeatLevel.MAX_VISIBLE;
        assertThat(max).isEqualTo(1600f);

        for (float capacity : new float[] {2.857142925262451f, 0.11428571492433548f, Float.POSITIVE_INFINITY}) {
            HeatState set = HeatState.cold(0L).set(max, 100L);
            assertThat(set.temperature()).isEqualTo(max);
            assertThat(set.temperatureAt(100L, capacity, CFG)).isEqualTo(max);
        }
    }

    /**
     * TFC {@code HeatingItemTest.testCookMeat} (line 38), pure part: the visible top of the heat scale is reached and
     * shown, {@code Heat.getHeat} gives {@code BRILLIANT_WHITE} and the thermometer is full (51 pixels).
     */
    @Test
    void testCookMeatMaxVisibleIsTheTopLevel() {
        assertThat(HeatLevel.of(HeatLevel.MAX_VISIBLE)).contains(HeatLevel.BRILLIANT_WHITE);
        assertThat(HeatLevel.scaleForGui(HeatLevel.MAX_VISIBLE)).isEqualTo(51);
    }
}
