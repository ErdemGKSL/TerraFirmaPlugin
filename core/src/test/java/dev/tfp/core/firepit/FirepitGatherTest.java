/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.firepit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-HEAT-30: golden cases of {@code insertSlots} over four fuel slots (hand-traced from NeoForge's insertItem). */
class FirepitGatherTest {
    static final boolean[] ALL = {true, true, true, true};
    static final boolean[] NONE = {false, false, false, false};

    @Test
    void anEmptyFirepitTakesTheFirstSlotUpToItsLimitThenTheNext() {
        int[] add = FirepitGather.insert(new int[] {16, 16, 16, 16}, new int[4], ALL, ALL, 20);
        assertThat(add).containsExactly(16, 4, 0, 0);
    }

    @Test
    void aSlotWithTheSameKindFillsUpAndOtherKindsAreSkipped() {
        int[] add = FirepitGather.insert(new int[] {16, 16, 16, 16}, new int[] {10, 3, 0, 0},
            new boolean[] {true, false, false, false}, ALL, 9);
        assertThat(add).as("slot 0 takes 6, slot 1 holds another kind, slot 2 is empty").containsExactly(6, 0, 3, 0);
    }

    @Test
    void notFuelOrAFullFirepitTakesNothing() {
        assertThat(FirepitGather.insert(new int[] {16, 16, 16, 16}, new int[4], ALL, NONE, 5)).containsExactly(0, 0, 0, 0);
        assertThat(FirepitGather.insert(new int[] {4, 4, 4, 4}, new int[] {4, 4, 4, 4}, ALL, ALL, 5))
            .containsExactly(0, 0, 0, 0);
        assertThat(FirepitGather.insert(new int[] {4, 4, 4, 4}, new int[4], ALL, ALL, 0)).containsExactly(0, 0, 0, 0);
    }
}
