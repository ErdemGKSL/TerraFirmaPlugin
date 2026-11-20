/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.plantslow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PlantSlowRulesTest {
    @Test
    void plantFactorLerpsTheSpeedFactorTowardsOneByTheModifier() {
        // lerp(0.2, speed, 1) = speed + (1 - speed) * 0.2
        assertThat(PlantSlowRules.plant(1.0f)).isEqualTo(1.0f);
        assertThat(PlantSlowRules.plant(0.9f)).isCloseTo(0.92f, within(1e-6f));
        assertThat(PlantSlowRules.plant(0.7f)).isCloseTo(0.76f, within(1e-6f));
        assertThat(PlantSlowRules.plant(0.4f)).isCloseTo(0.52f, within(1e-6f));
        assertThat(PlantSlowRules.plant(0.0f)).isCloseTo(0.2f, within(1e-6f));
    }

    @Test
    void berryBushSlowsMoreWithTheStage() {
        // Mth.lerp((1 + stage) / 3, 1, 0.4)
        assertThat(PlantSlowRules.seasonalBush(0, 0.4f)).isCloseTo(0.8f, within(1e-6f));
        assertThat(PlantSlowRules.seasonalBush(1, 0.4f)).isCloseTo(0.6f, within(1e-6f));
        assertThat(PlantSlowRules.seasonalBush(2, 0.4f)).isCloseTo(0.4f, within(1e-6f));
    }

    @Test
    void thatchSlowsOnlyOutOfFluid() {
        assertThat(PlantSlowRules.thatch(false, PlantSlowRules.THATCH_MULTIPLIER)).isEqualTo(0.6f);
        assertThat(PlantSlowRules.thatch(true, PlantSlowRules.THATCH_MULTIPLIER)).isEqualTo(1.0f);
    }
}
