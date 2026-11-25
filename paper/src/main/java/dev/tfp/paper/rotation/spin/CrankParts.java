/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/CrankshaftBlockEntityRenderer.java @ c3b4d9536 (render: the
 *       facing turn YP(180 - 90 * get2DDataValue), the wheel's XP(rotationAngle + pi), the 6/16 axle extension toward the
 *       counter-clockwise side, the shaft's translate(8/16 + armRadius, 8/16, lengthEH) and XP(raiseAngle) of the arm),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java @ c3b4d9536
 *       (calculateRealRotationAngle, calculateShaftMovement),
 *       src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (angle(rotation, partialTick))
 */
package dev.tfp.paper.rotation.spin;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.machine.RotationAngles;
import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.Direction;

/**
 * The display transforms of a crankshaft's moving parts (PAR-MECH-26), from TFC's renderer. A display shows its item as
 * {@code translation + left * scale * right * Yπ * m} ({@code Yπ} is the item display's own half turn), so a part TFC draws as
 * {@code A * m} about the block centre gets {@code left * scale * right = A * Yπ}.
 *
 * <ul>
 *   <li>The wheel: TFC turns it by {@code XP(realAngle + pi)} after the facing turn {@code Yf}. With
 *       {@code realAngle = calculateRealRotationAngle} the turn is {@code -node} for north and east and {@code node} for south
 *       and west, about {@code Yf * x}, which is the negative axis for those two: for every facing the wheel turns by
 *       {@code phi = -step(direction) * sourceAngle} about the positive x or z axis, the angle an axle of the same rotation
 *       shows ({@link RotationAnglesSpin#rate}). Its two halves go to the left and right rotation, as the axles do.</li>
 *   <li>The axle stub (TFC: a {@code 0..6/16} cuboid toward the sink face in the turned frame, drawn with the axle's texture):
 *       the axle's own model (along z, 4/16 thick) scaled to 6/16 along the turning axis and centred 5/16 from the block
 *       centre toward the sink face; it turns with the wheel.</li>
 *   <li>The shaft: the connecting box and piston (the slider) at {@code (8/16 + armRadius, 8/16, lengthEH)} of the shaft block
 *       in the turned frame, the arm there too, raised by {@code XP(raiseAngle)}; both from
 *       {@link CrankshaftRules#shaftMovement} of {@link #realAngle}.</li>
 * </ul>
 *
 * Quaternions are {x, y, z, w}.
 */
public final class CrankParts {
    /** The display items (written by the datagen {@code CrankshaftBlock}). */
    public static final String WHEEL_ITEM = "tfp:render/crankshaft/wheel";
    public static final String SLIDER_ITEM = "tfp:render/crankshaft/slider";
    public static final String ARM_ITEM = "tfp:render/crankshaft/arm";
    /** TFC's brass attachment on the piston when the shaft drives a steel pump; it moves with the slider. */
    public static final String PUMP_ITEM = "tfp:render/crankshaft/pump";
    /** TFC's {@code armRadius}: the shaft's connecting point is this far off the block's middle along x. */
    static final float ARM_RADIUS = 1 / 16f;
    /** TFC's axle extension: 6/16 long, from the sink face to 6/16 into the base. */
    static final float STUB_LENGTH = 6 / 16f;
    static final double STUB_CENTRE = 0.5 - STUB_LENGTH / 2.0;

    /** One display's transformation. */
    public record Pose(float[] translation, float[] left, float[] scale, float[] right) {
    }

    private CrankParts() {
    }

    /** TFC's facing turn: {@code Axis.YP.rotationDegrees(180 - 90 * get2DDataValue)} (south 0, west 1, north 2, east 3). */
    static double[] facingTurn(Direction facing) {
        int index = switch (facing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException("not horizontal: " + facing);
        };
        return SpinQuaternions.about(0, 1, 0, Math.toRadians(180 - 90 * index));
    }

    /** The world axis the wheel turns about: {@code Yf * x}, x for north and south, z for east and west. */
    public static RotationAxis wheelAxis(Direction facing) {
        return facing == Direction.NORTH || facing == Direction.SOUTH ? RotationAxis.X : RotationAxis.Z;
    }

    /** The item display's half turn about y. */
    static double[] displayTurn() {
        return SpinQuaternions.about(0, 1, 0, Math.PI);
    }

    /** The wheel at {@code halfAngle} (it shows {@code 2 * halfAngle} about the positive {@link #wheelAxis}). */
    public static Pose wheel(Direction facing, double halfAngle) {
        RotationAxis axis = wheelAxis(facing);
        double[] half = SpinQuaternions.about(axis, halfAngle);
        double[] right = SpinQuaternions.mul(SpinQuaternions.mul(half, facingTurn(facing)), displayTurn());
        return new Pose(new float[3], floats(half), new float[] {1, 1, 1}, floats(right));
    }

    /** The axle stub at {@code halfAngle}: the axle model shortened along the wheel's axis, toward the sink face. */
    public static Pose stub(Direction facing, double halfAngle) {
        RotationAxis axis = wheelAxis(facing);
        double[] half = SpinQuaternions.about(axis, halfAngle);
        double[] right = SpinQuaternions.mul(half, SpinQuaternions.base(axis));
        Direction sink = CrankshaftRules.sinkFace(facing);
        float[] t = {(float) (sink.dx() * STUB_CENTRE), 0f, (float) (sink.dz() * STUB_CENTRE)};
        float[] scale = axis == RotationAxis.X ? new float[] {STUB_LENGTH, 1, 1} : new float[] {1, 1, STUB_LENGTH};
        return new Pose(t, floats(half), scale, floats(right));
    }

    /** The connecting box and piston at the shaft's {@code movement}. */
    public static Pose slider(Direction facing, CrankshaftRules.ShaftMovement movement) {
        double[] yf = facingTurn(facing);
        return new Pose(pivot(yf, movement), floats(yf), new float[] {1, 1, 1}, floats(displayTurn()));
    }

    /** The arm at the shaft's {@code movement}: raised by {@code raiseAngle} about the turned x at the connecting point. */
    public static Pose arm(Direction facing, CrankshaftRules.ShaftMovement movement) {
        double[] yf = facingTurn(facing);
        double[] left = SpinQuaternions.mul(yf, SpinQuaternions.about(1, 0, 0, movement.raiseAngle()));
        return new Pose(pivot(yf, movement), floats(left), new float[] {1, 1, 1}, floats(displayTurn()));
    }

    /** The connecting point from the block centre: {@code Yf * (armRadius, 0, lengthEH - 1/2)}. */
    static float[] pivot(double[] yf, CrankshaftRules.ShaftMovement movement) {
        double[] p = rotate(yf, ARM_RADIUS, 0, movement.lengthEH() - 0.5);
        return new float[] {(float) p[0], (float) p[1], (float) p[2]};
    }

    /**
     * TFC's node angle {@code Rotation.angle(rotation, partialTick)} of a source angle: the axis step of the rotation's
     * direction times it, in {@code [0, 2pi)}.
     *
     * @param direction the crank's incoming rotation direction
     * @param source    the source's unbounded angle
     */
    public static float nodeAngle(Direction direction, double source) {
        double step = -RotationAnglesSpin.rate(direction, 1f);
        double a = (step * source) % RotationAngles.TWO_PI;
        if (a < 0) {
            a += RotationAngles.TWO_PI;
        }
        return (float) a;
    }

    /** TFC {@code calculateRealRotationAngle} of the source angle. */
    public static float realAngle(Direction direction, double source, Direction facing) {
        return CrankshaftRules.realRotationAngle(nodeAngle(direction, source), facing);
    }

    /** TFC {@code calculateShaftMovement} at the source angle. */
    public static CrankshaftRules.ShaftMovement movement(Direction direction, double source, Direction facing) {
        return CrankshaftRules.shaftMovement(realAngle(direction, source, facing));
    }

    /** The vector {@code (x, y, z)} turned by the unit quaternion {@code q}. */
    static double[] rotate(double[] q, double x, double y, double z) {
        double[] v = SpinQuaternions.mul(SpinQuaternions.mul(q, new double[] {x, y, z, 0}), new double[] {-q[0], -q[1], -q[2], q[3]});
        return new double[] {v[0], v[1], v[2]};
    }

    static float[] floats(double[] q) {
        return new float[] {(float) q[0], (float) q[1], (float) q[2], (float) q[3]};
    }
}
