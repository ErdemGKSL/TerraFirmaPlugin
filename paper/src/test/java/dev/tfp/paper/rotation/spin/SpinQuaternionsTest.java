/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.rotation.RotationAxis;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-MECH-12: the spinning display's rotations lay TFC's shaft on its axis and turn it about the positive axis. */
class SpinQuaternionsTest {

    /** The datagen {@code RotationBlocks.rotation} values of the axle's old static element (generated axle.yml). */
    @Test
    void theBaseIsTheStaticElementsRotation() {
        Map<RotationAxis, double[]> yml = Map.of(
            RotationAxis.X, new double[] {0, 0.707107, 0, 0.707107},
            RotationAxis.Y, new double[] {0, 0.707107, -0.707107, 0},
            RotationAxis.Z, new double[] {0, 1, 0, 0});
        yml.forEach((axis, q) -> {
            double[] b = SpinQuaternions.base(axis);
            for (int i = 0; i < 4; i++) {
                assertThat(b[i]).as("%s %d", axis, i).isCloseTo(q[i], within(1e-6));
            }
        });
    }

    @Test
    void leftTimesRightTurnsTheBasedModelByTwiceTheHalfAngle() {
        double half = 0.4;
        for (RotationAxis axis : RotationAxis.values()) {
            float[] l = SpinQuaternions.left(axis, half);
            float[] r = SpinQuaternions.right(axis, half);
            double[] total = SpinQuaternions.mul(toDouble(l), toDouble(r));
            double[] expected = SpinQuaternions.mul(SpinQuaternions.about(axis, 2 * half), SpinQuaternions.base(axis));
            for (int i = 0; i < 4; i++) {
                assertThat(total[i]).as("%s %d", axis, i).isCloseTo(expected[i], within(1e-6));
            }
            // the model's shaft (z) lies on the axis after the base
            double[] z = rotate(SpinQuaternions.base(axis), new double[] {0, 0, 1});
            double along = switch (axis) {
                case X -> z[0];
                case Y -> z[1];
                case Z -> z[2];
            };
            assertThat(Math.abs(along)).isCloseTo(1, within(1e-9));
        }
    }

    @Test
    void theTurnIsAboutThePositiveAxis() {
        // a quarter turn about +y takes +x to -z (right hand)
        double[] v = rotate(SpinQuaternions.about(RotationAxis.Y, Math.PI / 2), new double[] {1, 0, 0});
        assertThat(v[2]).isCloseTo(-1, within(1e-9));
    }

    private static double[] toDouble(float[] f) {
        return new double[] {f[0], f[1], f[2], f[3]};
    }

    /** q v q*. */
    private static double[] rotate(double[] q, double[] v) {
        double[] p = SpinQuaternions.mul(SpinQuaternions.mul(q, new double[] {v[0], v[1], v[2], 0}),
            new double[] {-q[0], -q[1], -q[2], q[3]});
        return new double[] {p[0], p[1], p[2]};
    }
}
