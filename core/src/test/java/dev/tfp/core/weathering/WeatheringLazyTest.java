/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/IWeatheringBlock.java @ c3b4d9536 (onRandomTick)
 */
package dev.tfp.core.weathering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.metal.Metal;
import dev.tfp.core.weathering.WeatheringRules.Age;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Unit and golden tests for the lazy metal weathering ({@link WeatheringRules}). The expected values are worked by hand from
 * TFC's {@code onRandomTick} at c3b4d9536: the gate {@code 0.05 * (1 - resistance)}, the age modifier, the slow path, and the
 * vanilla random tick rate {@code randomTickSpeed / 4096} (speed 3 by default).
 */
class WeatheringLazyTest {
    private static final double LN2 = Math.log(2);

    @Test
    void ageModifierAndGateFollowTfc() {
        assertThat(WeatheringRules.ageModifier(Age.NONE)).isEqualTo(0.5);
        assertThat(WeatheringRules.ageModifier(Age.EXPOSED)).isEqualTo(1.0);
        assertThat(WeatheringRules.ageModifier(Age.WEATHERED)).isEqualTo(1.0);
        assertThat(WeatheringRules.ageModifier(Age.OXIDIZED)).isEqualTo(1.0);
        assertThat(WeatheringRules.gateChance(0f)).isCloseTo(0.05, within(1e-12));
        assertThat(WeatheringRules.gateChance(0.9f)).isCloseTo(0.005, within(1e-6));
        assertThat(WeatheringRules.gateChance(0.95f)).isCloseTo(0.0025, within(1e-6));
        assertThat(WeatheringRules.gateChance(1f)).isZero();
        assertThat(WeatheringRules.gateChance(-1f)).isZero();
    }

    @Test
    void weatheringMetalsAreExactlyThoseWithAResistance() {
        for (Metal metal : Metal.values()) {
            float resistance = metal.weatheringResistance();
            assertThat(WeatheringRules.weathers(resistance)).as(metal.name()).isEqualTo(metal.weathers());
            if (metal.weathers()) {
                assertThat(WeatheringRules.gateChance(resistance)).as(metal.name())
                    .isCloseTo(0.05 * (1 - resistance), within(1e-9));
            } else {
                assertThat(WeatheringRules.gateChance(resistance)).as(metal.name()).isZero();
            }
        }
    }

    @Test
    void nonWeatheringMetalsNeverAdvance() {
        Random random = new Random(0x4e0L);
        for (Metal metal : Metal.values()) {
            if (metal.weathers()) {
                continue;
            }
            for (int trial = 0; trial < 20; trial++) {
                double[] draws = {random.nextDouble(), random.nextDouble(), random.nextDouble()};
                assertThat(WeatheringRules.catchUp(Age.NONE, 1_000_000_000L, metal.weatheringResistance(), 3, 0.5, draws))
                    .as(metal.name()).isEqualTo(Age.NONE);
            }
        }
    }

    @Test
    void copperSlowPathGoldenRate() {
        // copper resistance 0: gate 0.05; NONE slow path 0.5 * 0.05 = 0.025; rate = 3 / 4096 * 0.05 * 0.025
        double rate = WeatheringRules.rate(Age.NONE, 0f, 3, 0.0);
        assertThat(rate).isCloseTo(9.1552734375e-7, within(1e-15));
        // the first advance takes 1 / rate ticks on average, so the median is ln 2 / rate
        double median = LN2 / rate;
        assertThat(median).isCloseTo(757_101.5, within(1.0));
        double[] half = {0.5, 0.5, 0.5};
        assertThat(WeatheringRules.catchUp(Age.NONE, 750_000L, 0f, 3, 0.0, half)).isEqualTo(Age.NONE);
        assertThat(WeatheringRules.catchUp(Age.NONE, 765_000L, 0f, 3, 0.0, half)).isEqualTo(Age.EXPOSED);
    }

