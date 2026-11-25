/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-MECH-26: every part's display transform against TFC's renderer matrices (CrankshaftBlockEntityRenderer): the facing
 * turn YP(180 - 90 * get2DDataValue), the wheel's XP(realAngle + pi), the shaft's translate and XP(raiseAngle), the stub
 * cuboid 0..6/16 toward the counter-clockwise side.
 */
class CrankPartsTest {
    static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    static final List<Direction> HANDS = List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
    static final double[][] POINTS = {{0.1, 0.2, 0.3}, {-0.4, 0.05, 0.25}, {0.3, -0.3, -0.1}};

    /** Where the display puts model point {@code v} (block-centred): {@code t + L * (s . (R * (Ypi * v)))}. */
    static double[] shown(CrankParts.Pose p, double[] v) {
        double[] a = CrankParts.rotate(CrankParts.displayTurn(), v[0], v[1], v[2]);
        a = CrankParts.rotate(d(p.right()), a[0], a[1], a[2]);
        a = new double[] {a[0] * p.scale()[0], a[1] * p.scale()[1], a[2] * p.scale()[2]};
        a = CrankParts.rotate(d(p.left()), a[0], a[1], a[2]);
        return new double[] {a[0] + p.translation()[0], a[1] + p.translation()[1], a[2] + p.translation()[2]};
    }

    static double[] d(float[] q) {
        return new double[] {q[0], q[1], q[2], q[3]};
    }

    static double[] xp(double angle, double[] v) {
        return CrankParts.rotate(SpinQuaternions.about(1, 0, 0, angle), v[0], v[1], v[2]);
    }

    static double[] yf(Direction facing, double[] v) {
        return CrankParts.rotate(CrankParts.facingTurn(facing), v[0], v[1], v[2]);
    }

    static void close(double[] a, double[] b) {
        for (int i = 0; i < 3; i++) {
            assertThat(a[i]).isCloseTo(b[i], within(1e-5));
        }
    }

    @Test
    void theWheelIsTfcsYfTimesXpOfTheRealAnglePlusPi() {
        for (Direction facing : FACINGS) {
            for (Direction hand : HANDS) {
                for (double source : new double[] {0, 0.4, 2.0, 3.5, 5.9, 13.1, -7.7}) {
                    double phi = RotationAnglesSpin.rate(hand, 1f) * source;
                    CrankParts.Pose pose = CrankParts.wheel(facing, phi / 2);
                    float real = CrankParts.realAngle(hand, source, facing);
                    for (double[] v : POINTS) {
                        close(shown(pose, v), yf(facing, xp(real + Math.PI, v)));
                    }
                }
            }
        }
    }

    @Test
    void theShaftPartsAreTfcsTranslateAndRaise() {
        for (Direction facing : FACINGS) {
            for (double source : new double[] {0, 1.0, 2.2, 4.0, 5.5}) {
                CrankshaftRules.ShaftMovement m = CrankParts.movement(Direction.EAST, source, facing);
                double[] pivot = {8 / 16.0 + 1 / 16.0 - 0.5, 0, m.lengthEH() - 0.5};
                for (double[] v : POINTS) {
                    double[] slider = {pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2]};
                    close(shown(CrankParts.slider(facing, m), v), yf(facing, slider));
                    double[] raised = xp(m.raiseAngle(), v);
                    double[] arm = {pivot[0] + raised[0], pivot[1] + raised[1], pivot[2] + raised[2]};
                    close(shown(CrankParts.arm(facing, m), v), yf(facing, arm));
                }
            }
        }
    }

    @Test
    void theMovementIsTfcsOfTheRealAngle() {
        // source 0 rests the shaft furthest withdrawn: real angle pi, lengthEH 1, no raise
        for (Direction facing : FACINGS) {
            assertThat(CrankParts.realAngle(Direction.EAST, 0, facing)).isCloseTo((float) Math.PI, within(1e-6f));
            CrankshaftRules.ShaftMovement rest = CrankParts.movement(Direction.EAST, 0, facing);
            assertThat(rest.lengthEH()).isCloseTo(1f, within(1e-5f));
            assertThat(rest.raiseAngle()).isCloseTo(0f, within(1e-3f));
        }
        float node = CrankParts.nodeAngle(Direction.WEST, 1.25);
        assertThat(node).isCloseTo((float) (2 * Math.PI - 1.25), within(1e-5f));
        assertThat(CrankParts.movement(Direction.WEST, 1.25, Direction.SOUTH))
            .isEqualTo(CrankshaftRules.shaftMovement(CrankshaftRules.realRotationAngle(node, Direction.SOUTH)));
    }

    /** The stub: the axle model's box (4/16 thick along z) lands on TFC's 0..6/16 extension cuboid, turned with the wheel. */
    @Test
    void theStubCoversTfcsExtensionCuboid() {
        for (Direction facing : FACINGS) {
            for (double source : new double[] {0, 0.7, 2.9}) {
                double phi = RotationAnglesSpin.rate(Direction.EAST, 1f) * source;
                float real = CrankParts.realAngle(Direction.EAST, source, facing);
                List<double[]> ours = new ArrayList<>();
                List<double[]> tfc = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    double sx = (i & 1) == 0 ? -1 : 1;
                    double sy = (i & 2) == 0 ? -1 : 1;
                    double sz = (i & 4) == 0 ? -1 : 1;
                    ours.add(shown(CrankParts.stub(facing, phi / 2), new double[] {sx * 2 / 16.0, sy * 2 / 16.0, sz * 0.5}));
                    double[] corner = {(sx < 0 ? 0 : 6 / 16.0) - 0.5, sy * 2 / 16.0, sz * 2 / 16.0};
                    tfc.add(yf(facing, xp(real + Math.PI, corner)));
                }
                sameCorners(ours, tfc);
            }
        }
    }

    static void sameCorners(List<double[]> a, List<double[]> b) {
        Comparator<double[]> order = Comparator.<double[]>comparingDouble(v -> Math.round(v[0] * 1e4))
            .thenComparingDouble(v -> Math.round(v[1] * 1e4)).thenComparingDouble(v -> Math.round(v[2] * 1e4));
        List<double[]> x = new ArrayList<>(a);
        List<double[]> y = new ArrayList<>(b);
        x.sort(order);
        y.sort(order);
        for (int i = 0; i < x.size(); i++) {
            close(x.get(i), y.get(i));
        }
    }

    @Test
    void theStubPointsAtTheSinkFace() {
        assertThat(CrankParts.stub(Direction.NORTH, 0).translation()[0]).isCloseTo(-5 / 16f, within(1e-6f)); // west
        assertThat(CrankParts.stub(Direction.EAST, 0).translation()[2]).isCloseTo(-5 / 16f, within(1e-6f)); // north
        assertThat(CrankParts.stub(Direction.SOUTH, 0).translation()[0]).isCloseTo(5 / 16f, within(1e-6f)); // east
        assertThat(CrankParts.stub(Direction.WEST, 0).translation()[2]).isCloseTo(5 / 16f, within(1e-6f)); // south
    }
}
