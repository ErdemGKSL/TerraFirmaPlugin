/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sign;

/**
 * PAR-DECO-03: which side of a sign a player is writing on or reading, and the yaw of a sign (vanilla
 * {@code SignBlock.getYRotationDegrees} and {@code SignBlockEntity.isFacingFrontText}, which TFC's signs inherit).
 */
public final class SignGeometry {
    /** Standing and ceiling hanging signs turn in 16 steps of 22.5 degrees. */
    public static final int ROTATIONS = 16;

    private SignGeometry() {
    }

    /** Yaw of a standing or ceiling hanging sign: south is 0, west 90 (vanilla {@code RotationSegment.convertToDegrees}). */
    public static float standingYaw(int rotation) {
        return Math.floorMod(rotation, ROTATIONS) * 360f / ROTATIONS;
    }

    /** Yaw of a wall sign by the direction it faces ({@code Direction.toYRot}: south 0, west 90, north 180, east 270). */
    public static float wallYaw(String facing) {
        return switch (facing) {
            case "south" -> 0f;
            case "west" -> 90f;
            case "north" -> 180f;
            case "east" -> 270f;
            default -> throw new IllegalArgumentException("not a horizontal direction: " + facing);
        };
    }

    /** Vanilla {@code Mth.degreesDifference}: the signed difference of two angles in [-180, 180). */
    static float degreesDifference(float from, float to) {
        float d = (to - from) % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /**
     * {@code SignBlockEntity.isFacingFrontText}: the player at (px, pz) faces the front when the angle from the player to the
     * sign's centre block differs from the sign's yaw by at most 90 degrees.
     */
    public static boolean isFacingFront(float signYaw, int blockX, int blockZ, double px, double pz) {
        double angle = Math.atan2(blockZ + 0.5 - pz, blockX + 0.5 - px) * 180.0 / Math.PI - 90.0;
        return Math.abs(degreesDifference(signYaw, (float) angle)) <= 90f;
    }
}
