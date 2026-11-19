/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player.exhaustion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-SURV-15: vanilla FoodData's spending order. */
class FoodSpendTest {
    @Test
    void saturationGoesFirst() {
        assertThat(FoodSpend.spend(20, 5f, 3)).isEqualTo(new FoodSpend.Bar(20, 2f));
    }

    @Test
    void thenFoodOnePerPoint() {
        assertThat(FoodSpend.spend(20, 1.5f, 4)).isEqualTo(new FoodSpend.Bar(18, 0f));
        assertThat(FoodSpend.spend(3, 0f, 10)).isEqualTo(new FoodSpend.Bar(0, 0f));
    }

    @Test
    void noPointsNoChange() {
        assertThat(FoodSpend.spend(17, 2f, 0)).isEqualTo(new FoodSpend.Bar(17, 2f));
    }
}
