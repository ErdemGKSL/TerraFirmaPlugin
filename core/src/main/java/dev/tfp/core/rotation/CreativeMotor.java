/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/CreativeRotationBlockEntity.java @ c3b4d9536
 *       (MAX_SPEED, MAX_STEPS, LERP_SPEED, incrementSpeed, decrementSpeed, the tick's target speed),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/CreativeRotationBlock.java @ c3b4d9536 (useWithoutItem)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.Direction;

/** TFC's creative motor: a speed step in {@code -8..8}, turned by clicking the plus and minus faces. */
public final class CreativeMotor {
    /** TFC {@code Mth.TWO_PI}. */
    static final float TWO_PI = (float) (Math.PI * 2);
    /** TFC {@code MAX_SPEED}: 30 RPM, in radians per tick. */
    public static final float MAX_SPEED = TWO_PI / (2 * 20);
    /** TFC {@code MAX_STEPS}. */
    public static final int MAX_STEPS = 8;
    /** TFC {@code LERP_SPEED}: the speed of one step. */
    public static final float LERP_SPEED = MAX_SPEED / MAX_STEPS;

    private CreativeMotor() {
    }

    /** TFC {@code Mth.clamp(step, -MAX_STEPS, MAX_STEPS)}. */
    public static int clamp(int step) {
        return Math.max(-MAX_STEPS, Math.min(MAX_STEPS, step));
    }

    /**
     * TFC's target speed {@code step * LERP_SPEED}, radians per tick. TFC sets it on the source rotation every tick
     * ({@code setSpeed(target)}), so a step change takes effect on the next tick, with no ramp.
     */
    public static float speed(int step) {
        return clamp(step) * LERP_SPEED;
    }

    /**
     * TFC {@code CreativeRotationBlock.useWithoutItem}: the step change of a click on {@code face} of a motor on
     * {@code axis}. The interactable axis is z for an x motor, else x; its positive face increments, its negative face
     * decrements; any other face is 0 (TFC {@code PASS}).
     */
    public static int stepChange(RotationAxis axis, Direction face) {
        RotationAxis interactable = axis == RotationAxis.X ? RotationAxis.Z : RotationAxis.X;
        if (RotationAxis.of(face) != interactable) {
            return 0;
        }
        return RotationAxis.isPositive(face) ? 1 : -1;
    }

    /** The step after a click; the same step when the click passes. */
    public static int click(int step, RotationAxis axis, Direction face) {
        return clamp(step + stepChange(axis, face));
    }

    /** A speed in radians per tick as revolutions per minute (20 ticks per second). */
    public static float rpm(float radiansPerTick) {
        return radiansPerTick * 20 * 60 / TWO_PI;
    }
}
