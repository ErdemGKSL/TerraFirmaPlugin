/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.thirst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** PAR-SURV-04: TFC's rain-drinking gate ({@code onPlayerTick}) and the thirst effect's combination and duration. */
class RainDrinkingTest {
    @Test
    void looksUpAtMinusEightyOrBelow() {
        assertThat(RainDrinking.looksUp(-90f)).isTrue();
        assertThat(RainDrinking.looksUp(-80f)).isTrue();
        assertThat(RainDrinking.looksUp(-79.9f)).isFalse();
        assertThat(RainDrinking.looksUp(0f)).isFalse();
        assertThat(RainDrinking.looksUp(90f)).isFalse();
        assertThat(RainDrinking.looksUp(280f)).as("wrapped to -80").isTrue();
    }

    @Test
    void rainNeedsWeatherSkyAndATemperatureAboveZero() {
        assertThat(RainDrinking.drinks(-90f, true, true, 10f)).isTrue();
        assertThat(RainDrinking.drinks(-90f, false, true, 10f)).as("clear weather").isFalse();
        assertThat(RainDrinking.drinks(-90f, true, false, 10f)).as("under a roof").isFalse();
        assertThat(RainDrinking.drinks(-90f, true, true, 0f)).as("snow").isFalse();
        assertThat(RainDrinking.drinks(-90f, true, true, Float.NaN)).as("no reading").isFalse();
        assertThat(RainDrinking.drinks(-45f, true, true, 10f)).as("not looking up").isFalse();
    }

    @Test
    void oneSecondOfRainIsTwentyTicksOfFiveTwentyFourths() {
        ThirstState s = new ThirstState(50f, 0).drinkRain(20, ThirstConfig.DEFAULT);
        assertThat(s.thirst()).isCloseTo(54.1667f, org.assertj.core.api.Assertions.within(1e-3f));
        assertThat(new ThirstState(50f, 0).drinkRain(0, ThirstConfig.DEFAULT).thirst()).isEqualTo(50f);
    }

    @Test
    void thirstEffectTickerAndCombination() {
        assertThat(new ThirstEffect(0, 1).ticker()).isEqualTo(50);
        assertThat(new ThirstEffect(1, 1).ticker()).isEqualTo(25);
        assertThat(new ThirstEffect(6, 1).ticker()).isZero();
        assertThat(new ThirstEffect(40, 1).ticker()).isZero();
        ThirstEffect running = new ThirstEffect(1, 300);
        assertThat(running.combine(new ThirstEffect(1, 600))).isEqualTo(new ThirstEffect(1, 600));
        assertThat(running.combine(new ThirstEffect(1, 80))).isEqualTo(running);
        assertThat(running.combine(new ThirstEffect(2, 10))).isEqualTo(new ThirstEffect(2, 10));
        assertThat(running.combine(new ThirstEffect(0, 6000))).isEqualTo(running);
        ThirstEffect.Drain partial = running.advance(50f, 20);
        assertThat(partial.left()).isEqualTo(new ThirstEffect(1, 280));
        assertThat(running.advance(50f, 0).left()).isSameAs(running);
        assertThatThrownBy(() -> new ThirstEffect(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
