/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.thirst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.food.HungerState;
import dev.tfp.core.food.NutritionConfig;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Thirst is never ticked: one jump to a tick must equal any sequence of steps to the same tick, when each step
 * spends the passive exhaustion points of its own gap (the hunger state supplies the count, as in the glue).
 */
class ThirstLazyVsSteppedTest {
    private static final ThirstConfig CFG = ThirstConfig.DEFAULT;
    private static final NutritionConfig NUTRITION = NutritionConfig.DEFAULT;

    @Test
    void oneCalendarDayAtMildTemperatureIsEightPoints() {
        // At exactly 24000 ticks the 8th point is still pending (vanilla spends above 4, not at 4), so a day plus one
        // tick is the first instant with 8 points: 8 x 5 at 22 degrees (no heat share) is 40 thirst lost.
        assertThat(HungerState.fresh(0).pointsSpentTo(24_000, NUTRITION)).isEqualTo(7);
        long points = HungerState.fresh(0).pointsSpentTo(24_001, NUTRITION);
        assertThat(points).isEqualTo(8);
        ThirstState s = ThirstState.full(0, CFG).advance(24_001, points, 22f, CFG);
        assertThat(s.thirst()).isEqualTo(60f);
        assertThat(s.lastTick()).isEqualTo(24_001);
    }

    @Test
    void steppedGapsEqualOneJump() {
        for (long seed = 1; seed <= 20; seed++) {
            Random rnd = new Random(seed);
            long end = 200_000;
            float temp = 10f + rnd.nextFloat() * 30f;

            HungerState h = HungerState.fresh(0);
            ThirstState t = ThirstState.full(0, CFG);
            long now = 0;
            long steppedPoints = 0;
            while (now < end) {
                long next = Math.min(end, now + 1 + rnd.nextInt(5000));
                long p = h.pointsSpentTo(next, NUTRITION);
                steppedPoints += p;
                t = t.advance(next, p, temp, CFG);
                h = h.advance(next, NUTRITION);
                now = next;
            }

            long jumpPoints = HungerState.fresh(0).pointsSpentTo(end, NUTRITION);
            ThirstState jump = ThirstState.full(0, CFG).advance(end, jumpPoints, temp, CFG);

            assertThat(steppedPoints).as("seed %d", seed).isEqualTo(jumpPoints);
            assertThat(t.thirst()).as("seed %d", seed).isCloseTo(jump.thirst(), within(1e-3f));
        }
    }

    @Test
    void noGapNoLossAndTimeOnlyMovesForward() {
        ThirstState s = new ThirstState(42f, 100);
        assertThat(s.advance(100, 0, 40f, CFG)).isSameAs(s);
        assertThat(s.advance(50, 0, 40f, CFG).thirst()).isEqualTo(42f);
        assertThat(s.advance(150, 0, 40f, CFG).lastTick()).isEqualTo(150);
    }
}
