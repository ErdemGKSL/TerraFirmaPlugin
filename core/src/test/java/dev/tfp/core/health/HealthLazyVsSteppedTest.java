/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Regeneration is never ticked: one jump to a tick must equal tick-by-tick stepping over the same gap, when food and
 * thirst are constant over each gap (the tick-by-tick reference applies the TFC rule at every tick).
 */
class HealthLazyVsSteppedTest {
    private static final HealthConfig CFG = HealthConfig.DEFAULT;

    @Test
    void randomSegmentsEqualTickByTickStepping() {
        for (long seed = 1; seed <= 25; seed++) {
            Random rnd = new Random(seed);
            float max = CFG.vanillaMaxHealth();
            HealthState lazy = new HealthState(rnd.nextFloat() * max, 0);
            float stepped = lazy.health();
            long tick = 0;
            for (int segment = 0; segment < 40; segment++) {
                long length = 1 + rnd.nextInt(600);
                float food = rnd.nextInt(41) / 2f; // 0..20 in half steps
                float thirst = rnd.nextInt(201) / 2f; // 0..100 in half steps
                for (long t = tick + 1; t <= tick + length; t++) {
                    if (t % CFG.regenIntervalTicks() == 0 && stepped > 0 && stepped < max
                            && HealthRegen.canRegenerate(food, thirst, CFG)) {
                        stepped = Math.min(max, stepped + HealthRegen.healPerStep(food, thirst, CFG));
                    }
                }
                tick += length;
                lazy = lazy.advance(tick, food, thirst, max, CFG);
                assertThat(lazy.health()).as("seed %d segment %d", seed, segment).isCloseTo(stepped, within(1e-3f));
                assertThat(lazy.lastTick()).isEqualTo(tick);
            }
        }
    }

    @Test
    void longGapStopsAtMaxHealthExactly() {
        HealthState s = new HealthState(0.5f, 0).advance(1_000_000, 20f, 100f, 20f, CFG);
        assertThat(s.health()).isEqualTo(20f);
    }

    @Test
    void deadOrFullOrUnfedPlayersDoNotRegenerate() {
        assertThat(new HealthState(0f, 0).advance(1000, 20f, 100f, 20f, CFG).health()).isZero();
        assertThat(new HealthState(20f, 0).advance(1000, 20f, 100f, 20f, CFG).health()).isEqualTo(20f);
        assertThat(new HealthState(5f, 0).advance(1000, 3.9f, 100f, 20f, CFG).health()).isEqualTo(5f);
        assertThat(new HealthState(5f, 0).advance(1000, 20f, 20f, 20f, CFG).health()).isEqualTo(5f);
    }

    @Test
    void timeMovesWithoutSteps() {
        HealthState s = new HealthState(5f, 100).advance(105, 20f, 100f, 20f, CFG);
        assertThat(s.health()).isEqualTo(5f);
        assertThat(s.lastTick()).isEqualTo(105);
        assertThat(new HealthState(5f, 100).advance(50, 20f, 100f, 20f, CFG)).isEqualTo(new HealthState(5f, 100));
    }
}
