/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HudGaugeTest {

    @Test
    void clampKeepsValuesInsideZeroAndMax() {
        assertThat(HudGauge.clamp(-5, 50)).isEqualTo(0);
        assertThat(HudGauge.clamp(0, 50)).isEqualTo(0);
        assertThat(HudGauge.clamp(25, 50)).isEqualTo(25);
        assertThat(HudGauge.clamp(50, 50)).isEqualTo(50);
        assertThat(HudGauge.clamp(80, 50)).isEqualTo(50);
    }

    @Test
    void clampGivesZeroForBadInputs() {
        assertThat(HudGauge.clamp(Double.NaN, 50)).isEqualTo(0);
        assertThat(HudGauge.clamp(10, 0)).isEqualTo(0);
        assertThat(HudGauge.clamp(10, -1)).isEqualTo(0);
        assertThat(HudGauge.clamp(10, Double.NaN)).isEqualTo(0);
    }

    @Test
    void fractionIsZeroMidAndFull() {
        assertThat(HudGauge.fraction(0, 50)).isEqualTo(0.0);
        assertThat(HudGauge.fraction(25, 50)).isEqualTo(0.5);
        assertThat(HudGauge.fraction(50, 50)).isEqualTo(1.0);
    }

    @Test
    void fractionIsClampedAtTheEnds() {
        assertThat(HudGauge.fraction(-10, 50)).isEqualTo(0.0);
        assertThat(HudGauge.fraction(70, 50)).isEqualTo(1.0);
        assertThat(HudGauge.fraction(10, 0)).isEqualTo(0.0);
    }

    @Test
    void pairRoundsHalfUpAndClampsTheCurrent() {
        assertThat(HudGauge.pair(45, 50)).isEqualTo("45/50");
        assertThat(HudGauge.pair(37.5, 50)).isEqualTo("38/50");
        assertThat(HudGauge.pair(37.4, 50)).isEqualTo("37/50");
        assertThat(HudGauge.pair(0, 50)).isEqualTo("0/50");
        assertThat(HudGauge.pair(60, 50)).isEqualTo("50/50");
        assertThat(HudGauge.pair(-3, 50)).isEqualTo("0/50");
    }

    @Test
    void pairOfNoMaxIsZeroOverZero() {
        assertThat(HudGauge.pair(10, 0)).isEqualTo("0/0");
    }
}
