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

import dev.tfp.core.drying.DryingRules.Pile;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The lazy {@link DryingRules#advance} against the TFC rule stepped one calendar tick at a time, over seeded random piles,
 * thresholds, brick adds and rain. Composing two windows must give the same pile as one window.
 */
class DryingLazyVsSteppedTest {
    private static final int[] THRESHOLDS = {DryingRules.DISABLED, 0, 1, 7, 50, 1200, DryingRules.DEFAULT_THRESHOLD_TICKS};

    @Test
    void lazyEqualsStepping() {
        Random random = new Random(0xD41E);
        for (int trial = 0; trial < 2500; trial++) {
            int threshold = THRESHOLDS[random.nextInt(THRESHOLDS.length)];
            long start = random.nextInt(4000);
            long add = random.nextBoolean() ? -1 : start + 1 + random.nextInt(40000);
            long now = start + 1 + random.nextInt(70_000);
            boolean[] rain = randomRain(random, now, random.nextInt(4));

            Pile lazy = lazy(start, add, threshold, rain, now);
            Pile stepped = stepped(start, add, threshold, rain, now);
            String what = "T=" + threshold + " start=" + start + " add=" + add + " now=" + now;
            assertThat(lazy).as(what).isEqualTo(stepped);
        }
    }

    @Test
    void windowsCompose() {
        Random random = new Random(0x51DE);
        for (int trial = 0; trial < 1500; trial++) {
            int threshold = THRESHOLDS[random.nextInt(THRESHOLDS.length)];
            long now = 1 + random.nextInt(70_000);
            long cut = random.nextInt((int) now + 1);
            long[] rainTicks = toTicks(randomRain(random, now, random.nextInt(4)));

            Pile whole = DryingRules.advance(Pile.placed(0), now, threshold, rainTicks);
            Pile first = DryingRules.advance(Pile.placed(0), cut, threshold, rainTicks);
            Pile split = DryingRules.advance(first, now, threshold, rainTicks);
            assertThat(split).as("T=" + threshold + " cut=" + cut + " now=" + now).isEqualTo(whole);
        }
    }

    private static Pile lazy(long start, long add, int threshold, boolean[] rain, long now) {
        long[] rainTicks = toTicks(rain);
        if (add >= 0 && add <= now) {
            Pile p = DryingRules.advance(Pile.placed(start), add, threshold, rainTicks);
            p = DryingRules.reset(p, add);
            return DryingRules.advance(p, now, threshold, rainTicks);
        }
        return DryingRules.advance(Pile.placed(start), now, threshold, rainTicks);
    }

    /** TFC's tick, one calendar tick at a time: rain resets the counter, else a counter over the threshold dries. */
    static Pile stepped(long start, long add, int threshold, boolean[] rain, long now) {
        long last = start;
        long dried = Pile.NOT_DRIED;
        for (long t = start + 1; t <= now; t++) {
            if (dried == Pile.NOT_DRIED) {
                if (rain[(int) t]) {
                    last = t;
                } else if (threshold != DryingRules.DISABLED && t - last > threshold) {
                    dried = t;
                }
            }
            if (add >= 0 && t == add && dried == Pile.NOT_DRIED) {
                last = add;
            }
        }
        return new Pile(last, dried);
    }

    /** Rain on about {@code 0 .. 0.5} of the ticks, with {@code mode} choosing the density; index 0 is never rained on. */
    private static boolean[] randomRain(Random random, long now, int mode) {
        double[] density = {0.0, 0.0002, 0.01, 0.5};
        boolean[] rain = new boolean[(int) now + 1];
        for (int t = 1; t <= now; t++) {
            rain[t] = random.nextDouble() < density[mode];
        }
        return rain;
    }

    static long[] toTicks(boolean[] rain) {
        return java.util.stream.IntStream.range(0, rain.length).filter(t -> rain[t]).asLongStream().toArray();
    }
}
