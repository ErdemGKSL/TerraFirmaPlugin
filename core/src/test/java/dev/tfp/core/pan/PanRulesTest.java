/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PanRulesTest {
    @Test
    void offHandPassesEvenInWater() {
        assertThat(PanRules.start(true, true)).isEqualTo(PanRules.Start.PASS);
        assertThat(PanRules.start(true, false)).isEqualTo(PanRules.Start.PASS);
    }

    @Test
    void mainHandNeedsInfiniteWater() {
        assertThat(PanRules.start(false, false)).isEqualTo(PanRules.Start.NEEDS_WATER);
        assertThat(PanRules.start(false, true)).isEqualTo(PanRules.Start.STARTED);
    }

    @Test
    void onlyAFilledPanYieldsLoot() {
        assertThat(PanRules.yieldsLoot(true)).isTrue();
        assertThat(PanRules.yieldsLoot(false)).isFalse();
    }

    @Test
    void emptyPanFillsOnlyFromADepositBlock() {
        assertThat(PanRules.canFill(true)).isTrue();
        assertThat(PanRules.canFill(false)).isFalse();
    }

    @Test
    void constantsMatchTfc() {
        assertThat(PanRules.USE_TICKS).isEqualTo(120);
        assertThat(PanRules.SOUND_INTERVAL).isEqualTo(16);
    }
}
