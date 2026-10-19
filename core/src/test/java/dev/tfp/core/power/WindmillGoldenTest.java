/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft) @ c3b4d9536. Golden values are derived by hand
 * from the TFC source named in each test's Javadoc; TFC has no data file for these numbers.
 */
package dev.tfp.core.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class WindmillGoldenTest {

    /**
     * TFC lines 34-37: SLOTS = 5, MIN_SPEED = 2 pi / 400, MAX_SPEED = 2 pi / 160 - MIN_SPEED, LERP_SPEED = MIN_SPEED / 100.
     */
    @Test
    void constantsMatchTfc() {
        assertThat(Windmill.SLOTS).isEqualTo(5);
        assertThat(Windmill.MIN_SPEED).isCloseTo(0.01570796f, within(1e-8f));
        assertThat(Windmill.MAX_SPEED).isCloseTo(0.02356195f, within(1e-8f));
        assertThat(Windmill.LERP_SPEED).isCloseTo(0.0001570796f, within(1e-10f));
        assertThat(Windmill.SAMPLE_INTERVAL_TICKS).isEqualTo(40);
    }

    /**
     * TFC line 73: Mth.map(count, 1, 5, 0, MAX_SPEED) + (count &gt; 1 ? MIN_SPEED : 0). One blade targets 0 (the map gives 0 and no
     * MIN_SPEED is added). Two blades give MAX/4 + MIN, three give MAX/2 + MIN, five give MAX + MIN = 2 pi / 160.
     */
    @Test
    void targetBeforeWindGrowsWithBlades() {
        assertThat(Windmill.targetBeforeWind(1)).isZero();
        assertThat(Windmill.targetBeforeWind(2)).isCloseTo(Windmill.MAX_SPEED / 4 + Windmill.MIN_SPEED, within(1e-7f));
        assertThat(Windmill.targetBeforeWind(3)).isCloseTo(Windmill.MAX_SPEED / 2 + Windmill.MIN_SPEED, within(1e-7f));
        assertThat(Windmill.targetBeforeWind(5)).isCloseTo((float) (2 * Math.PI) / 160, within(1e-7f));
    }

    @Test
    void bladeCountOutsideOneToFiveIsRejected() {
        assertThatThrownBy(() -> Windmill.targetBeforeWind(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Windmill.targetBeforeWind(6)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * TFC line 77: min(wind, 0.5) * 4. Wind 0.25 gives 1, and any wind at or above 0.5 gives the cap of 2.
     */
    @Test
    void windFactorIsCappedAtTwo() {
        assertThat(Windmill.windFactor(0f)).isZero();
        assertThat(Windmill.windFactor(0.25f)).isCloseTo(1f, within(1e-7f));
        assertThat(Windmill.windFactor(0.5f)).isCloseTo(2f, within(1e-7f));
        assertThat(Windmill.windFactor(3f)).isCloseTo(2f, within(1e-7f));
    }

    /**
     * Five blades at wind 0.5 (line 79): windFactor 2 times 2 pi / 160 gives 2 pi / 80 = 0.0785398 rad per tick. A single blade stays at
     * 0 whatever the wind is (line 73).
     */
    @Test
    void fiveBladesAtCappedWindReachTwoPiOverEighty() {
        assertThat(Windmill.targetSpeed(5, 0.5f)).isCloseTo((float) (2 * Math.PI) / 80, within(1e-6f));
        assertThat(Windmill.targetSpeed(1, 2f)).isZero();
    }

    /**
     * TFC lines 81-83: the speed moves toward the target by at most LERP_SPEED. An obstructed windmill stops at once (lines 69-70 of
     * WaterWheelBlockEntity lines 69-70: rotation.setSpeed(0) with no tick).
     */
    @Test
    void rampStepsByLerpSpeedAndObstructionStops() {
        assertThat(Windmill.step(0f, 1f, false)).isCloseTo(Windmill.LERP_SPEED, within(1e-10f));
        assertThat(Windmill.step(0.1f, 0f, true)).isZero();
    }

    /**
     * TFC lines 96-110: the disk is dH^2 + dy^2 &lt; 49 (radius 7), excluding the centre. A solid at (0, 6) is 36 &lt; 49 and obstructs, as does
     * (4, 5) at 41 and (6, 0) at 36. A solid at (5, 5) is 50, outside the disk, and does not. The centre is never asked.
     */
    @Test
    void obstructionDiskIsRadiusSevenAndSkipsTheCentre() {
        assertThat(Windmill.isObstructed((dH, dy) -> dH == 0 && dy == 6)).isTrue();
        assertThat(Windmill.isObstructed((dH, dy) -> dH == 4 && dy == 5)).isTrue();
        assertThat(Windmill.isObstructed((dH, dy) -> dH == 6 && dy == 0)).isTrue();
        assertThat(Windmill.isObstructed((dH, dy) -> dH == 5 && dy == 5)).isFalse();
        assertThat(Windmill.isObstructed((dH, dy) -> dH == 0 && dy == 0)).isFalse();
        assertThat(Windmill.isObstructed((dH, dy) -> {
            if (dH == 0 && dy == 0) {
                throw new AssertionError("the centre must not be asked");
            }
            return false;
        })).isFalse();
    }
}
