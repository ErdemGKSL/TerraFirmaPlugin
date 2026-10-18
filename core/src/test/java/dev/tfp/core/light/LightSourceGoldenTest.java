/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (onRandomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java @ c3b4d9536 (onRandomTick, LIGHTING_SCALE)
 */
package dev.tfp.core.light;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-04 golden values from TFC's torch and candle arithmetic at c3b4d9536. The expected rows were produced by a
 * transcription of TFC's formulas (scratch script, not committed) and pasted here: burnt-out is {@code limit > 0 &&
 * elapsed > limit}, remaining is {@code limit - elapsed} clamped at 0, light levels are TFC's {@code LIGHTING_SCALE} and
 * the fixed torch, cake and lamp values.
 */
class LightSourceGoldenTest {
    /** elapsed ticks, limit, burnt out, remaining ticks ({@code Long.MAX_VALUE} = never). */
    private static final long[][] BURN = {
        {0L, 86400, 0, 86400L}, {1L, 86400, 0, 86399L}, {1199L, 86400, 0, 85201L}, {1200L, 86400, 0, 85200L},
        {1201L, 86400, 0, 85199L}, {86400L, 86400, 0, 0L}, {86401L, 86400, 1, 0L}, {288000L, 86400, 1, 0L},
        {288001L, 86400, 1, 0L},
        {0L, 1200, 0, 1200L}, {1L, 1200, 0, 1199L}, {1199L, 1200, 0, 1L}, {1200L, 1200, 0, 0L},
        {1201L, 1200, 1, 0L}, {86400L, 1200, 1, 0L}, {86401L, 1200, 1, 0L}, {288000L, 1200, 1, 0L},
        {288001L, 1200, 1, 0L},
        {0L, 1, 0, 1L}, {1L, 1, 0, 0L}, {1199L, 1, 1, 0L}, {1200L, 1, 1, 0L}, {1201L, 1, 1, 0L},
        {86400L, 1, 1, 0L}, {86401L, 1, 1, 0L}, {288000L, 1, 1, 0L}, {288001L, 1, 1, 0L},
        {0L, -1, 0, Long.MAX_VALUE}, {1L, -1, 0, Long.MAX_VALUE}, {1199L, -1, 0, Long.MAX_VALUE},
        {1200L, -1, 0, Long.MAX_VALUE}, {1201L, -1, 0, Long.MAX_VALUE}, {86400L, -1, 0, Long.MAX_VALUE},
        {86401L, -1, 0, Long.MAX_VALUE}, {288000L, -1, 0, Long.MAX_VALUE}, {288001L, -1, 0, Long.MAX_VALUE},
        {0L, 0, 0, Long.MAX_VALUE}, {1L, 0, 0, Long.MAX_VALUE}, {1199L, 0, 0, Long.MAX_VALUE},
        {1200L, 0, 0, Long.MAX_VALUE}, {1201L, 0, 0, Long.MAX_VALUE}, {86400L, 0, 0, Long.MAX_VALUE},
        {86401L, 0, 0, Long.MAX_VALUE}, {288000L, 0, 0, Long.MAX_VALUE}, {288001L, 0, 0, Long.MAX_VALUE},
        {0L, 288000, 0, 288000L}, {1L, 288000, 0, 287999L}, {1199L, 288000, 0, 286801L},
        {1200L, 288000, 0, 286800L}, {1201L, 288000, 0, 286799L}, {86400L, 288000, 0, 201600L},
        {86401L, 288000, 0, 201599L}, {288000L, 288000, 0, 0L}, {288001L, 288000, 1, 0L},
    };

    @Test
    void burnTimeMatchesTfcGolden() {
        assertThat(BURN.length).isEqualTo(54);
        for (long[] r : BURN) {
            boolean burnt = r[2] == 1;
            assertThat(LightSourceRules.burnedOut(r[0], (int) r[1])).as("burnedOut(%s, %s)", r[0], r[1]).isEqualTo(burnt);
            assertThat(LightSourceRules.remainingTicks(r[0], (int) r[1])).as("remaining(%s, %s)", r[0], r[1]).isEqualTo(r[3]);
        }
    }

    @Test
    void defaultsMatchTfcConfig() {
        assertThat(LightSourceRules.DEFAULT_TORCH_TICKS).isEqualTo(86_400);
        assertThat(LightSourceRules.DEFAULT_CANDLE_TICKS).isEqualTo(288_000);
    }

    @Test
    void candleLightPerStageMatchesTfcGolden() {
        // TFC LIGHTING_SCALE: lit = 3 * candles + 2, unlit = 0
        int[] lit = {5, 8, 11, 14};
        for (int candles = 1; candles <= 4; candles++) {
            assertThat(LightSourceRules.candleLight(candles, true)).isEqualTo(lit[candles - 1]);
            assertThat(LightSourceRules.candleLight(candles, false)).isZero();
        }
        assertThatThrownBy(() -> LightSourceRules.candleLight(0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LightSourceRules.candleLight(5, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixedLightLevelsMatchTfcGolden() {
        assertThat(LightSourceRules.torchLight(true)).isEqualTo(14);
        assertThat(LightSourceRules.torchLight(false)).isZero();
        assertThat(LightSourceRules.candleCakeLight(true)).isEqualTo(3);
        assertThat(LightSourceRules.candleCakeLight(false)).isZero();
    }
}
