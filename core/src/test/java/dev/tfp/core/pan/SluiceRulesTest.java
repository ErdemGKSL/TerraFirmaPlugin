/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.pan.SluiceRules.Run;
import dev.tfp.core.pan.SluiceRules.State;
import dev.tfp.core.pan.SluiceRules.Step;
import org.junit.jupiter.api.Test;

class SluiceRulesTest {
    private static final int CYCLE = SluiceRules.DEFAULT_CYCLE_TICKS;

    @Test
    void representativeStateNeedsBothSidesForBoth() {
        assertThat(SluiceRules.representative(true, true, true)).isEqualTo(State.BOTH);
        assertThat(SluiceRules.representative(true, true, false)).isEqualTo(State.INPUT_ONLY);
        assertThat(SluiceRules.representative(true, false, false)).isEqualTo(State.INPUT_ONLY);
        assertThat(SluiceRules.representative(false, true, false)).isEqualTo(State.OUTPUT_ONLY);
        assertThat(SluiceRules.representative(false, false, false)).isEqualTo(State.NONE);
    }

    @Test
    void countdownOnlyMovesWhileWorking() {
        Run run = Run.empty(CYCLE);
        Step idle = SluiceRules.step(run, State.INPUT_ONLY, CYCLE);
        assertThat(idle.run().ticksRemaining()).isEqualTo(CYCLE);
        Step working = SluiceRules.step(run, State.BOTH, CYCLE);
        assertThat(working.run().ticksRemaining()).isEqualTo(CYCLE - 1);
    }

    @Test
    void cycleResetsWithoutSoilAndProducesNothing() {
        Run run = new Run(1, Run.empty(CYCLE).slots());
        Step step = SluiceRules.step(run, State.BOTH, CYCLE);
        assertThat(step.produced()).isNull();
        assertThat(step.run().ticksRemaining()).isEqualTo(CYCLE);
    }

    @Test
    void firstFilledSlotIsProducedAndEmptied() {
        Run run = SluiceRules.accept(SluiceRules.accept(new Run(1, Run.empty(CYCLE).slots()), "gold"), "iron");
        Step step = SluiceRules.step(run, State.BOTH, CYCLE);
        assertThat(step.produced()).isEqualTo("gold");
        assertThat(step.run().slots().get(0)).isNull();
        assertThat(step.run().slots().get(1)).isEqualTo("iron");
    }

    @Test
    void acceptFillsTheFirstHoleAndStopsWhenFull() {
        Run run = new Run(CYCLE, Run.empty(CYCLE).slots());
        for (int i = 0; i < SluiceRules.MAX_SOIL; i++) {
            run = SluiceRules.accept(run, "soil");
        }
        Run full = run;
        assertThat(SluiceRules.accept(full, "extra")).isEqualTo(full);
        assertThat(full.slots()).doesNotContainNull();
    }

    @Test
    void stateIsUnchangedWhenNotWorking() {
        Run run = SluiceRules.accept(Run.empty(CYCLE), "gold");
        Step step = SluiceRules.step(run, State.OUTPUT_ONLY, CYCLE);
        assertThat(step.run()).isEqualTo(run);
        assertThat(step.produced()).isNull();
    }
}
