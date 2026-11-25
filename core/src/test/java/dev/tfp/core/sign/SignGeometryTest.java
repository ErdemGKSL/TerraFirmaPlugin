/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-DECO-03: vanilla's sign yaw and front-side rule. */
class SignGeometryTest {
    @Test
    void yawsFollowVanilla() {
        assertThat(SignGeometry.standingYaw(0)).isEqualTo(0f);
        assertThat(SignGeometry.standingYaw(4)).isEqualTo(90f);
        assertThat(SignGeometry.standingYaw(15)).isEqualTo(337.5f);
        assertThat(SignGeometry.standingYaw(-1)).isEqualTo(337.5f);
        assertThat(SignGeometry.wallYaw("south")).isEqualTo(0f);
        assertThat(SignGeometry.wallYaw("west")).isEqualTo(90f);
        assertThat(SignGeometry.wallYaw("north")).isEqualTo(180f);
        assertThat(SignGeometry.wallYaw("east")).isEqualTo(270f);
    }

    @Test
    void aPlayerSouthOfASouthFacingSignSeesTheFront() {
        // sign at block (0,0), facing south (yaw 0): its front is the +z side
        assertThat(SignGeometry.isFacingFront(0f, 0, 0, 0.5, 3.5)).isTrue();
        assertThat(SignGeometry.isFacingFront(0f, 0, 0, 0.5, -2.5)).isFalse();
        assertThat(SignGeometry.isFacingFront(180f, 0, 0, 0.5, -2.5)).isTrue();
        assertThat(SignGeometry.isFacingFront(90f, 0, 0, -3.5, 0.5)).isTrue(); // west
        assertThat(SignGeometry.isFacingFront(270f, 0, 0, 4.5, 0.5)).isTrue(); // east
    }
}
