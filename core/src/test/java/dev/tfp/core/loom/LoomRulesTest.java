/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.loom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoomRulesTest {
    @Test
    void aFreshLoomCanBePushedOnlyWithTheFullInput() {
        assertThat(LoomRules.canPush(LoomState.FRESH, 1000, 16, 16, 16)).isTrue();
        assertThat(LoomRules.canPush(LoomState.FRESH, 1000, 16, 16, 15)).isFalse();
    }

    @Test
    void aPushNeedsMoreThanTwentyTicksSinceTheLast() {
        LoomState pushed = LoomRules.push(LoomState.FRESH, 100);
        assertThat(LoomRules.canPush(pushed, 200, 16, 16, 16)).as("a step is still pending").isFalse();
        LoomState settled = LoomRules.settle(pushed, 120, 16).state();
        assertThat(LoomRules.canPush(settled, 120, 16, 16, 16)).as("exactly 20: TFC passes the click on").isFalse();
        assertThat(LoomRules.canPush(settled, 121, 16, 16, 16)).isTrue();
    }

    @Test
    void aStepIsCountedAfterTwentyTicksAndTheLastOneFinishes() {
        LoomState state = new LoomState(15, 0, false);
        state = LoomRules.push(state, 500);
        assertThat(LoomRules.settle(state, 519, 16).state()).isEqualTo(state);
        LoomRules.Settled done = LoomRules.settle(state, 520, 16);
        assertThat(done.state().progress()).isEqualTo(16);
        assertThat(done.finished()).isTrue();
        assertThat(LoomRules.canPush(done.state(), 10_000, 16, 16, 16)).as("no steps left").isFalse();
    }

    @Test
    void aLateSettleCountsOneStep() {
        LoomState state = LoomRules.push(LoomState.FRESH, 0);
        assertThat(LoomRules.settle(state, 1_000_000, 16).state().progress()).isEqualTo(1);
    }

    @Test
    void codecRoundTrips() {
        LoomState s = new LoomState(7, 12345L, true);
        assertThat(LoomState.CODEC.decode(LoomState.CODEC.encode(s))).isEqualTo(s);
        assertThat(LoomState.CODEC.decode(LoomState.CODEC.encode(LoomState.FRESH))).isEqualTo(LoomState.FRESH);
    }
}
