/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.world.Direction;
import org.junit.jupiter.api.Test;

class RotationAnglesSpinTest {
    /** 30 rpm of the creative motor: 2 pi * 30 / 1200 radians per tick. */
    private static final float RPM30 = (float) (2 * Math.PI * 30 / 1200);

    @Test
    void rateFollowsTfcAxisStepAndTheRendererSign() {
        // TFC: Rotation.angle = axisStep * angle; the renderer turns by minus it.
        assertThat(RotationAnglesSpin.rate(Direction.EAST, RPM30)).isCloseTo(-RPM30, within(1e-9));
        assertThat(RotationAnglesSpin.rate(Direction.WEST, RPM30)).isCloseTo(RPM30, within(1e-9));
        assertThat(RotationAnglesSpin.rate(Direction.UP, -RPM30)).isCloseTo(RPM30, within(1e-9));
        assertThat(RotationAnglesSpin.rate(Direction.NORTH, 0f)).isZero();
    }

    @Test
    void lazyTrackEqualsTfcSteppedAngle() {
        // TFC: angle += speed each tick, wrapped to [0, 2 pi]; the visual angle is minus the stepped angle (mod 2 pi).
        float angle = 0;
        float speed = RPM30;
        for (int t = 1; t <= 5000; t++) {
            angle = RotationAngles.advance(angle, speed);
            double visual = RotationAnglesSpin.rate(Direction.EAST, speed) * t;
            double stepped = -angle;
            double diff = Math.IEEEremainder(visual - stepped, 2 * Math.PI);
            assertThat(diff).isCloseTo(0, within(1e-2));
        }
    }

    @Test
    void retargetKeepsTheAngle() {
        var t = RotationAnglesSpin.Track.rest(10, 1.0).retarget(10, 0.1);
        var u = t.retarget(30, -0.2);
        assertThat(u.phi()).isCloseTo(1.0 + 0.1 * 20, within(1e-12));
        assertThat(u.phiAt(40)).isCloseTo(3.0 - 2.0, within(1e-12));
    }

    @Test
    void segmentSweepsAtMostTwoShortArcsAndEndsOnTheLine() {
        var t = RotationAnglesSpin.Track.rest(0, 0).retarget(0, RPM30);
        var u = RotationAnglesSpin.plan(t, 0);
        assertThat(u.duration()).isEqualTo(37);
        assertThat(u.nextTick()).isEqualTo(37);
        assertThat(u.phi()).isCloseTo((double) RPM30 * 37, within(1e-9));
        // each of the two rotations turns under 180 degrees over the segment
        assertThat(Math.abs(u.halfAngle() - t.phiAt(0) / 2)).isLessThan(Math.PI);
        assertThat(Math.abs(u.phi() - t.phiAt(0))).isLessThanOrEqualTo(RotationAnglesSpin.MAX_SWEEP);
    }

    @Test
    void successiveSegmentsAreContinuous() {
        var t = RotationAnglesSpin.Track.rest(5, 0.3).retarget(5, -0.07);
        long at = 5;
        double shown = t.phiAt(at);
        for (int i = 0; i < 20; i++) {
            var u = RotationAnglesSpin.plan(t, at);
            assertThat(Math.abs(u.phi() - shown)).isLessThanOrEqualTo(RotationAnglesSpin.MAX_SWEEP + 1e-9);
            shown = u.phi();
            at = u.nextTick();
            assertThat(shown).isCloseTo(t.phiAt(at), within(1e-9));
        }
    }

    @Test
    void restingShaftSnapsOnceAndStopsRefreshing() {
        var t = RotationAnglesSpin.Track.rest(0, 2.5);
        var u = RotationAnglesSpin.plan(t, 100);
        assertThat(u.duration()).isZero();
        assertThat(u.nextTick()).isEqualTo(-1);
        assertThat(u.phi()).isCloseTo(2.5, within(1e-12));
    }

    @Test
    void slowShaftRefreshesAtTheCap() {
        var t = RotationAnglesSpin.Track.rest(0, 0).retarget(0, 0.001);
        assertThat(RotationAnglesSpin.plan(t, 0).duration()).isEqualTo(RotationAnglesSpin.MAX_SEGMENT_TICKS);
    }

    @Test
    void fastShaftNeverGetsAZeroDuration() {
        var t = RotationAnglesSpin.Track.rest(0, 0).retarget(0, 7.0);
        assertThat(RotationAnglesSpin.plan(t, 0).duration()).isEqualTo(1);
    }

    @Test
    void packetCountPerSpeedChange() {
        // creative motor top speed: one packet now, then one per 37 ticks: 200 ticks = 6 packets, a rest is one packet
        assertThat(RotationAnglesSpin.packetsFor(RPM30, 200)).isEqualTo(6);
        assertThat(RotationAnglesSpin.packetsFor(0, 10_000)).isEqualTo(1);
        // far fewer than one packet per tick
        assertThat(RotationAnglesSpin.packetsFor(RPM30, 1200)).isLessThan(40);
    }
}
