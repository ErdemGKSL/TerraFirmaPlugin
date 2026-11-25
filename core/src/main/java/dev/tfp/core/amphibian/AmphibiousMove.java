/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/TrueAmphibiousMoveControl.java @ c3b4d9536 (tick),
 * aquatic/AmphibiousAnimal.java (travel, AmphibianMoveControl), predator/AmphibiousPredator.java (constructor, travel),
 * same commit; with vanilla 1.21.1 SmoothSwimmingMoveControl.tick, MoveControl.rotlerp and Entity.moveRelative
 */
package dev.tfp.core.amphibian;

/**
 * The in-water half of TFC's {@code TrueAmphibiousMoveControl} (a {@code SmoothSwimmingMoveControl(85, 10, 0.1, 0.5,
 * false)}) followed by the in-water {@code travel} of amphibians and the crocodile ({@code moveRelative(speed)}, move,
 * velocity times 0.9, no gravity). On land TFC uses vanilla's walking {@code MoveControl}, which the carrier already has.
 *
 * <p>Trigonometry uses {@link Math} where vanilla uses {@code Mth}'s 65536-entry sine table (differences below 1e-4).
 */
public final class AmphibiousMove {
    private AmphibiousMove() {
    }

    /** The {@code TrueAmphibiousMoveControl} arguments shared by every amphibian and the crocodile. */
    public static final int MAX_TURN_X = 85;
    public static final int MAX_TURN_Y = 10;
    public static final float IN_WATER_SPEED = 0.1f;
    public static final float OUT_OF_WATER_SPEED = 0.5f;
    /** {@code travel} in water: the velocity kept per tick. */
    public static final double WATER_DRAG = 0.9;
    /** {@code SmoothSwimmingMoveControl}: the pitch turn per tick. */
    public static final float PITCH_STEP = 5.0f;

    /**
     * The mob's motion state.
     *
     * @param yRot the body/head yaw in degrees
     * @param xRot the pitch in degrees
     * @param vx   velocity x
     * @param vy   velocity y
     * @param vz   velocity z
     */
    public record State(float yRot, float xRot, double vx, double vy, double vz) {
    }

    /**
     * One tick in water toward the wanted position: turn (yaw by at most 10 degrees, pitch by at most 5 toward a goal clamped
     * to +-85), set the forward/vertical input to {@code speedModifier x movementSpeed}, then {@code travel}. Without a
     * wanted position (navigation done) the input is zero and only the drag applies.
     *
     * @param s             the state before
     * @param dx            wanted x minus the mob's x (ignored without a target)
     * @param dy            wanted y minus the mob's y
     * @param dz            wanted z minus the mob's z
     * @param hasTarget     the move control is in {@code MOVE_TO} and the navigation is not done
     * @param speedModifier the walk target's speed modifier
     * @param movementSpeed the {@code MOVEMENT_SPEED} attribute
     * @param playingDead   {@code AmphibiousAnimal}: the move control does not tick and the input is zero
     * @return the state after
     */
    public static State waterTick(State s, double dx, double dy, double dz, boolean hasTarget, double speedModifier,
            double movementSpeed, boolean playingDead) {
        float yRot = s.yRot();
        float xRot = s.xRot();
        float speed = 0f;
        float zza = 0f;
        float yya = 0f;
        if (hasTarget && !playingDead) {
            double d3 = dx * dx + dy * dy + dz * dz;
            if (d3 >= 2.5000003E-7F) {
                float f = (float) (Math.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                yRot = rotlerp(yRot, f, MAX_TURN_Y);
                float f1 = (float) (speedModifier * movementSpeed);
                speed = f1 * IN_WATER_SPEED;
                double d4 = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(dy) > 1.0E-5F || Math.abs(d4) > 1.0E-5F) {
                    float f3 = -((float) (Math.atan2(dy, d4) * 180.0F / (float) Math.PI));
                    f3 = clamp(wrapDegrees(f3), -MAX_TURN_X, MAX_TURN_X);
                    xRot = rotlerp(xRot, f3, PITCH_STEP);
                }
                float f6 = (float) Math.cos(xRot * (float) (Math.PI / 180.0));
                float f4 = (float) Math.sin(xRot * (float) (Math.PI / 180.0));
                zza = f6 * f1;
                yya = -f4 * f1;
            }
        }
        if (playingDead) {
            // AmphibiousAnimal.travel: the input keeps only a downward y (zero here: the move control did not run)
            zza = 0f;
            yya = Math.min(yya, 0f);
        }
        // Entity.moveRelative(speed, (xxa=0, yya, zza)) then move, then scale(0.9)
        double ix = 0.0;
        double iy = yya;
        double iz = zza;
        double len = ix * ix + iy * iy + iz * iz;
        double ax = 0;
        double ay = 0;
        double az = 0;
        if (len >= 1.0E-7) {
            double n = len > 1.0 ? Math.sqrt(len) : 1.0;
            ix = ix / n * speed;
            iy = iy / n * speed;
            iz = iz / n * speed;
            float sin = (float) Math.sin(yRot * (float) (Math.PI / 180.0));
            float cos = (float) Math.cos(yRot * (float) (Math.PI / 180.0));
            ax = ix * cos - iz * sin;
            ay = iy;
            az = iz * cos + ix * sin;
        }
        return new State(yRot, xRot, (s.vx() + ax) * WATER_DRAG, (s.vy() + ay) * WATER_DRAG, (s.vz() + az) * WATER_DRAG);
    }

    /** Vanilla {@code MoveControl.rotlerp}. */
    static float rotlerp(float from, float to, float maxStep) {
        float f = wrapDegrees(to - from);
        if (f > maxStep) {
            f = maxStep;
        }
        if (f < -maxStep) {
            f = -maxStep;
        }
        float f1 = from + f;
        if (f1 < 0.0F) {
            f1 += 360.0F;
        } else if (f1 > 360.0F) {
            f1 -= 360.0F;
        }
        return f1;
    }

    /** Vanilla {@code Mth.wrapDegrees(float)}. */
    static float wrapDegrees(float value) {
        float f = value % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }
        if (f < -180.0F) {
            f += 360.0F;
        }
        return f;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}
