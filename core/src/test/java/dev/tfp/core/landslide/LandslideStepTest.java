/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.landslide.LandslideRules.LandslideStep;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** M3-PHYS-02: what the platform must write for a decision, as TFC's tryLandslide does. */
class LandslideStepTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    @Test
    void aSlideIntoAirClearsTheSourceAndLeavesTheResult() {
        // dirt at the origin, stone below and to the east: slides west into air
        LandslideView view = LandslideGoldenTest.view(LandslideDeterminismTest.oneOpenSide());
        LandslideRules rules = LandslideGoldenTest.rules();
        Optional<LandslideStep> step = rules.step(view, ORIGIN, new LandslideDecision.Slide(Direction.WEST));
        assertThat(step).contains(new LandslideStep(ORIGIN, new BlockPos(-1, 0, 0), "minecraft:dirt", true, false));
    }

    @Test
    void aSlideIntoAnOccupiedBlockBreaksIt() {
        // grass slides into the air to its west, but the block there is cobble
        String box = LandslideDeterminismTest.box('G', Map.of(new BlockPos(0, -1, 0), 'R', new BlockPos(1, 0, 0), 'R',
            new BlockPos(-1, 0, 0), 'C'));
        LandslideRules rules = LandslideGoldenTest.rules();
        Optional<LandslideStep> step = rules.step(LandslideGoldenTest.view(box), ORIGIN, new LandslideDecision.Slide(Direction.WEST));
        assertThat(step).contains(new LandslideStep(ORIGIN, new BlockPos(-1, 0, 0), "tfc:dirt/fluvisol", true, true));
    }

    @Test
    void aFallStaysInPlaceAndKeepsTheBlockAsItsResult() {
        LandslideRules rules = LandslideGoldenTest.rules();
        LandslideView view = LandslideGoldenTest.view(LandslideDeterminismTest.box('G', Map.of()));
        assertThat(rules.step(view, ORIGIN, new LandslideDecision.Fall()))
            .contains(new LandslideStep(ORIGIN, ORIGIN, "tfc:dirt/fluvisol", false, false));
    }

    @Test
    void noDecisionAndNonSlidingKindsGiveNothing() {
        LandslideRules rules = LandslideGoldenTest.rules();
        LandslideView stone = LandslideGoldenTest.view(LandslideDeterminismTest.box('R', Map.of()));
        assertThat(rules.step(stone, ORIGIN, new LandslideDecision.Fall())).isEmpty();
        LandslideView dirt = LandslideGoldenTest.view(LandslideDeterminismTest.box('D', Map.of()));
        assertThat(rules.step(dirt, ORIGIN, new LandslideDecision.None())).isEmpty();
    }
}
