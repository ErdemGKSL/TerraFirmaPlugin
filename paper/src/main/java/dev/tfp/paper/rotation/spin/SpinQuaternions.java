/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/AxleBlockEntityRenderer.java @ c3b4d9536 (applyRotation: the
 *       shaft model lies along z, is turned onto its axis and spins about it)
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.rotation.RotationAxis;

/**
 * The display rotations of a spinning shaft (PAR-MECH-12). The display's transformation is {@code left * scale * right}; the
 * shaft shows {@code phi = 2 * halfAngle} about its positive axis when left = {@code R(axis, halfAngle)} and right =
 * {@code R(axis, halfAngle) * base}, where {@code base} lays TFC's z-aligned shaft model onto the axis exactly as the datagen
 * blockstate turns do ({@code RotationBlocks.rotation}: x 0/y 90 for x, x 90 for y, none for z, then the item display's half
 * turn). The client interpolates each rotation along its own shortest arc, so each may turn just under half a turn per segment.
 * Quaternions are {x, y, z, w}.
 */
public final class SpinQuaternions {
    private SpinQuaternions() {
    }

    /** The rotation that lays the shaft model onto {@code axis} (the datagen {@code RotationBlocks.rotation} of its x/y turn). */
    public static double[] base(RotationAxis axis) {
        return switch (axis) {
            case X -> blockstate(0, 90);
            case Y -> blockstate(90, 0);
            case Z -> blockstate(0, 0);
        };
    }

    /** {@code RotationBlocks.rotation}: vanilla's rotateYXZ(-y, -x, 0) followed by the item display's half turn about y. */
    static double[] blockstate(int xDegrees, int yDegrees) {
        return mul(mul(about(0, 1, 0, Math.toRadians(-yDegrees)), about(1, 0, 0, Math.toRadians(-xDegrees))),
            about(0, 1, 0, Math.PI));
    }

    /** The left rotation: {@code halfAngle} radians about the positive axis. */
    public static float[] left(RotationAxis axis, double halfAngle) {
        return floats(about(axis, halfAngle));
    }

    /** The right rotation: {@code halfAngle} radians about the positive axis after {@link #base}. */
    public static float[] right(RotationAxis axis, double halfAngle) {
        return floats(mul(about(axis, halfAngle), base(axis)));
    }

    static double[] about(RotationAxis axis, double radians) {
        return switch (axis) {
            case X -> about(1, 0, 0, radians);
            case Y -> about(0, 1, 0, radians);
            case Z -> about(0, 0, 1, radians);
        };
    }

    /** A rotation of {@code radians} about a unit axis. */
    static double[] about(double ax, double ay, double az, double radians) {
        double h = radians / 2;
        double s = Math.sin(h);
        return new double[] {ax * s, ay * s, az * s, Math.cos(h)};
    }

    /** The Hamilton product {@code a * b} (b applied first). */
    static double[] mul(double[] a, double[] b) {
        return new double[] {
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static float[] floats(double[] q) {
        return new float[] {(float) q[0], (float) q[1], (float) q[2], (float) q[3]};
    }
}
