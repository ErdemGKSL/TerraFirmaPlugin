/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-DECO-25: vanilla's sign placement rotation (the placer looks along yaw 0 = south, 90 = west). */
class SignPlacementTest {
    @Test
    void segmentsAreSixteenStepsOfTwentyTwoAndAHalfDegrees() {
        assertThat(SignPlacement.segment(0f)).isZero();
        assertThat(SignPlacement.segment(22.5f)).isEqualTo(1);
        assertThat(SignPlacement.segment(11.2f)).isZero();
        assertThat(SignPlacement.segment(11.25f)).isEqualTo(1);
        assertThat(SignPlacement.segment(180f)).isEqualTo(8);
        assertThat(SignPlacement.segment(-90f)).isEqualTo(12);
        assertThat(SignPlacement.segment(360f)).isZero();
        assertThat(SignPlacement.segment("west")).isEqualTo(4);
        assertThat(SignPlacement.segment("north")).isEqualTo(8);
        assertThat(SignPlacement.segment("east")).isEqualTo(12);
    }

    @Test
    void aStandingSignFacesBackAtThePlacer() {
        assertThat(SignPlacement.standingRotation(0f)).as("looking south: the board faces north").isEqualTo(8);
        assertThat(SignPlacement.standingRotation(90f)).as("looking west: faces east").isEqualTo(12);
        assertThat(SignPlacement.standingRotation(180f)).isZero();
        assertThat(SignPlacement.standingRotation(-90f)).as("looking east: faces west").isEqualTo(4);
        assertThat(SignGeometry.standingYaw(SignPlacement.standingRotation(33f))).isEqualTo(SignGeometry.standingYaw(
            SignPlacement.segment(33f + 180f)));
    }

    @Test
    void aCeilingSignUnderASolidBlockHangsFromAVWhenItIsNotFull() {
        assertThat(SignPlacement.ceilingHanging(0f, false, true, null)).isEqualTo(new SignPlacement.Hanging(8, false));
        assertThat(SignPlacement.ceilingHanging(0f, false, false, null)).as("no full face above").isEqualTo(new SignPlacement.Hanging(8, true));
        assertThat(SignPlacement.ceilingHanging(0f, true, true, null)).as("sneaking").isEqualTo(new SignPlacement.Hanging(8, true));
    }

    @Test
    void underAHangingSignOnTheSameAxisItHangsPlainAlongThePlacersDirection() {
        // the sign above points along z; the placer looks south (z): not attached, rotation = the opposite direction (north 8)
        assertThat(SignPlacement.ceilingHanging(0f, false, false, "z")).isEqualTo(new SignPlacement.Hanging(8, false));
        assertThat(SignPlacement.ceilingHanging(10f, false, false, "z")).as("the direction snaps to south").isEqualTo(
            new SignPlacement.Hanging(8, false));
        assertThat(SignPlacement.ceilingHanging(10f, false, false, "x")).as("across the axis").isEqualTo(
            new SignPlacement.Hanging(SignPlacement.standingRotation(10f), true));
        assertThat(SignPlacement.ceilingHanging(10f, true, false, "z")).as("sneaking ignores it").isEqualTo(
            new SignPlacement.Hanging(SignPlacement.standingRotation(10f), true));
        assertThat(SignPlacement.rotationAxis(8)).isEqualTo("z");
        assertThat(SignPlacement.rotationAxis(4)).isEqualTo("x");
        assertThat(SignPlacement.rotationAxis(5)).isNull();
    }
}
