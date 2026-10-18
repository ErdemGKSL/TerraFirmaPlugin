/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (handleCooking)
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.heat.HeatConfig;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-03 golden values: the grill's item heating against TFC's {@code GrillBlockEntity.handleCooking} at c3b4d9536
 * (addTemp then adjustTemp, with the grill temperature held constant over the window), and the first tick a
 * {@code HeatingRecipe} accepts the item.
 */
class GrillGoldenTest {
    private static final HeatConfig CONFIG = HeatConfig.DEFAULT;

    @Test
    void lazyMatchesTfcGrillGolden() throws IOException {
        var rows = FirepitGoldenTest.rows("grill.txt");
        assertThat(rows).hasSize(252);
        for (String[] r : rows) {
            float after = GrillRules.advance(Float.parseFloat(r[0]), Float.parseFloat(r[1]), Float.parseFloat(r[2]), Long.parseLong(r[3]), CONFIG);
            float expected = Float.parseFloat(r[4]);
            assertThat(after).as(String.join("|", r)).isCloseTo(expected, within(tolerance(expected, Long.parseLong(r[3]))));
        }
    }

    @Test
    void ticksToCookMatchesTfcGolden() throws IOException {
        var rows = FirepitGoldenTest.rows("cook.txt");
        assertThat(rows).hasSize(81);
        for (String[] r : rows) {
            long ticks = GrillRules.ticksToCook(Float.parseFloat(r[0]), Float.parseFloat(r[1]), Float.parseFloat(r[2]), Float.parseFloat(r[3]), CONFIG);
            // An exact tie (the item reaches the recipe temperature on a tick exactly) can land one tick later in TFC's float steps.
            assertThat(Math.abs(ticks - Long.parseLong(r[4]))).as(String.join("|", r)).isLessThanOrEqualTo(1);
        }
    }

    /**
     * The closed form is exact in real numbers; TFC rounds each tick to float32, so after {@code ticks} ticks the two can differ
     * by up to {@code ticks} half-ulps of the value. The bound used is {@code ticks x ulp}, with a floor of 0.01.
     */
    static float tolerance(float value, long ticks) {
        return Math.max(0.01f, (float) ticks * Math.ulp(value));
    }

    @Test
    void weakHeatingIsRefused() {
        assertThatThrownBy(() -> GrillRules.advance(0f, 4f, 500f, 10, new HeatConfig(0.8, 0.2)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
