/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (tick, useItemOn)
 */
package dev.tfp.core.drying;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.drying.DryingRules.Pile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Determinism, thread sharing, the reset and add rules, progress, and the input checks of the drying rule.
 */
class DryingPropertyTest {
    @Test
    void sameInputsGiveTheSameResult() {
        long[] rain = {300, 9000, 40000};
        Pile a = DryingRules.advance(Pile.placed(10), 70_000, 28_800, rain);
        for (int i = 0; i < 50; i++) {
            assertThat(DryingRules.advance(Pile.placed(10), 70_000, 28_800, rain)).isEqualTo(a);
        }
        assertThat(a.dried()).isTrue();
    }

    @Test
    void sharedAcrossThreadsGivesOneResult() throws Exception {
        Random random = new Random(7);
        List<long[]> inputs = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            long[] rain = random.longs(20, 0, 80_000).distinct().sorted().toArray();
            long[] in = new long[2 + rain.length];
            in[0] = random.nextInt(2000);
            in[1] = random.nextInt(40_000);
            System.arraycopy(rain, 0, in, 2, rain.length);
            inputs.add(in);
        }
        List<Pile> expected = new ArrayList<>();
        for (long[] in : inputs) {
            expected.add(run(in));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Pile>> futures = new ArrayList<>();
            for (int rep = 0; rep < 4; rep++) {
                for (long[] in : inputs) {
                    futures.add(pool.submit(() -> run(in)));
                }
            }
            for (int i = 0; i < futures.size(); i++) {
                assertThat(futures.get(i).get()).isEqualTo(expected.get(i % inputs.size()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void driedPileStaysDryAndTakesNoBricksOrResets() {
        Pile dry = DryingRules.advance(Pile.placed(0), 40_000, 1200, new long[0]);
        assertThat(dry.dried()).isTrue();
        assertThat(DryingRules.advance(dry, 90_000, 1200, new long[] {50_000})).isEqualTo(dry);
        assertThat(DryingRules.reset(dry, 50_000)).isEqualTo(dry);
        assertThat(DryingRules.canAdd(dry, 1)).isFalse();
    }

    @Test
    void addAndResetRules() {
        assertThat(DryingRules.canAdd(Pile.placed(0), 1)).isTrue();
        assertThat(DryingRules.canAdd(Pile.placed(0), 3)).isTrue();
        assertThat(DryingRules.canAdd(Pile.placed(0), DryingRules.MAX_COUNT)).isFalse();
        assertThat(DryingRules.reset(Pile.placed(5), 900)).isEqualTo(Pile.placed(900));
    }

    @Test
    void disabledNeverDriesButRainStillResets() {
        Pile p = DryingRules.advance(Pile.placed(0), 1_000_000, DryingRules.DISABLED, new long[] {400_000});
        assertThat(p.dried()).isFalse();
        assertThat(p.lastUpdateTick()).isEqualTo(400_000);
        assertThat(DryingRules.progress(p, 1_000_000, DryingRules.DISABLED)).isZero();
    }

    @Test
    void zeroThresholdDriesOneTickAfterThePlacement() {
        assertThat(DryingRules.advance(Pile.placed(100), 100, 0, new long[0]).dried()).isFalse();
        assertThat(DryingRules.advance(Pile.placed(100), 101, 0, new long[0]).driedTick()).isEqualTo(101);
    }

    @Test
    void rainOnTheDryTickBlocksIt() {
        long[] rain = {28_801};
        Pile p = DryingRules.advance(Pile.placed(0), 28_801, 28_800, rain);
        assertThat(p).isEqualTo(new Pile(28_801, Pile.NOT_DRIED));
        assertThat(DryingRules.advance(Pile.placed(0), 57_602, 28_800, rain).driedTick()).isEqualTo(57_602);
    }

    @Test
    void progressIsMonotoneAndReachesOneAtTheDryTick() {
        Pile p = Pile.placed(1000);
        double last = -1;
        for (long now = 1000; now <= 1000 + 1201; now++) {
            Pile at = DryingRules.advance(p, now, 1200, new long[0]);
            double progress = DryingRules.progress(at, now, 1200);
            assertThat(progress).isGreaterThanOrEqualTo(last);
            last = progress;
        }
        assertThat(last).isEqualTo(1.0);
        assertThat(DryingRules.progress(Pile.placed(1000), 1600, 1200)).isEqualTo(600 / 1201.0);
    }

    @Test
    void rejectsBadInputs() {
        assertThatThrownBy(() -> DryingRules.advance(Pile.placed(0), 10, -2, new long[0]))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DryingRules.advance(Pile.placed(0), 10, 5, new long[] {7, 3}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pile(100, 99)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Inputs: {placed, threshold, rain...}. */
    private static Pile run(long[] in) {
        long[] rain = java.util.Arrays.copyOfRange(in, 2, in.length);
        return DryingRules.advance(Pile.placed(in[0]), 80_000, (int) in[1], rain);
    }
}
