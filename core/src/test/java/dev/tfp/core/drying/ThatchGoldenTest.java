/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/ThatchBlock.java @ c3b4d9536 (slowEntityFactor, getLightBlock)
 */
package dev.tfp.core.drying;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Golden values for TFC's thatch rules at c3b4d9536: {@code slowEntityFactor} is {@code thatchMovementMultiplier} as a float
 * when the block holds no fluid, and {@code NO_SLOW} (1f) otherwise. Rows are {@code multiplier, fluidPresent, factor}, with
 * the factor from a float32 cast of the TFC expression (scratch script {@code scratchpad/m3pot4/gen_drying.py}, not committed).
 */
class ThatchGoldenTest {
    private static final Object[][] ROWS = {
        {0.0, false, 0.0f},
        {0.0, true, 1.0f},
        {0.25, false, 0.25f},
        {0.25, true, 1.0f},
        {0.6, false, 0.6000000238418579f},
        {0.6, true, 1.0f},
        {0.75, false, 0.75f},
        {0.75, true, 1.0f},
        {1.0, false, 1.0f},
        {1.0, true, 1.0f},
    };

    @Test
    void slowFactorMatchesTfcGolden() {
        assertThat(ROWS.length).isEqualTo(10);
        for (Object[] r : ROWS) {
            float factor = ThatchRules.slowFactor((boolean) r[1], (double) r[0]);
            assertThat(factor).as("multiplier %s fluid %s", r[0], r[1]).isEqualTo((float) r[2]);
        }
    }

    @Test
    void lightAndDefaults() {
        assertThat(ThatchRules.LIGHT_BLOCK).isEqualTo(15);
        assertThat(ThatchRules.NO_SLOW).isEqualTo(1f);
        assertThat(ThatchRules.slowFactor(false, ThatchRules.DEFAULT_MOVEMENT_MULTIPLIER)).isEqualTo(0.6f);
    }

    @Test
    void rejectsAMultiplierOutsideTheConfigRange() {
        assertThatThrownBy(() -> ThatchRules.slowFactor(false, 1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThatchRules.slowFactor(false, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
