/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (waterTick, onCalendarUpdate)
 */
package dev.tfp.core.farm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.farm.FarmlandRules.SoilHydration;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Lazy evaluation must give the same answer as stepping. The water reserve is one update over a gap, or many smaller updates over
 * the same gap; the moisture is one evaluation at any instant, with no state carried between reads.
 */
class FarmlandLazyVsSteppedTest {
    /** Float32 rounding of chunked sums is well below one reserve unit; the gap is far from the cap in these cases. */
    private static final float TOLERANCE = 1e-5f;

    @Test
    void steppedReserveMatchesOneLazyUpdateOverTheSameGap() {
        Random random = new Random(0x5EEDF00DL);
        for (int trial = 0; trial < 500; trial++) {
            long from = random.nextInt(1_000_000);
            long gap = random.nextInt(60_000);
            float start = random.nextFloat() * 4f; // starts below the cap so the gap is what moves it
            float lazy = FarmlandRules.additionalWater(start, from, from + gap);
            float stepped = start;
            long cursor = from;
            while (cursor < from + gap) {
                long step = Math.min(1 + random.nextInt(1000), from + gap - cursor);
                stepped = FarmlandRules.additionalWater(stepped, cursor, cursor + step);
                cursor += step;
            }
            assertThat(stepped).as("trial %d gap %d", trial, gap).isCloseTo(lazy, org.assertj.core.api.Assertions.within(TOLERANCE));
        }
    }

    @Test
    void reserveSaturatesAtTheCapThoughStepsAndLazyAgree() {
        // Once the cap is reached every further step keeps it there, so both forms land on exactly the cap.
        float lazy = FarmlandRules.additionalWater(0f, 0L, 10_000_000L);
        float stepped = 0f;
        for (long t = 0; t < 10_000_000L; t += 240_000L) {
            stepped = FarmlandRules.additionalWater(stepped, t, t + 240_000L);
        }
        assertThat(lazy).isEqualTo(FarmlandRules.MAX_ADDITIONAL_WATER);
        assertThat(stepped).isEqualTo(FarmlandRules.MAX_ADDITIONAL_WATER);
    }

    @Test
    void stateWaterTickOverAGapEqualsOneStepCalendarSpan() {
        FarmlandState state = FarmlandState.fresh(100L);
        // An 8000-tick gap adds 8 to the reserve, below the cap, so the comparison is not trivially saturated.
        FarmlandState lazy = state.waterTick(5_000L, 13_000L, 2_000L);
        FarmlandState stepped = state
            .waterTick(5_000L, 9_000L, 1_000L)
            .waterTick(9_000L, 13_000L, 2_000L);
        assertThat(lazy.additionalWater()).isCloseTo(8f, org.assertj.core.api.Assertions.within(TOLERANCE));
        assertThat(stepped.additionalWater()).isCloseTo(lazy.additionalWater(), org.assertj.core.api.Assertions.within(TOLERANCE));
        assertThat(lazy.lastWaterTick()).isEqualTo(2_000L);
        assertThat(stepped.lastWaterTick()).isEqualTo(2_000L);
    }

    @Test
    void momentaryMoistureIsAFunctionOfItsInputsOnly() {
        // Reading the moisture at one instant never depends on earlier reads: no state, so interleaving reads changes nothing.
        float[] rainfalls = {0f, 120f, 340f, 599f, 800f};
        int[] expectedFirst = new int[rainfalls.length];
        for (int i = 0; i < rainfalls.length; i++) {
            expectedFirst[i] = FarmlandRules.moisture(rainfalls[i], false, true, SoilHydration.NORMAL);
        }
        // A long unrelated sequence of reads, then the same reads again in reverse.
        for (int round = 0; round < 50; round++) {
            FarmlandRules.moisture(round * 13f, round % 2 == 0, false, SoilHydration.DECREASES);
        }
        for (int i = rainfalls.length - 1; i >= 0; i--) {
            assertThat(FarmlandRules.moisture(rainfalls[i], false, true, SoilHydration.NORMAL)).isEqualTo(expectedFirst[i]);
        }
    }
}
