/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** M2-GEN-15: the statistics of {@code /tfp debug worldgen}. */
class FillTimingTest {
    @Test
    void statsInMillisecondsWithNearestRankP95AndRefusedLeftOut() {
        long[] nanos = new long[21];
        for (int i = 0; i < 20; i++) {
            nanos[i] = (i + 1) * 1_000_000L; // 1..20 ms
        }
        nanos[20] = -1; // refused
        FillTiming.Stats s = FillTiming.of(nanos);
        assertThat(s.count()).isEqualTo(20);
        assertThat(s.refused()).isEqualTo(1);
        assertThat(s.meanMs()).isEqualTo(10.5);
        assertThat(s.p95Ms()).isEqualTo(19.0);
        assertThat(s.minMs()).isEqualTo(1.0);
        assertThat(s.maxMs()).isEqualTo(20.0);
    }

    @Test
    void allRefusedIsEmpty() {
        assertThat(FillTiming.of(new long[] {-1, -1})).isEqualTo(new FillTiming.Stats(0, 2, 0, 0, 0, 0));
    }

    @Test
    void gridSide() {
        assertThat(FillTiming.side(300)).isEqualTo(18);
        assertThat(FillTiming.side(1)).isEqualTo(1);
        assertThat(FillTiming.side(16)).isEqualTo(4);
    }
}
