/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut),
 *       src/generated/resources/data/tfc/tfc/lamp_fuel/canola_oil.json @ c3b4d9536 (burn_rate 6000),
 *       src/generated/resources/data/tfc/tfc/lamp_fuel/olive_oil.json @ c3b4d9536 (burn_rate 7200),
 *       src/generated/resources/data/tfc/tfc/lamp_fuel/tallow.json @ c3b4d9536 (burn_rate 2400)
 */
package dev.tfp.core.light;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-04 golden values for TFC's lamp fuel rule at c3b4d9536: one {@code checkHasRanOut} call on a lit lamp. Rows are
 * {@code ticks, burnRate, tankMb, usage, drained, tankAfter, lit, reset}, produced by a transcription of TFC's
 * {@code Mth.floor(ticks / burnRate)} drain (scratch script, not committed) and pasted here.
 */
class LampGoldenTest {
    private static final Object[][] CHECKS = {
        {0L, 6000, 250, 0L, 0, 250, true, false},
        {5999L, 6000, 250, 0L, 0, 250, true, false},
        {6000L, 6000, 250, 1L, 1, 249, true, true},
        {11999L, 6000, 250, 1L, 1, 249, true, true},
        {12000L, 6000, 250, 2L, 2, 248, true, true},
        {1_500_000L, 6000, 250, 250L, 250, 0, false, true},
        {1_500_001L, 6000, 250, 250L, 250, 0, false, true},
        {6000L, 6000, 1, 1L, 1, 0, false, true},
        {5999L, 6000, 1, 0L, 0, 1, true, false},
        {12000L, 6000, 1, 2L, 1, 0, false, true},
        {6000L, 6000, 2, 1L, 1, 1, true, true},
        {18000L, 6000, 2, 3L, 2, 0, false, true},
        {7200L, 7200, 3, 1L, 1, 2, true, true},
        {14400L, 7200, 3, 2L, 2, 1, true, true},
        {2400L, 2400, 1, 1L, 1, 0, false, true},
        {100L, 2400, 0, 0L, 0, 0, true, false},
        {100L, 1, 5, 100L, 5, 0, false, true},
        {2999L, 1000, 5, 2L, 2, 3, true, true},
    };

    @Test
    void checkMatchesTfcGolden() {
        assertThat(CHECKS.length).isEqualTo(18);
        for (Object[] r : CHECKS) {
            LampRules.Check c = LampRules.check((long) r[0], (int) r[1], (int) r[2], true);
            assertThat(c.usage()).as("usage %s", java.util.Arrays.toString(r)).isEqualTo((long) r[3]);
            assertThat(c.drained()).isEqualTo((int) r[4]);
            assertThat(c.tankAfter()).isEqualTo((int) r[5]);
            assertThat(c.lit()).isEqualTo((boolean) r[6]);
            assertThat(c.reset()).isEqualTo((boolean) r[7]);
        }
    }

    @Test
    void lavaNeverRunsOutAndUnlitLampIsUntouched() {
        LampRules.Check lava = LampRules.check(10_000_000L, LampRules.LAVA.burnRate(), 250, true);
        assertThat(lava.drained()).isZero();
        assertThat(lava.tankAfter()).isEqualTo(250);
        assertThat(lava.lit()).isTrue();
        assertThat(lava.reset()).isFalse();

        LampRules.Check unlit = LampRules.check(100_000L, LampRules.CANOLA_OIL.burnRate(), 250, false);
        assertThat(unlit.lit()).isFalse();
        assertThat(unlit.drained()).isZero();
        assertThat(unlit.reset()).isFalse();
    }

    @Test
    void fuelDurationsMatchTfcGolden() {
        // lamp_fuel burn_rate x 250 mB (DEFAULT_CAPACITY_MB)
        assertThat(LampRules.DEFAULT_CAPACITY_MB).isEqualTo(250);
        assertThat(LampRules.durationTicks(LampRules.CANOLA_OIL.burnRate(), 250)).isEqualTo(1_500_000L);
        assertThat(LampRules.durationTicks(LampRules.OLIVE_OIL.burnRate(), 250)).isEqualTo(1_800_000L);
        assertThat(LampRules.durationTicks(LampRules.TALLOW.burnRate(), 250)).isEqualTo(600_000L);
        assertThat(LampRules.durationTicks(LampRules.LAVA.burnRate(), 250)).isEqualTo(LightSourceRules.NEVER);
        assertThat(LampRules.lampLight(true)).isEqualTo(15);
        assertThat(LampRules.lampLight(false)).isZero();
    }
}
