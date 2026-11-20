/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.farm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.farm.FarmlandRules.SoilHydration;
import org.junit.jupiter.api.Test;

/** PAR-FARM-01: the hoe overlay hydration numbers. */
class FarmlandHydrationTest {
    @Test
    void minAndMaxRainHydrationFollowTheVariance() {
        assertThat(FarmlandHydration.minRainHydration(300f, 0.5f)).isEqualTo(300f * 0.5f * 0.1f);
        assertThat(FarmlandHydration.maxRainHydration(300f, 0.5f)).isEqualTo(300f * 1.5f * 0.1f);
        assertThat(FarmlandHydration.maxRainHydration(600f, 1f)).isEqualTo(60f); // capped
        assertThat(FarmlandHydration.minRainHydration(300f, -0.5f)).isEqualTo(15f); // |variance|
    }

    @Test
    void waterSourceAddsFortyAndSoilScalesTheTotal() {
        // 300 mm: 30 rain hydration; source (cost 2): +40; normal soil
        FarmlandHydration h = FarmlandHydration.of(300f, 300f, 0f, false, 2, SoilHydration.NORMAL);
        assertThat(h.now()).isEqualTo(70);
        assertThat(h.min()).isEqualTo(70);
        assertThat(h.max()).isEqualTo(70);
        assertThat(FarmlandHydration.of(300f, 300f, 0f, false, 2, SoilHydration.INCREASES).now()).isEqualTo(100);
        assertThat(FarmlandHydration.of(300f, 300f, 0f, false, 2, SoilHydration.DECREASES).now()).isEqualTo(35);
    }

    @Test
    void noSourceMeansRainOnlyAndWaterloggedIsFull() {
        assertThat(FarmlandHydration.of(300f, 300f, 0f, false, FarmlandRules.NO_SOURCE_COST, SoilHydration.NORMAL).now()).isEqualTo(30);
        assertThat(FarmlandHydration.of(0f, 0f, 0f, true, FarmlandRules.NO_SOURCE_COST, SoilHydration.DECREASES).now()).isEqualTo(100);
    }

    @Test
    void minIsAtMostNowAtMostMaxForAnAverageRainNow() {
        FarmlandHydration h = FarmlandHydration.of(250f, 250f, 0.4f, false, FarmlandRules.NO_SOURCE_COST, SoilHydration.NORMAL);
        assertThat(h.min()).isLessThanOrEqualTo(h.now());
        assertThat(h.now()).isLessThanOrEqualTo(h.max());
    }
}
