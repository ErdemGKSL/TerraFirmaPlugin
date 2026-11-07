/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (getRotationAngle)
 */
package dev.tfp.core.quern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-PROC-04: the handstone turn in interpolated steps, against TFC's per-tick angle. */
class QuernVisualsTest {

    @Test
    void stepsCoverTheGrindAndEndWhereTfcEnds() {
        List<QuernVisuals.Step> steps = QuernVisuals.spin();
        assertThat(steps).hasSize(QuernVisuals.SPIN_STEPS);
        int t = 0;
        for (QuernVisuals.Step s : steps) {
            assertThat(s.delay()).isEqualTo(t);
            assertThat(s.duration()).isPositive();
            t += s.duration();
        }
        assertThat(t).isEqualTo(QuernRules.TICKS_PER_GRIND);
        assertThat(steps.getLast().angle()).isZero();
    }

    @Test
    void eachStepEndsAtTfcsAngleForThatTick() {
        double previous = 0;
        for (QuernVisuals.Step s : QuernVisuals.spin()) {
            int end = s.delay() + s.duration();
            double tfc = QuernVisuals.angle(end);
            // the step's end angle is TFC's angle at that tick, up to the rounding of the step boundary (half a tick)
            double diff = Math.abs(Math.IEEEremainder(s.angle() - tfc, 2 * Math.PI));
            assertThat(diff).isLessThan(2 * Math.PI / QuernRules.TICKS_PER_GRIND);
            // under a half turn per step, so the display interpolation takes TFC's direction
            double turn = Math.abs(Math.IEEEremainder(s.angle() - previous, 2 * Math.PI));
            assertThat(turn).isLessThan(Math.PI);
            previous = s.angle();
        }
    }

    @Test
    void tfcAngleIsOneTurnPerGrind() {
        assertThat(QuernVisuals.angle(0)).isZero();
        assertThat(QuernVisuals.angle(45)).isCloseTo(-Math.PI, within(1e-12));
        assertThat(QuernVisuals.angle(90)).isZero();
        assertThat(QuernVisuals.angle(500)).isZero();
    }
}
