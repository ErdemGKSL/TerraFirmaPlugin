/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** M3-SURV-04: the displayed health and max health on TFC's scale (HealthScale.shownHealth at a health modifier). */
class HealthDisplayTest {
    private static final HealthConfig CFG = HealthConfig.DEFAULT;

    @Test
    void fullHealthAtAverageNutritionShowsEightHundredFifty() {
        HealthDisplay d = HealthDisplay.atAverageNutrition(20, 20, CFG);
        assertThat(d.maxHealth()).isCloseTo(850f, within(0.01f));
        assertThat(d.health()).isCloseTo(850f, within(0.01f));
    }

    @Test
    void halfHealthScalesWithTheSameMaximum() {
        HealthDisplay d = HealthDisplay.of(10, 20, 0.85f, CFG);
        assertThat(d.health()).isCloseTo(425f, within(0.01f));
        assertThat(d.maxHealth()).isCloseTo(850f, within(0.01f));
    }

    @Test
    void atAverageNutritionUsesTheModifierOfTheAverage() {
        HealthDisplay d = HealthDisplay.atAverageNutrition(15, 20, CFG);
        assertThat(d).isEqualTo(HealthDisplay.of(15, 20, 0.85f, CFG));
    }

    @Test
    void shownHealthNeverExceedsTheShownMax() {
        HealthDisplay d = HealthDisplay.of(25, 20, 1f, CFG);
        assertThat(d.health()).isEqualTo(d.maxHealth());
        assertThat(d.maxHealth()).isCloseTo(1000f, within(0.01f));
    }

    @Test
    void zeroHealthShowsZero() {
        assertThat(HealthDisplay.atAverageNutrition(0, 20, CFG).health()).isZero();
    }

    @Test
    void rejectsInvalidVanillaValues() {
        assertThatThrownBy(() -> HealthDisplay.of(10, 0, 0.85f, CFG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HealthDisplay.of(-1, 20, 0.85f, CFG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HealthDisplay.of(Float.NaN, 20, 0.85f, CFG)).isInstanceOf(IllegalArgumentException.class);
    }
}
