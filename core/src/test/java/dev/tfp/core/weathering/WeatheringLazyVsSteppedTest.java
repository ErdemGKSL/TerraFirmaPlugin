/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/IWeatheringBlock.java @ c3b4d9536 (onRandomTick, the draw order)
 */
package dev.tfp.core.weathering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.weathering.WeatheringRules.Age;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The lazy catch-up against a stepped reference. The stepped reference applies TFC's draw order to each random tick: a
 * gate draw that must reach 0.95, a resistance draw that must not exceed {@code 1 - resistance}, and a chance draw compared
 * with the stage's chance. Random ticks arrive at {@code randomTickSpeed / 4096} per game tick (geometric gaps, which is the
 * same as a per-tick Bernoulli draw). The stepped and lazy probabilities that a block has reached each stage after a
 * span are compared over many samples. The lazy form is equal to the stepped one in the limit of small per-tick chances, so
 * the tolerance covers the sampling error and the order-{@code p} difference.
 */
class WeatheringLazyVsSteppedTest {
    private static final int SAMPLES = 20_000;
    private static final double TOLERANCE = 0.02;

    @Test
    void slowAndExtraPathsAgreeWithSteppedInDistribution() {
        // copper-like, extra chance 0.5: a fast-ish stage walk over 100 000 ticks
        check(0f, 3, 0.5, Age.NONE, 100_000L, 0x1001L);
        // half resistance, extra chance 0.3: three stages in a 400 000-tick span
        check(0.5f, 3, 0.3, Age.NONE, 400_000L, 0x1002L);
        // slow path only, from EXPOSED over two million ticks
        check(0f, 3, 0.0, Age.EXPOSED, 2_000_000L, 0x1003L);
    }

    private static void check(float resistance, int speed, double extra, Age start, long span, long seed) {
        Random stepped = new Random(seed);
        Random lazy = new Random(seed ^ 0x9e3779b97f4a7c15L);
        int[] steppedAtLeast = new int[Age.values().length];
        int[] lazyAtLeast = new int[Age.values().length];
        for (int i = 0; i < SAMPLES; i++) {
            Age s = steppedStage(start, span, resistance, speed, extra, stepped);
            Age l = WeatheringRules.catchUp(start, span, resistance, speed, extra,
                new double[]{lazy.nextDouble(), lazy.nextDouble(), lazy.nextDouble()});
            for (int k = 0; k <= s.ordinal(); k++) {
                steppedAtLeast[k]++;
            }
            for (int k = 0; k <= l.ordinal(); k++) {
                lazyAtLeast[k]++;
            }
        }
        for (int k = start.ordinal() + 1; k < Age.values().length; k++) {
            double p = (double) steppedAtLeast[k] / SAMPLES;
            double q = (double) lazyAtLeast[k] / SAMPLES;
            assertThat(q).as("P(stage >= %s) after %d ticks, resistance %s, extra %s", Age.values()[k], span, resistance, extra)
                .isCloseTo(p, within(TOLERANCE));
        }
    }

    /** TFC's per-random-tick sequence, with the random ticks placed by geometric gaps over the game ticks. */
    private static Age steppedStage(Age start, long span, float resistance, int speed, double extra, Random random) {
        double tickChance = (double) speed / 4096;
        Age stage = start;
        long tick = 0;
        while (stage != Age.OXIDIZED) {
            double u = random.nextDouble();
            long gap = 1 + (long) Math.floor(Math.log(1 - u) / Math.log1p(-tickChance));
            tick += gap;
            if (tick > span) {
                break;
            }
            // TFC: return unless the gate draw reaches 0.95
            if (random.nextFloat() < 0.95f) {
                continue;
            }
            // TFC: return unless the resistance draw is at most 1 - resistance
            if (random.nextFloat() > 1f - resistance) {
                continue;
            }
            // TFC: the chance draw is compared with the slow chance and the extra chance, which are both thresholds
            double chance = Math.max((stage == Age.NONE ? 0.5 : 1.0) * 0.05, extra);
            if (random.nextFloat() < chance) {
                stage = stage.next();
            }
        }
        return stage;
    }
}
