/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.rock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;

class MossSpreadTest {
    @Test
    void offsetFollowsTfcDrawOrderAndRange() {
        // TFC: nextInt(4) - nextInt(4) for x, then y, then z; replay the same seed by hand
        Random a = new Random(42);
        Random b = new Random(42);
        int[] o = MossSpread.offset(a);
        assertThat(o).containsExactly(b.nextInt(4) - b.nextInt(4), b.nextInt(4) - b.nextInt(4),
                b.nextInt(4) - b.nextInt(4));
        Random r = new Random(1);
        for (int i = 0; i < 10_000; i++) {
            for (int v : MossSpread.offset(r)) {
                assertThat(v).isBetween(-MossSpread.REACH, MossSpread.REACH);
            }
        }
    }

    @Test
    void attemptsAreZeroWithoutTimeOrSpeed() {
        Random r = new Random(7);
        assertThat(MossSpread.attempts(0, 3, 20, r)).isZero();
        assertThat(MossSpread.attempts(-5, 3, 20, r)).isZero();
        assertThat(MossSpread.attempts(1_000_000, 0, 20, r)).isZero();
        assertThatThrownBy(() -> MossSpread.attempts(10, 3, 0, r)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attemptMeanMatchesTheTickRate() {
        // a day: 24000 * 3 / 4096 / 20 = 0.8789 expected attempts
        double mean = 24_000 * MossSpread.attemptsPerTick(3, 20);
        assertThat(mean).isCloseTo(0.8789, org.assertj.core.data.Offset.offset(1e-3));
        Random r = new Random(99);
        long total = 0;
        int runs = 40_000;
        for (int i = 0; i < runs; i++) {
            total += MossSpread.attempts(24_000, 3, 20, r);
        }
        assertThat(total / (double) runs).isCloseTo(mean, org.assertj.core.data.Offset.offset(0.03));
    }

    @Test
    void attemptsAreCappedAndDeterministic() {
        assertThat(MossSpread.attempts(Long.MAX_VALUE / 4, 3, 1, new Random(3))).isEqualTo(MossSpread.MAX_ATTEMPTS);
        assertThat(MossSpread.attempts(500_000, 3, 20, new Random(5))).isEqualTo(MossSpread.attempts(500_000, 3, 20, new Random(5)));
    }

    @Test
    void mossyIdsMapEveryShape() {
        assertThat(MossSpread.mossyOf("tfp:rock/cobble/granite")).contains("tfp:rock/mossy_cobble/granite");
        assertThat(MossSpread.mossyOf("tfp:rock/bricks/granite_stairs")).contains("tfp:rock/mossy_bricks/granite_stairs");
        assertThat(MossSpread.mossyOf("tfp:rock/cobble/basalt_wall")).contains("tfp:rock/mossy_cobble/basalt_wall");
        assertThat(MossSpread.mossyOf("tfp:rock/mossy_cobble/granite")).isEmpty();
        assertThat(MossSpread.mossyOf("tfp:rock/raw/granite")).isEmpty();
        assertThat(MossSpread.isMossy("tfp:rock/mossy_bricks/chalk_slab")).isTrue();
        assertThat(MossSpread.isMossy("tfp:rock/bricks/chalk_slab")).isFalse();
    }
}
