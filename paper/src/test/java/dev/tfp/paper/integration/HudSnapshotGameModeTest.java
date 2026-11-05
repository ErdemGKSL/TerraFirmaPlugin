/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weather.PrecipitationType;
import org.junit.jupiter.api.Test;

/** PAR-HUD-12: the needs-active flag of the snapshot. */
class HudSnapshotGameModeTest {
    @Test
    void defaultAndShortConstructorsHaveNeeds() {
        assertThat(HudSnapshot.DEFAULT.needsActive()).isTrue();
        assertThat(new HudSnapshot(1, 2, 3, 4, 5).needsActive()).isTrue();
    }

    @Test
    void everyWitherKeepsTheFlag() {
        HudSnapshot off = HudSnapshot.DEFAULT.withNeedsActive(false);
        assertThat(off.needsActive()).isFalse();
        assertThat(off.withThirst(1).needsActive()).isFalse();
        assertThat(off.withHealth(1, 2).needsActive()).isFalse();
        assertThat(off.withHunger(1).needsActive()).isFalse();
        assertThat(off.withHeldTemperature(900).needsActive()).isFalse();
        assertThat(off.withHeldHeat(HudSnapshot.HeldHeat.NONE).needsActive()).isFalse();
        assertThat(off.withAmbientTemperature(10).needsActive()).isFalse();
        assertThat(off.withPrecipitation(PrecipitationType.RAIN).needsActive()).isFalse();
        assertThat(off.withAir(10).needsActive()).isFalse();
        assertThat(off.withArmor(10).needsActive()).isFalse();
        assertThat(off.withNeedsActive(true)).isEqualTo(HudSnapshot.DEFAULT);
    }

    /** The flag changes nothing else: the stored values come back as they were on a switch back. */
    @Test
    void theFlagKeepsTheValues() {
        HudSnapshot s = HudSnapshot.DEFAULT.withThirst(42).withHeldTemperature(900);
        HudSnapshot off = s.withNeedsActive(false);
        assertThat(off.thirst()).isEqualTo(42);
        assertThat(off.heldTemperature()).isEqualTo(900);
        assertThat(off.maxHealth()).isEqualTo(s.maxHealth());
    }
}
