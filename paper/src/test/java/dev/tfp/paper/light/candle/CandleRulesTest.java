/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light.candle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.light.LightSourceRules;
import dev.tfp.paper.light.candle.CandleRules.Family;
import dev.tfp.paper.light.candle.CandleRules.Use;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-11: the pure candle rules against TFC's values at c3b4d9536, and lazy against stepped random ticks. */
class CandleRulesTest {
    @Test
    void tfcDefaultsAndIds() {
        assertThat(CandleRules.CANDLE_TICKS).isEqualTo(288_000);
        assertThat(CandleRules.JACK_O_LANTERN_TICKS).isEqualTo(108_000);
        assertThat(CandleRules.candles()).hasSize(17).startsWith("tfp:candle", "tfp:candle/white").endsWith("tfp:candle/black");
        assertThat(CandleRules.family("tfp:candle/light_gray")).contains(Family.CANDLE);
        assertThat(CandleRules.family("tfp:candle_cake/cyan")).contains(Family.CANDLE_CAKE);
        assertThat(CandleRules.family("tfp:jack_o_lantern")).contains(Family.JACK_O_LANTERN);
        assertThat(CandleRules.family("tfp:candle/teal")).isEmpty();
        assertThat(CandleRules.candleCakeOf("tfp:candle/red")).contains("tfp:candle_cake/red");
        assertThat(CandleRules.candleCakeOf("tfp:candle")).contains("tfp:candle_cake");
        assertThat(CandleRules.candleOf("tfp:candle_cake/red")).contains("tfp:candle/red");
        assertThat(CandleRules.candleCakeOf("tfp:torch")).isEmpty();
    }

    @Test
    void lazyBurnoutMatchesSteppedRandomTicks() {
        // TFC: the first random tick with ticksSinceUpdate > limit puts it out; lazily the first such tick is due
        for (int limit : new int[] {1, 100, CandleRules.CANDLE_TICKS}) {
            long lit = 5_000;
            long due = CandleRules.burnoutTick(lit, limit);
            long stepped = lit;
            while (!CandleRules.burntOut(lit, stepped, limit)) {
                stepped++;
            }
            assertThat(due).isEqualTo(stepped);
        }
        assertThat(CandleRules.burnoutTick(0, -1)).isEqualTo(LightSourceRules.NEVER);
        assertThat(CandleRules.burnsOut(Family.CANDLE, false, 0, 1_000_000, 10, 10)).isFalse();
        assertThat(CandleRules.burnsOut(Family.JACK_O_LANTERN, false, 0, 11, 1_000, 10)).isTrue();
        assertThat(CandleRules.burnsOut(Family.CANDLE_CAKE, true, CandleRules.NEVER_LIT, 0, 288_000, 1)).isTrue();
    }

    @Test
    void useTable() {
        assertThat(CandleRules.use(Family.CANDLE, true, false, true, true, 0)).isEqualTo(Use.EXTINGUISH);
        assertThat(CandleRules.use(Family.CANDLE, true, true, true, true, 0)).isEqualTo(Use.PASS);
        assertThat(CandleRules.use(Family.CANDLE, false, false, true, true, 0)).isEqualTo(Use.PASS);
        assertThat(CandleRules.use(Family.CANDLE, true, false, false, true, 0)).isEqualTo(Use.PASS);
        assertThat(CandleRules.use(Family.CANDLE, true, false, true, false, 0)).isEqualTo(Use.PASS);
        assertThat(CandleRules.use(Family.CANDLE_CAKE, true, false, true, true, 0.6)).isEqualTo(Use.EXTINGUISH);
        assertThat(CandleRules.use(Family.CANDLE_CAKE, true, false, true, true, 0.5)).isEqualTo(Use.EAT);
        assertThat(CandleRules.use(Family.CANDLE_CAKE, false, false, true, true, 0.9)).isEqualTo(Use.EAT);
        assertThat(CandleRules.use(Family.CANDLE_CAKE, true, false, false, true, 0.9)).isEqualTo(Use.EAT);
        assertThat(CandleRules.use(Family.CANDLE_CAKE, true, true, true, true, 0.9)).isEqualTo(Use.PASS);
        assertThat(CandleRules.use(Family.JACK_O_LANTERN, true, false, true, true, 0.9)).isEqualTo(Use.PASS);
    }

    @Test
    void lightingDousingLightAndCarving() {
        assertThat(CandleRules.lights(Family.CANDLE, true)).isFalse();
        assertThat(CandleRules.lights(Family.CANDLE, false)).isTrue();
        assertThat(CandleRules.lights(Family.JACK_O_LANTERN, true)).isTrue();
        // vanilla CandleBlock.placeLiquid: water puts a lit candle out; cakes and the jack-o'-lantern hold no water
        assertThat(CandleRules.drowned(Family.CANDLE, true, true)).isTrue();
        assertThat(CandleRules.drowned(Family.CANDLE, false, true)).isFalse();
        assertThat(CandleRules.drowned(Family.CANDLE, true, false)).isFalse();
        assertThat(CandleRules.drowned(Family.CANDLE_CAKE, true, true)).isFalse();
        assertThat(CandleRules.drowned(Family.JACK_O_LANTERN, true, true)).isFalse();
        assertThat(CandleRules.douses(Family.CANDLE, false)).isFalse();
        assertThat(CandleRules.douses(Family.JACK_O_LANTERN, false)).isTrue();
        assertThat(CandleRules.light(Family.CANDLE, 4, true)).isEqualTo(14);
        assertThat(CandleRules.light(Family.CANDLE_CAKE, 1, true)).isEqualTo(3);
        assertThat(CandleRules.light(Family.JACK_O_LANTERN, 1, true)).isEqualTo(15);
        assertThat(CandleRules.carvedFacing("east", "north")).isEqualTo("east");
        assertThat(CandleRules.carvedFacing("up", "north")).isEqualTo("south");
        assertThat(CandleRules.carvedFacing("down", "west")).isEqualTo("east");
        assertThat(CandleRules.takesCandle(0)).isTrue();
        assertThat(CandleRules.takesCandle(1)).isFalse();
    }
}
