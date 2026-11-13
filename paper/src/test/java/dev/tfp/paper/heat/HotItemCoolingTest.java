/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.heat.HotItemCooling.At;
import dev.tfp.paper.heat.HotItemCooling.Below;
import dev.tfp.paper.heat.HotItemCooling.Change;
import dev.tfp.paper.heat.HotItemCooling.Column;
import dev.tfp.paper.heat.HotItemCooling.Expect;
import dev.tfp.paper.heat.HotItemCooling.Outcome;
import dev.tfp.paper.heat.HotItemCooling.Result;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-03: golden values of TFC {@code onItemExpire} replayed lazily. */
class HotItemCoolingTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;
    /** TFC data/tfc/tfc/item_heat/wrought_iron/ingot.json heat_capacity. */
    private static final float INGOT = 2.857143f;

    /** A random that always misses (no melt, no evaporation). */
    private static final RandomGenerator NEVER = () -> Long.MAX_VALUE;

    private static RandomGenerator always() {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public float nextFloat() {
                return 0f;
            }
        };
    }

    /** TFC stepped: every 10 ticks getTemperature (passive cooling since the last set) then setTemperature(t - cool). */
    private static float tfcStepped(float t, float capacity, int checks, float cool) {
        HeatState s = new HeatState(t, 0);
        for (int i = 1; i <= checks; i++) {
            long tick = i * 10L;
            float now = s.temperatureAt(tick, capacity, CFG);
            s = s.set(Math.max(0f, now - cool), tick);
        }
        return s.temperature();
    }

    @Test
    void ingotIn900WaterFiveSecondsGolden() {
        Outcome out = HotItemCooling.replay(900f, INGOT, 100, Column.of(At.WATER_BLOCK, Below.OTHER), CFG, NEVER);
        assertThat(out.checks()).isEqualTo(10);
        assertThat(out.ticksConsumed()).isEqualTo(100);
        // 10 x (10 x 0.8 / 2.857143 = 2.8 passive + 50 water) = 528
        assertThat(out.temperature()).isCloseTo(372f, within(0.01f));
        assertThat(out.temperature()).isCloseTo(tfcStepped(900f, INGOT, 10, 50f), within(0.001f));
        assertThat(out.surfaceCooled()).isTrue();
        assertThat(out.changes()).isEmpty();
        float air = new HeatState(900f, 0).temperatureAt(100, INGOT, CFG);
        assertThat(out.temperature()).isLessThan(air);
    }

    @Test
    void surfaceAmountsMatchTfc() {
        assertThat(HotItemCooling.coolAmount(Column.of(At.WATER_BLOCK, Below.OTHER))).isEqualTo(50f);
        assertThat(HotItemCooling.coolAmount(Column.of(At.WATERLOGGED, Below.PACKED_ICE))).isEqualTo(50f);
        assertThat(HotItemCooling.coolAmount(new Column(At.SNOW, 3, Below.OTHER))).isEqualTo(70f);
        assertThat(HotItemCooling.coolAmount(Column.of(At.OTHER, Below.SNOW_BLOCK))).isEqualTo(75f);
        assertThat(HotItemCooling.coolAmount(Column.of(At.OTHER, Below.ICE))).isEqualTo(100f);
        assertThat(HotItemCooling.coolAmount(Column.of(At.OTHER, Below.SEA_ICE))).isEqualTo(100f);
        assertThat(HotItemCooling.coolAmount(Column.of(At.OTHER, Below.PACKED_ICE))).isEqualTo(125f);
        assertThat(HotItemCooling.coolAmount(Column.of(At.OTHER, Below.OTHER))).isZero();
    }

    @Test
    void eachSurfaceMatchesSteppedTfc() {
        Object[][] rows = {
            {Column.of(At.OTHER, Below.SNOW_BLOCK), 75f},
            {Column.of(At.OTHER, Below.ICE), 100f},
            {Column.of(At.OTHER, Below.PACKED_ICE), 125f},
            {new Column(At.SNOW, 8, Below.OTHER), 70f},
        };
        for (Object[] row : rows) {
            Outcome out = HotItemCooling.replay(1200f, INGOT, 75, (Column) row[0], CFG, NEVER);
            assertThat(out.checks()).isEqualTo(7);
            assertThat(out.temperature()).isCloseTo(tfcStepped(1200f, INGOT, 7, (float) row[1]), within(0.001f));
        }
    }

    @Test
    void inAirOnlyPassiveCoolingAndNoSurface() {
        Outcome out = HotItemCooling.replay(900f, INGOT, 100, Column.of(At.OTHER, Below.OTHER), CFG, always());
        assertThat(out.temperature()).isCloseTo(new HeatState(900f, 0).temperatureAt(100, INGOT, CFG), within(0.01f));
        assertThat(out.surfaceCooled()).isFalse();
        assertThat(out.changes()).isEmpty();
    }

    @Test
    void stopsAtZeroAndReportsTheCheck() {
        Outcome out = HotItemCooling.replay(120f, INGOT, 1000, Column.of(At.OTHER, Below.PACKED_ICE), CFG, NEVER);
        // check 1: 120 - 2.8 - 125 < 0
        assertThat(out.temperature()).isZero();
        assertThat(out.checks()).isEqualTo(1);
        assertThat(out.zeroAtCheck()).isEqualTo(1);
    }

    @Test
    void partialChecksAreNotReplayed() {
        Outcome out = HotItemCooling.replay(500f, INGOT, 9, Column.of(At.WATER_BLOCK, Below.OTHER), CFG, NEVER);
        assertThat(out.checks()).isZero();
        assertThat(out.temperature()).isEqualTo(500f);
    }

    @Test
    void snowLayersMeltOnePerHitAndThenTheItemStopsCooling() {
        // every draw hits: 3 layers -> 2 -> 1 -> air, then nothing below
        Outcome out = HotItemCooling.replay(1500f, INGOT, 100, new Column(At.SNOW, 3, Below.OTHER), CFG, always());
        assertThat(out.changes()).containsExactly(new Change(0, Expect.SNOW, Result.AIR, 0));
        assertThat(out.temperature()).isCloseTo(1500f - 3 * 70f - 10 * 2.8f, within(0.01f));
    }

    @Test
    void iceMeltsIntoWaterAndTheItemKeepsCoolingInIt() {
        Outcome out = HotItemCooling.replay(1500f, INGOT, 30, Column.of(At.OTHER, Below.ICE), CFG, always());
        // check 1: ice 100 then melts; checks 2, 3: water 50 each; the new water evaporates on the first water draw
        assertThat(out.temperature()).isCloseTo(1500f - 100f - 50f - 0f - 3 * 2.8f, within(0.01f));
        assertThat(out.changes()).containsExactly(new Change(-1, Expect.ICE, Result.AIR, 0));
    }

    @Test
    void waterloggedNeverEvaporates() {
        Outcome out = HotItemCooling.replay(1500f, INGOT, 50, Column.of(At.WATERLOGGED, Below.OTHER), CFG, always());
        assertThat(out.changes()).isEmpty();
        assertThat(out.temperature()).isCloseTo(1500f - 5 * 52.8f, within(0.01f));
    }

    @Test
    void snowBlockBelowIsDestroyed() {
        Outcome out = HotItemCooling.replay(1500f, INGOT, 20, Column.of(At.OTHER, Below.SNOW_BLOCK), CFG, always());
        assertThat(out.changes()).containsExactly(new Change(-1, Expect.SNOW_BLOCK, Result.AIR, 0));
        assertThat(out.temperature()).isCloseTo(1500f - 75f - 2 * 2.8f, within(0.01f));
    }
}
