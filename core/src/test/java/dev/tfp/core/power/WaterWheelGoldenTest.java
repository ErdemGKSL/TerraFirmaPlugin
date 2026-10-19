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
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.worldgen.river.Flow;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaterWheelGoldenTest {

    /** A window where every cell is open except those given. Offsets are {@code (dH, dy)}. */
    private static WaterWheel.CellAt window(java.util.Map<String, WaterWheel.Cell> cells) {
        return (dH, dy) -> cells.getOrDefault(dH + "," + dy, WaterWheel.Cell.of(WaterWheel.Kind.OPEN));
    }

    /** Every bottom-row cell (dy < 0) of the 5 x 5 window set to {@code cell}. */
    private static java.util.Map<String, WaterWheel.Cell> bottomRow(WaterWheel.Cell cell) {
        final java.util.Map<String, WaterWheel.Cell> map = new java.util.HashMap<>();
        for (int dH = -2; dH <= 2; dH++) {
            for (int dy = -2; dy < 0; dy++) {
                map.put(dH + "," + dy, cell);
            }
        }
        return map;
    }

    /**
     * TFC {@code MAX_SPEED = Mth.TWO_PI / (4 * 20)}, {@code LERP_SPEED = MAX_SPEED / (20 * 20)}, {@code MAX_FLOW = 10f} and the 40 tick
     * sample (WaterWheelBlockEntity lines 33-36, 44). MAX_SPEED is 2 pi / 80 = 0.0785398 rad per tick.
     */
    @Test
    void constantsMatchTfc() {
        assertThat(WaterWheel.MAX_SPEED).isCloseTo(0.07853982f, within(1e-8f));
        assertThat(WaterWheel.LERP_SPEED).isCloseTo(0.07853982f / 400f, within(1e-10f));
        assertThat(WaterWheel.MAX_FLOW).isEqualTo(10f);
        assertThat(WaterWheel.SAMPLE_INTERVAL_TICKS).isEqualTo(40);
    }

    /**
     * Ten bottom river cells all flowing NNN on an X wheel. TFC line 127: flowRate = -flow.vector.z = -(-sin(pi/2)) = 1 per cell,
     * all in the bottom rows (dy &lt; 0), so contributing = 10, obstruction = 0. Line 159: -max(0, 10 - 0) = -10. Line 56:
     * target = -10 * MAX_SPEED / 10 = -MAX_SPEED.
     */
    @Test
    void fullBottomFlowOnXWheelTurnsAtMinusMaxSpeed() {
        final Optional<Float> net = WaterWheel.netFlow(ShaftAxis.X,
            window(bottomRow(WaterWheel.Cell.river(Flow.NNN))));
        assertThat(net).isPresent();
        assertThat(net.get()).isCloseTo(-10f, within(1e-4f));
        assertThat(WaterWheel.targetSpeed(net.get())).isCloseTo(-WaterWheel.MAX_SPEED, within(1e-7f));
    }

    /**
     * EEE flows along +x, so on an X wheel its flowRate is -vector.z = -(-sin 0) = 0 (line 127): no contribution. The same flow on
     * a Z wheel is vector.x = cos 0 = 1 per cell (line 127), giving -10 (line 159).
     */
    @Test
    void flowAlongTheAxleDoesNotTurnTheWheel() {
        final Optional<Float> onX = WaterWheel.netFlow(ShaftAxis.X,
            window(bottomRow(WaterWheel.Cell.river(Flow.EEE))));
        assertThat(onX.get()).isCloseTo(0f, within(1e-6f));

        final Optional<Float> onZ = WaterWheel.netFlow(ShaftAxis.Z,
            window(bottomRow(WaterWheel.Cell.river(Flow.EEE))));
        assertThat(onZ.get()).isCloseTo(-10f, within(1e-4f));
    }

    /**
     * WWW is angle pi, so vector.x = cos pi = -1. On a Z wheel each bottom cell gives -1 (line 127), so contributing = -10. Line 163:
     * the contributing flow is not positive, so the result is -min(0, -10 + 0) = 10, the wheel turns the other way.
     */
    @Test
    void reversedFlowTurnsTheWheelTheOtherWay() {
        final Optional<Float> net = WaterWheel.netFlow(ShaftAxis.Z,
            window(bottomRow(WaterWheel.Cell.river(Flow.WWW))));
        assertThat(net.get()).isCloseTo(10f, within(1e-4f));
        assertThat(WaterWheel.targetSpeed(net.get())).isCloseTo(WaterWheel.MAX_SPEED, within(1e-7f));
    }

    /**
     * Nine bottom NNN river cells and one still water cell at (-1, -1). Still water below the centre adds 0.25 to obstruction (line
     * 142), so contributing = 9 and obstruction = 0.25; line 159 gives -(9 - 0.25) = -8.75.
     */
    @Test
    void stillWaterBelowTheCentreObstructsByAQuarter() {
        final java.util.Map<String, WaterWheel.Cell> cells = bottomRow(WaterWheel.Cell.river(Flow.NNN));
        cells.put("-1,-1", WaterWheel.Cell.of(WaterWheel.Kind.STILL_WATER));
        final Optional<Float> net = WaterWheel.netFlow(ShaftAxis.X, window(cells));
        assertThat(net.get()).isCloseTo(-8.75f, within(1e-4f));
    }

    /**
     * Five NNN river cells in the top row (dy = 1), with the ten bottom NNN cells. Top-row flow is an obstruction of |flowRate| = 1 each
     * (line 135), so obstruction = 5 and contributing = 10; line 159 gives -(10 - 5) = -5.
     */
    @Test
    void riverFlowAboveTheCentreObstructs() {
        final java.util.Map<String, WaterWheel.Cell> cells = bottomRow(WaterWheel.Cell.river(Flow.NNN));
        for (int dH = -2; dH <= 2; dH++) {
            cells.put(dH + ",1", WaterWheel.Cell.river(Flow.NNN));
        }
        final Optional<Float> net = WaterWheel.netFlow(ShaftAxis.X, window(cells));
        assertThat(net.get()).isCloseTo(-5f, within(1e-4f));
    }

    /**
     * Still water above the centre adds a full 1 to obstruction (line 142), so one still water cell at (0, 1) on top of ten bottom
     * NNN cells gives -(10 - 1) = -9.
     */
    @Test
    void stillWaterAboveTheCentreObstructsByOne() {
        final java.util.Map<String, WaterWheel.Cell> cells = bottomRow(WaterWheel.Cell.river(Flow.NNN));
        cells.put("0,1", WaterWheel.Cell.of(WaterWheel.Kind.STILL_WATER));
        final Optional<Float> net = WaterWheel.netFlow(ShaftAxis.X, window(cells));
        assertThat(net.get()).isCloseTo(-9f, within(1e-4f));
    }

    /**
     * A solid block at (2, -2) is not air and has a collision shape, so it falls into TFC's "any other block" branch and the scan
     * returns null (WaterWheelBlockEntity line 151). Here that is empty, and the speed is held at 0 (lines 69-70).
     */
    @Test
    void solidBlockInTheWindowObstructsTheWheel() {
        final java.util.Map<String, WaterWheel.Cell> cells = bottomRow(WaterWheel.Cell.river(Flow.NNN));
        cells.put("2,-2", WaterWheel.Cell.of(WaterWheel.Kind.SOLID));
        assertThat(WaterWheel.netFlow(ShaftAxis.X, window(cells))).isEmpty();
        assertThat(WaterWheel.step(0.05f, 0.05f, true)).isZero();
    }

    /**
     * No flow and no obstruction: contributing = 0 and obstruction = 0, so line 163 gives -min(0, 0) = 0 (negated zero, so it is checked
     * with a tolerance).
     */
    @Test
    void emptyWindowHoldsZero() {
        final Optional<Float> net = WaterWheel.netFlow(ShaftAxis.X, window(java.util.Map.of()));
        assertThat(net.get()).isCloseTo(0f, within(1e-9f));
    }

    /**
     * TFC lines 77-79: the speed moves toward the target by at most LERP_SPEED = MAX_SPEED / 400 per tick. It takes 400 ticks from 0 to
     * MAX_SPEED, and the 400th tick lands exactly on the target. An obstructed wheel stops at once (line 70).
     */
    @Test
    void speedRampsInFourHundredTicks() {
        assertThat(WaterWheel.step(0f, WaterWheel.MAX_SPEED, false)).isCloseTo(WaterWheel.LERP_SPEED, within(1e-9f));

        float speed = 0f;
        for (int i = 0; i < 399; i++) {
            speed = WaterWheel.step(speed, WaterWheel.MAX_SPEED, false);
        }
        assertThat(speed).isLessThan(WaterWheel.MAX_SPEED);
        speed = WaterWheel.step(speed, WaterWheel.MAX_SPEED, false);
        assertThat(speed).isCloseTo(WaterWheel.MAX_SPEED, within(1e-7f));
    }
}
