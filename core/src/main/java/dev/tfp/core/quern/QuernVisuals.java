/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/QuernBlockEntityRenderer.java @ c3b4d9536 (the handstone
 * at y 0.705 scaled 1.25 and turned by the rotation angle; the input at y 0.875 or 0.75, turned 45 degrees, scaled 0.5; the
 * first output at (0.125, 0.625, 0.125) tilted 75 degrees, scaled 0.125),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (getRotationAngle:
 * -recipeTimer * MANUAL_SPEED, one full turn per grind)
 */
package dev.tfp.core.quern;

import java.util.ArrayList;
import java.util.List;

/**
 * How the quern's contents are drawn (PAR-PROC-04): positions and scales of TFC's block entity renderer, and the handstone
 * turn split into interpolated steps.
 *
 * <p>TFC turns the handstone by {@code -recipeTimer * 2pi / 90} about {@code -y} every frame, so one grind is one full turn,
 * the angle about {@code +y} going from {@code 2pi} down to {@code 0}. A display entity interpolates between two
 * transformations along the shorter arc, so the turn is sent as {@link #SPIN_STEPS} steps of a quarter turn each
 * ({@link #spin}); the client interpolates each one smoothly. Pure; any thread.
 */
public final class QuernVisuals {
    /** The handstone display: centre height and scale (TFC {@code translate(center, 0.705, center)}, {@code scale(1.25)}). */
    public static final double HANDSTONE_Y = 0.705;
    public static final float HANDSTONE_SCALE = 1.25f;
    /** The input display: height with and without a handstone, the turn about y, the scale. */
    public static final double INPUT_Y_WITH_HANDSTONE = 0.875;
    public static final double INPUT_Y = 0.75;
    public static final double INPUT_YAW_DEGREES = 45;
    public static final float INPUT_SCALE = 0.5f;
    /** The output display: the position of TFC's first output item, its tilt about x and its scale. */
    public static final double OUTPUT_X = 0.125;
    public static final double OUTPUT_Y = 0.625;
    public static final double OUTPUT_Z = 0.125;
    public static final double OUTPUT_PITCH_DEGREES = 75;
    public static final float OUTPUT_SCALE = 0.125f;
    /** Interpolated steps per grind; each is a quarter turn, under the half turn a display interpolation can take. */
    public static final int SPIN_STEPS = 4;

    /**
     * One interpolated step of the handstone turn.
     *
     * @param delay    ticks after the grind start the step is sent
     * @param duration interpolation ticks
     * @param angle    the handstone's angle about +y at the end of the step, radians, in {@code (-2pi, 0]}
     */
    public record Step(int delay, int duration, double angle) {
    }

    private QuernVisuals() {
    }

    /**
     * The steps of one grind: {@link #SPIN_STEPS} steps whose durations add up to {@link QuernRules#TICKS_PER_GRIND}, each
     * turning a quarter turn further in TFC's direction (decreasing angle about +y), the last one back at 0.
     */
    public static List<Step> spin() {
        List<Step> steps = new ArrayList<>(SPIN_STEPS);
        int total = QuernRules.TICKS_PER_GRIND;
        for (int k = 0; k < SPIN_STEPS; k++) {
            int start = Math.round((float) k * total / SPIN_STEPS);
            int end = Math.round((float) (k + 1) * total / SPIN_STEPS);
            double angle = k + 1 == SPIN_STEPS ? 0 : -2 * Math.PI * (k + 1) / SPIN_STEPS;
            steps.add(new Step(start, end - start, angle));
        }
        return List.copyOf(steps);
    }

    /**
     * TFC {@code getRotationAngle} of a manual grind after {@code progress} units, as an angle about +y: TFC's
     * {@code 2pi * recipeTimer / 90}, written as the equal angle {@code -2pi * progress / 90} (0 at both ends).
     */
    public static double angle(double progress) {
        double p = Math.max(0, Math.min(QuernRules.TICKS_PER_GRIND, progress));
        double a = -2 * Math.PI * p / QuernRules.TICKS_PER_GRIND;
        return a <= -2 * Math.PI ? 0 : a;
    }
}
