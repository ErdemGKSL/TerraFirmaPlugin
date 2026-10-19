/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (Rotation.of tick and wrap, angle(rotation, partialTick)
 *       with the axis step of the direction, clampToTwoPi)
 */
package dev.tfp.core.machine;

import dev.tfp.core.world.Direction;

/**
 * The angle of a rotation as TFC's tickable rotation keeps it, for the machines that read the angle (the trip hammer's strike
 * window). The angle is state the caller owns: TFC keeps it on the source's tickable rotation, and the solver holds no angle.
 *
 * <p>Angles are in radians unless a method says degrees. The float arithmetic follows TFC's {@code float} fields.
 */
public final class RotationAngles {

    /** TFC's {@code Mth.TWO_PI} as a float. */
    public static final float TWO_PI = 6.2831855f;

    /** TFC's {@code Constants.RAD_TO_DEG}. */
    public static final float RAD_TO_DEG = 57.29578f;

    private RotationAngles() {
    }

    /**
     * One tick of a source's angle: adds the speed, then wraps into {@code [0, TWO_PI]} as TFC's tickable {@code tick} does.
     *
     * @param angle the angle now, in {@code [0, TWO_PI]}
     * @param speed the signed speed in radians per tick
     * @return the angle after one tick
     */
    public static float advance(float angle, float speed) {
        float next = angle + speed;
        if (next > TWO_PI) {
            next -= TWO_PI;
        }
        if (next < 0) {
            next += TWO_PI;
        }
        return next;
    }

    /**
     * TFC's {@code Rotation.angle(rotation, 1f)}: the angle one partial tick on, with the sign of the direction's axis step, and
     * a negative result wrapped up by one turn. A rotation whose direction runs against its axis reads the mirror angle.
     *
     * @param direction the rotation's direction, {@code Rotation.direction()}
     * @param angle     the caller's angle, from {@link #advance}
     * @param speed     the rotation's signed speed
     * @return the real angle in radians, in {@code [0, TWO_PI]} for the values the machines use
     */
    public static float radiansAtNextTick(Direction direction, float angle, float speed) {
        final float raw = angle + speed;
        final float signed = axisStep(direction) * raw;
        return signed < 0 ? TWO_PI + signed : signed;
    }

    /** {@link #radiansAtNextTick} converted to degrees as TFC's {@code getRealRotationDegrees(rotation, 1f)} does. */
    public static float degreesAtNextTick(Direction direction, float angle, float speed) {
        return RAD_TO_DEG * radiansAtNextTick(direction, angle, speed);
    }

    /**
     * The sign of the direction's axis, as Minecraft's {@code AxisDirection.getStep}: positive for UP, SOUTH and EAST, negative for
     * DOWN, NORTH and WEST.
     */
    static int axisStep(Direction direction) {
        return switch (direction) {
            case UP, SOUTH, EAST -> 1;
            case DOWN, NORTH, WEST -> -1;
        };
    }
}
