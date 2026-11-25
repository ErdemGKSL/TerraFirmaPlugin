/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sluice;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.pan.SluiceRules;
import dev.tfp.core.pan.SluiceRules.Run;
import dev.tfp.core.pan.SluiceRules.State;
import dev.tfp.core.pan.SluiceRules.Step;
import dev.tfp.core.persist.DataMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SluiceStateTest {
    @Test
    void codecRoundTrips() {
        SluiceState s = new SluiceState(37, true, List.of("tfp:deposit/a", "tfp:deposit/b"));
        DataMap map = SluiceState.CODEC.encode(s);
        assertThat(SluiceState.CODEC.decode(map)).isEqualTo(s);
    }

    @Test
    void lazyAdvanceEqualsSteppingEveryTick() {
        Random random = new Random(13);
        for (int round = 0; round < 300; round++) {
            int cycle = 1 + random.nextInt(40);
            List<String> slots = new ArrayList<>();
            for (int i = 0; i < SluiceRules.MAX_SOIL; i++) {
                slots.add(random.nextInt(3) == 0 ? "d" + i : null);
            }
            Run start = new Run(random.nextInt(cycle + 2) - 1, slots);
            long elapsed = random.nextInt(500);
            Run stepped = start;
            List<String> produced = new ArrayList<>();
            for (long t = 0; t < elapsed; t++) {
                Step step = SluiceRules.step(stepped, State.BOTH, cycle);
                stepped = step.run();
                if (step.produced() != null) {
                    produced.add(step.produced());
                }
            }
            SluiceRules.Advanced lazy = SluiceRules.advance(start, State.BOTH, cycle, elapsed);
            assertThat(lazy.produced()).as("round %d", round).isEqualTo(produced);
            assertThat(lazy.run()).as("round %d", round).isEqualTo(stepped);
        }
    }

    @Test
    void notWorkingNeverAdvances() {
        Run run = Run.empty(100);
        assertThat(SluiceRules.advance(run, State.INPUT_ONLY, 100, 10_000).run()).isEqualTo(run);
    }
}
