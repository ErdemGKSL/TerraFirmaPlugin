/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (adjustTemp)
 */
package dev.tfp.core.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Item heat is never ticked. Cooling is {@code max(0, T - ticks x k / C)}, so a jump over a gap must equal any split of
 * the gap into steps, and a step-by-step simulation (one calendar tick at a time) must equal the direct value.
 *
 * <p><b>Tolerance.</b> The rules are exact in real arithmetic. In {@code float}, each step rounds its result to the
 * float grid, so a stepped run can drift from the direct value by at most half an ulp of the largest temperature per
 * step (the subtraction's rounding; the quotient's rounding is far smaller). The test bounds the drift by
 * {@code steps x ulp(1600)}, which is twice that worst case, so it is a proof bound and not a fitted value.
 */
class HeatLazyVsSteppedTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;
    private static final float[] CAPACITIES = {2.857143f, 5.714286f, 0.114285715f, 40f, 1.0f};

    @Test
    void steppingOneTickAtATimeMatchesTheDirectValue() {
        Random random = new Random(0x7fcL);
        for (int trial = 0; trial < 200; trial++) {
            float start = random.nextFloat() * HeatLevel.MAX_VISIBLE;
            float capacity = CAPACITIES[random.nextInt(CAPACITIES.length)];
            long ticks = 1 + random.nextInt(1200);

            float direct = new HeatState(start, 0L).temperatureAt(ticks, capacity, CFG);
            HeatState stepped = new HeatState(start, 0L);
            for (long now = 1; now <= ticks; now++) {
                stepped = stepped.advance(now, capacity, CFG);
            }
            float tolerance = ticks * Math.ulp(HeatLevel.MAX_VISIBLE);
            assertThat(stepped.temperatureAt(ticks, capacity, CFG))
                .as("start %s, capacity %s, %s ticks", start, capacity, ticks)
                .isCloseTo(direct, within(tolerance));
        }
    }

    @Test
    void anySplitOfAGapEqualsTheWholeGap() {
        Random random = new Random(0x7fdL);
        for (int trial = 0; trial < 200; trial++) {
            float start = random.nextFloat() * HeatLevel.MAX_VISIBLE;
            float capacity = CAPACITIES[random.nextInt(CAPACITIES.length)];
            long total = 1 + random.nextInt(24_000);
            long cut = random.nextInt((int) total + 1);

            float whole = new HeatState(start, 0L).temperatureAt(total, capacity, CFG);
            HeatState split = new HeatState(start, 0L).advance(cut, capacity, CFG);
            float tolerance = 2 * Math.ulp(HeatLevel.MAX_VISIBLE);
            assertThat(split.temperatureAt(total, capacity, CFG))
                .as("start %s, capacity %s, split %s of %s", start, capacity, cut, total)
                .isCloseTo(whole, within(tolerance));
        }
    }
}
