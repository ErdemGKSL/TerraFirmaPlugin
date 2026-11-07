/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (recipeTimer counted down by
 * MANUAL_RECIPE_PER_TICK from MANUAL_TICKS; finishGrinding on the tick it reaches 0)
 */
package dev.tfp.core.quern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.persist.DataMap;
import org.junit.jupiter.api.Test;

/** PAR-PROC-04: the lazy grind against TFC's stepped timer, and the codec. */
class QuernStateTest {

    /** TFC's serverTick, stepped: the tick the timer reaches 0 (and the grind finishes) after a start at {@code start}. */
    static long steppedFinish(long start) {
        float timer = QuernRules.TICKS_PER_GRIND; // startGrinding
        long tick = start;
        while (true) {
            tick++;
            boolean wasGrinding = timer > 0;
            if (timer > 0) {
                timer -= 1f; // MANUAL_RECIPE_PER_TICK
            }
            if (wasGrinding && timer <= 0) {
                return tick;
            }
        }
    }

    @Test
    void lazyFinishTickEqualsTheSteppedOne() {
        for (long start : new long[] {0, 1, 17, 24000, 1_000_000_007L}) {
            QuernState s = QuernState.FRESH.start(start);
            long finish = steppedFinish(start);
            assertThat(s.finishTick()).isEqualTo(finish);
            assertThat(s.grinding(finish - 1)).isTrue();
            assertThat(s.due(finish - 1)).isFalse();
            assertThat(s.grinding(finish)).isFalse();
            assertThat(s.due(finish)).isTrue();
            assertThat(s.due(finish + 100_000)).as("a late finish is still due").isTrue();
        }
    }

    @Test
    void progressFollowsTheManualRule() {
        QuernState s = QuernState.FRESH.start(1000);
        assertThat(s.progress(1000)).isZero();
        assertThat(s.progress(1045)).isEqualTo(45);
        assertThat(s.progress(1090)).isEqualTo(90);
        assertThat(s.progress(5000)).isEqualTo(90);
        assertThat(s.progress(900)).as("a calendar set back reads as just started").isZero();
        assertThat(QuernState.FRESH.progress(1000)).isZero();
    }

    @Test
    void idleIsNotGrinding() {
        assertThat(QuernState.FRESH.started()).isFalse();
        assertThat(QuernState.FRESH.grinding(Long.MAX_VALUE)).isFalse();
        assertThat(QuernState.FRESH.due(Long.MAX_VALUE)).isFalse();
        assertThat(QuernState.FRESH.start(5).idle()).isEqualTo(QuernState.FRESH);
        assertThatThrownBy(() -> QuernState.FRESH.start(QuernState.IDLE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codecRoundTripsAndDefaultsToIdle() {
        for (QuernState s : new QuernState[] {QuernState.FRESH, QuernState.FRESH.start(123456789L)}) {
            DataMap m = QuernState.CODEC.encode(s);
            assertThat(QuernState.CODEC.decode(m)).isEqualTo(s);
        }
        DataMap noStart = QuernState.CODEC.encode(QuernState.FRESH);
        noStart.remove("start");
        assertThat(QuernState.CODEC.decode(noStart)).isEqualTo(QuernState.FRESH);
    }
}
