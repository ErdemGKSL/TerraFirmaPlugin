/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.fluid.RiverWaterRules;
import dev.tfp.core.worldgen.river.Flow;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-FLUID-03: vanilla's fluid push with TFC's river flows, as the server applies it. */
class RiverCurrentTest {
    private static final double TOL = 1e-3;

    @Test
    void heightsOfRiverWater() {
        assertThat(RiverCurrent.height(true)).isEqualTo(1f);
        assertThat(RiverCurrent.height(false)).isEqualTo(8f / 9f);
    }

    @Test
    void aFloatingItemIsPushedDownstreamAtTheWaterMotionScale() {
        // a box from y 62.5 in a river block at 62 (top 62.889): depth 0.389 < 0.4, but a non-player is normalized
        List<RiverCurrent.Cell> cells = List.of(new RiverCurrent.Cell(62, RiverCurrent.height(false), Flow.EEE));
        RiverWaterRules.Vector v = RiverCurrent.push(cells, 62.5, false, 0.1, 0);
        assertThat(v.x()).isCloseTo(0.014, within(TOL));
        assertThat(v.y()).isCloseTo(0, within(TOL));
        assertThat(v.z()).isCloseTo(0, within(TOL));
        // north is -z (TFC's vector is (cos a, 0, -sin a))
        RiverWaterRules.Vector n = RiverCurrent.push(List.of(new RiverCurrent.Cell(62, 1f, Flow.NNN)), 62.0, false, 0.1, 0);
        assertThat(n.z()).isCloseTo(-0.014, within(TOL));
    }

    @Test
    void stillWaterCountsButDoesNotPush() {
        assertThat(RiverCurrent.push(List.of(new RiverCurrent.Cell(62, 1f, Flow.NONE)), 62.0, false, 0, 0))
                .isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
        assertThat(RiverCurrent.push(List.of(), 62.0, false, 0, 0)).isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
        // a player is not normalized: one river block of two, deep (scale 1), averages to half the flow
        List<RiverCurrent.Cell> cells = List.of(new RiverCurrent.Cell(61, 1f, Flow.EEE), new RiverCurrent.Cell(62, 1f, Flow.NONE));
        RiverWaterRules.Vector p = RiverCurrent.push(cells, 61.0, true, 0.1, 0);
        assertThat(p.x()).isCloseTo(0.5 * 0.014, within(TOL));
    }

    @Test
    void shallowPlayersArePushedByDepthAndRaisedToTheMinimumWhenStill() {
        // depth 0.1: the flow is scaled to 0.1, times 0.014 = 0.0014, below the minimum 0.0045 for a still entity
        List<RiverCurrent.Cell> cells = List.of(new RiverCurrent.Cell(62, 1f, Flow.EEE));
        RiverWaterRules.Vector moving = RiverCurrent.push(cells, 62.9, true, 0.1, 0);
        assertThat(moving.x()).isCloseTo(0.0014, within(1e-4));
        RiverWaterRules.Vector still = RiverCurrent.push(cells, 62.9, true, 0, 0);
        assertThat(still.x()).isCloseTo(RiverCurrent.MIN_PUSH, within(1e-5));
    }

    @Test
    void blocksBelowTheBoxDoNotCount() {
        List<RiverCurrent.Cell> cells = List.of(new RiverCurrent.Cell(60, 8f / 9f, Flow.EEE));
        assertThat(RiverCurrent.push(cells, 61.0, false, 0, 0)).isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
    }
}