    @Test
    void extraChanceReplacesTheSlowPathWhenLarger() {
        // NONE, copper, extra 0.5: advance chance 0.5, rate = 3 / 4096 * 0.05 * 0.5 = 1.8310546875e-5, median ln 2 / rate
        assertThat(WeatheringRules.advanceChance(Age.NONE, 0.5)).isEqualTo(0.5);
        assertThat(WeatheringRules.advanceChance(Age.EXPOSED, 0.01)).isEqualTo(0.05);
        assertThat(WeatheringRules.advanceChance(Age.OXIDIZED, 0.9)).isZero();
        double[] half = {0.5, 0.5, 0.5};
        assertThat(WeatheringRules.catchUp(Age.NONE, 30_000L, 0f, 3, 0.5, half)).isEqualTo(Age.NONE);
        assertThat(WeatheringRules.catchUp(Age.NONE, 40_000L, 0f, 3, 0.5, half)).isEqualTo(Age.EXPOSED);
    }

    @Test
    void zeroElapsedNeverAdvancesEvenAtTheFastestDraw() {
        double[] fastest = {0.0, 0.0, 0.0};
        for (Age age : Age.values()) {
            assertThat(WeatheringRules.catchUp(age, 0L, 0.5f, 3, 1.0, fastest)).isEqualTo(age);
        }
    }

    @Test
    void oxidizedIsAbsorbingAndTheChainStopsThere() {
        assertThat(Age.OXIDIZED.next()).isNull();
        assertThat(Age.NONE.next()).isEqualTo(Age.EXPOSED);
        assertThat(WeatheringRules.catchUp(Age.OXIDIZED, Long.MAX_VALUE, 0f, 3, 1.0, new double[]{0.0, 0.0, 0.0}))
            .isEqualTo(Age.OXIDIZED);
        // the fastest draws from NONE walk three stages, and no further
        assertThat(WeatheringRules.catchUp(Age.NONE, 1L, 0f, 3, 1.0, new double[]{0.0, 0.0, 0.0})).isEqualTo(Age.OXIDIZED);
    }

    @Test
    void monotoneInElapsedTimeForFixedDraws() {
        Random random = new Random(0x7e1L);
        for (int trial = 0; trial < 200; trial++) {
            float resistance = random.nextFloat();
            double extra = random.nextBoolean() ? 0.0 : random.nextDouble();
            double[] draws = {random.nextDouble(), random.nextDouble(), random.nextDouble()};
            Age previous = Age.NONE;
            for (long elapsed = 0; elapsed <= 5_000_000L; elapsed += 37_000L) {
                Age stage = WeatheringRules.catchUp(Age.NONE, elapsed, resistance, 3, extra, draws);
                assertThat(stage.ordinal()).isGreaterThanOrEqualTo(previous.ordinal());
                previous = stage;
            }
        }
    }

    @Test
    void largerDrawsGiveStagesThatAreNoLater() {
        Random random = new Random(0x2d5L);
        for (int trial = 0; trial < 200; trial++) {
            float resistance = random.nextFloat() * 0.99f;
            long elapsed = random.nextInt(3_000_000);
            double low = random.nextDouble() * 0.5;
            double high = low + random.nextDouble() * (1 - low) * 0.999;
            Age early = WeatheringRules.catchUp(Age.NONE, elapsed, resistance, 3, 0.2, new double[]{low, low, low});
            Age late = WeatheringRules.catchUp(Age.NONE, elapsed, resistance, 3, 0.2, new double[]{high, high, high});
            assertThat(late.ordinal()).isLessThanOrEqualTo(early.ordinal());
        }
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> WeatheringRules.catchUp(Age.NONE, -1L, 0f, 3, 0.0, new double[]{0.5, 0.5, 0.5}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeatheringRules.catchUp(Age.NONE, 1L, 0f, 3, 0.0, new double[]{0.5, 0.5}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeatheringRules.catchUp(Age.NONE, 1L, 0f, 3, 0.0, new double[]{1.0, 0.5, 0.5}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeatheringRules.rate(Age.NONE, 0f, -1, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
