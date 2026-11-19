/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.shield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ShieldRulesTest {
    @Test
    void clampedMapEnds() {
        assertThat(ShieldRules.damageBlocked(0f)).isEqualTo(0.25f);
        assertThat(ShieldRules.damageBlocked(12f)).isEqualTo(1f);
        assertThat(ShieldRules.damageBlocked(30f)).isEqualTo(1f);
        assertThat(ShieldRules.damageBlocked(6f)).isCloseTo(0.625f, within(1e-6f));
    }

    @Test
    void metalFactors() {
        assertThat(ShieldRules.factorForMetal("copper")).isCloseTo(0.25f + 0.75f * 3.25f / 12f, within(1e-6f));
        assertThat(ShieldRules.factorForMetal("blue_steel")).isCloseTo(0.8125f, within(1e-6f));
        assertThat(ShieldRules.factorForMetal("unknown")).isEqualTo(0.25f);
    }
}
