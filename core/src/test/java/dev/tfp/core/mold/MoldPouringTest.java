/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.vessel.FluidAmount;
import org.junit.jupiter.api.Test;

/** PAR-POT-05: the pour step and the heat mix, against hand-computed TFC values. */
class MoldPouringTest {
    static final String COPPER = "tfp:metal/copper";
    static final float SH = 0.35f;

    @Test
    void mixIsTheCapacityWeightedMean() {
        // (20 * 3 + 1100 * 1) / 4 = 290
        assertThat(MoldPouring.mix(20f, 3f, 1100f, 1f)).isCloseTo(290f, within(1e-3f));
    }

    @Test
    void oneStepMovesOneMbAndMixesWithTheCapacityAfterTheFill() {
        MoldPouring.Step s = MoldPouring.step(new FluidAmount(COPPER, 500), 1100f, FluidAmount.EMPTY, 20f, 100, true, SH);
        assertThat(s.moved()).isEqualTo(1);
        assertThat(s.content()).isEqualTo(new FluidAmount(COPPER, 1));
        float capAfter = 1.2f + SH * 1;
        float expected = 20f * capAfter / (capAfter + SH) + 1100f * SH / (capAfter + SH);
        assertThat(s.temperature()).isCloseTo(expected, within(1e-3f));
    }

    @Test
    void nothingMovesWhenFullWrongFluidOrNotContainable() {
        FluidAmount full = new FluidAmount(COPPER, 100);
        assertThat(MoldPouring.step(new FluidAmount(COPPER, 5), 1100f, full, 900f, 100, true, SH).moved()).isZero();
        assertThat(MoldPouring.step(new FluidAmount("tfp:metal/bronze", 5), 1100f, full, 900f, 200, true, SH).moved()).isZero();
        assertThat(MoldPouring.step(new FluidAmount(COPPER, 5), 1100f, FluidAmount.EMPTY, 20f, 100, false, SH).moved()).isZero();
        assertThat(MoldPouring.step(FluidAmount.EMPTY, 1100f, FluidAmount.EMPTY, 20f, 100, true, SH).moved()).isZero();
    }

    @Test
    void ahundredStepsFillAMoldToItsCapacity() {
        FluidAmount source = new FluidAmount(COPPER, 150);
        FluidAmount content = FluidAmount.EMPTY;
        float t = 20f;
        int moved = 0;
        for (int i = 0; i < 150; i++) {
            MoldPouring.Step s = MoldPouring.step(source, 1100f, content, t, 100, true, SH);
            moved += s.moved();
            content = s.content();
            t = s.temperature();
        }
        assertThat(moved).isEqualTo(100);
        assertThat(content.amount()).isEqualTo(100);
        assertThat(t).isBetween(20f, 1100f);
    }
}
