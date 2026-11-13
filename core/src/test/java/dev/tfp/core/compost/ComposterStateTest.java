/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.compost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** PAR-PROC-11: the stored composter state (codec round trip, settle with the sampled ready ticks). */
class ComposterStateTest {

    private static ComposterState full(long at, long ready) {
        return new ComposterState(new CompostBin(16, 16, CompostBin.Type.NORMAL, at, 0), ready);
    }

    @Test
    void codecRoundTripsEveryField() {
        ComposterState s = new ComposterState(new CompostBin(7, 12, CompostBin.Type.ROTTEN, 123_456L, 1), 302_400L);
        assertThat(ComposterState.CODEC.decode(ComposterState.CODEC.encode(s))).isEqualTo(s);
        assertThat(ComposterState.CODEC.decode(ComposterState.CODEC.encode(ComposterState.FRESH)))
            .isEqualTo(ComposterState.FRESH);
    }

    @Test
    void freshIsAnEmptyBinWithTfcDefaultTicks() {
        assertThat(ComposterState.FRESH.bin()).isEqualTo(CompostBin.empty());
        assertThat(ComposterState.FRESH.readyTicks()).isEqualTo(288_000L);
    }

    @Test
    void readyAfterComposterTicksAtDefaultConditions() {
        long ready = CompostRules.readyTicks(CompostRules.DEFAULT_READY_TICKS, 250f, 0);
        assertThat(ready).isEqualTo(CompostRules.DEFAULT_READY_TICKS);
        ComposterState s = full(1_000L, ready);
        assertThat(s.ripenedAt()).isEqualTo(1_000L + 288_000L + 1);
        assertThat(s.settle(1_000L + 288_000L).bin().type()).isEqualTo(CompostBin.Type.NORMAL);
        ComposterState done = s.settle(1_000L + 288_001L);
        assertThat(done.bin().type()).isEqualTo(CompostBin.Type.READY);
        assertThat(done.bin().output()).isEqualTo(1);
        assertThat(done.readyTicks()).isEqualTo(ready);
    }

    @Test
    void settleUsesTheStoredReadyTicks() {
        ComposterState s = full(0L, 500L);
        assertThat(s.settle(501L).bin().type()).isEqualTo(CompostBin.Type.READY);
        assertThat(s.withReadyTicks(10_000L).settle(501L).bin().type()).isEqualTo(CompostBin.Type.NORMAL);
        assertThat(s.settle(400L)).isSameAs(s);
    }

    @Test
    void negativeReadyTicksAreRejected() {
        assertThatThrownBy(() -> new ComposterState(CompostBin.empty(), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
