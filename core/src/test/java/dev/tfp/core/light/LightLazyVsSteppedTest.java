/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (onRandomTick),
 *       src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut)
 */
package dev.tfp.core.light;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Lazy forms against the per-tick (stepped) rule. A torch or candle is stepped one calendar tick at a time; the lazy form
 * is a single evaluation at the elapsed time. For a lamp the stepped reference checks every tick, and the lazy form is one
 * check over the same window.
 */
class LightLazyVsSteppedTest {
    private static final int[] LIMITS = {-1, 0, 1, 5, 100, 1200, 86_400};

    @Test
    void torchBurnoutLazyEqualsSteppedAtEveryTick() {
        Random random = new Random(0x7fc4L);
        for (int trial = 0; trial < 40; trial++) {
            int limit = LIMITS[trial % LIMITS.length];
            long placed = random.nextInt(1_000_000);
            boolean steppedDead = false;
            long firstDead = -1;
            for (long now = placed; now <= placed + Math.max(limit, 0) + 10; now++) {
                // stepped: a latch that is set by the first tick whose check sees burnout, and never cleared
                boolean dead = LightSourceRules.burnedOut(now - placed, limit);
                if (dead && !steppedDead) {
                    firstDead = now - placed;
                }
                steppedDead |= dead;
                // lazy: the single evaluation at the elapsed time must agree with the latch on every tick
                assertThat(LightSourceRules.burnedOut(now - placed, limit)).isEqualTo(steppedDead);
            }
            if (limit > 0) {
                assertThat(firstDead).as("limit %s", limit).isEqualTo(limit + 1L);
            } else {
                assertThat(firstDead).as("limit %s never burns out", limit).isEqualTo(-1L);
            }
        }
    }

    @Test
    void torchLazyAnswerIsIndependentOfCheckSchedule() {
        Random random = new Random(0x51L);
        for (int trial = 0; trial < 200; trial++) {
            int limit = 1 + random.nextInt(5000);
            long placed = random.nextInt(100_000);
            long end = placed + random.nextInt(10_000);
            // a schedule of random ticks: the stepped flag is set by whichever check first sees burnout
            boolean flag = false;
            long t = placed;
            while (t < end) {
                t += 1 + random.nextInt(200);
                flag |= LightSourceRules.burnedOut(Math.min(t, end) - placed, limit);
            }
            assertThat(flag).isEqualTo(LightSourceRules.burnedOut(end - placed, limit));
            assertThat(LightSourceRules.remainingTicks(end - placed, limit))
                .isEqualTo(Math.max(0, limit - (end - placed)));
        }
    }

    @Test
    void lampLazyEqualsPerTickCheckWhenWindowEndsOnTheRateGrid() {
        Random random = new Random(0x1ab3L);
        for (int trial = 0; trial < 60; trial++) {
            int rate = 1 + random.nextInt(60);
            int tank = random.nextInt(12);
            int k = 1 + random.nextInt(14);
            long end = (long) rate * k;

            // stepped: a check every tick, the counter reset whenever the check burns fuel
            int steppedTank = tank;
            boolean steppedLit = true;
            long last = 0;
            for (long t = 1; t <= end; t++) {
                LampRules.Check c = LampRules.check(t - last, rate, steppedTank, steppedLit);
                steppedTank = c.tankAfter();
                steppedLit = c.lit();
                if (c.reset()) {
                    last = t;
                }
            }

            LampRules.Check lazy = LampRules.check(end, rate, tank, true);
            assertThat(lazy.tankAfter()).as("rate %s tank %s k %s", rate, tank, k).isEqualTo(steppedTank);
            assertThat(lazy.lit()).isEqualTo(steppedLit);
        }
    }

    @Test
    void lampOffGridCheckDropsTheRemainderAsTfcDoes() {
        // TFC resets the counter on every burning check and drops the remainder. A single check at 1.5 rates burns 1 mB and
        // restarts the counter at 1.5 rates, so the next whole mB is due at 2.5 rates, not 2 rates. Per-tick checks burn 2 mB
        // by 2 rates. The port pins TFC here; see the ledger's lamp cadence divergence question.
        int rate = 6000;
        LampRules.Check first = LampRules.check(rate + rate / 2, rate, 250, true);
        assertThat(first.drained()).isEqualTo(1);
        assertThat(first.reset()).isTrue();

        // second check at 2 rates: only 0.5 rate has passed since the reset, so nothing is burnt (per-tick would have burnt 1 more)
        LampRules.Check second = LampRules.check(rate / 2, rate, first.tankAfter(), true);
        assertThat(second.drained()).isZero();
        assertThat(second.tankAfter()).isEqualTo(249);
    }
}
