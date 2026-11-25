/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCStandingSignBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/wood/TFCCeilingHangingSignBlock.java @ c3b4d9536
 *       (both inherit vanilla StandingSignBlock / CeilingHangingSignBlock getStateForPlacement unchanged)
 */
package dev.tfp.core.sign;

/**
 * PAR-DECO-25: the rotation (and {@code attached}) a standing or ceiling hanging sign takes when a player places it.
 * Pure rules; the plugin applies the result to the block.
 */
public final class SignPlacement {
    private SignPlacement() {
    }

    /** Vanilla {@code RotationSegment.convertToSegment(float)}: {@code floor(degrees * 16 / 360 + 0.5) & 15}. */
    public static int segment(float degrees) {
        return ((int) Math.floor(degrees * 16.0f / 360.0f + 0.5)) & 15;
    }

    /** Vanilla {@code RotationSegment.convertToSegment(Direction)} for a horizontal direction: south 0, west 4, north 8, east 12. */
    public static int segment(String horizontal) {
        return segment(SignGeometry.wallYaw(horizontal));
    }

    /** {@code StandingSignBlock.getStateForPlacement}: {@code convertToSegment(context.getRotation() + 180)}. */
    public static int standingRotation(float placerYaw) {
        return segment(placerYaw + 180.0f);
    }

    /** The placement of a ceiling hanging sign. */
    public record Hanging(int rotation, boolean attached) {
    }

    /**
     * {@code CeilingHangingSignBlock.getStateForPlacement}: the sign hangs by a V of chains ({@code attached}) when the
     * block above has no full bottom face or the placer sneaks; below another hanging sign (not sneaking) whose rotation or
     * facing lies on the placer's axis it hangs by plain chains instead and takes the placer's direction.
     *
     * @param placerYaw        the placer's yaw ({@code context.getRotation()})
     * @param sneaking         {@code isSecondaryUseActive}
     * @param aboveFullBottom  whether the block above has a full down face
     * @param aboveHangingAxis the axis of the hanging sign above ("x" or "z": its facing, or its rotation when it points
     *                         along an axis), or null when the block above is no hanging sign or points diagonally
     */
    public static Hanging ceilingHanging(float placerYaw, boolean sneaking, boolean aboveFullBottom, String aboveHangingAxis) {
        String direction = horizontal(placerYaw);
        boolean attached = !aboveFullBottom || sneaking;
        if (aboveHangingAxis != null && !sneaking && aboveHangingAxis.equals(axis(direction))) {
            attached = false;
        }
        int rotation = attached ? standingRotation(placerYaw) : segment(opposite(direction));
        return new Hanging(rotation, attached);
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    public static String horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
    }

    public static String opposite(String horizontal) {
        return switch (horizontal) {
            case "south" -> "north";
            case "north" -> "south";
            case "west" -> "east";
            case "east" -> "west";
            default -> throw new IllegalArgumentException(horizontal);
        };
    }

    /** "x" for east and west, "z" for north and south. */
    public static String axis(String horizontal) {
        return horizontal.equals("east") || horizontal.equals("west") ? "x" : "z";
    }

    /** The axis a 0..15 rotation points along, or null for the eight diagonal and in-between steps. */
    public static String rotationAxis(int rotation) {
        return switch (Math.floorMod(rotation, 16)) {
            case 0, 8 -> "z";
            case 4, 12 -> "x";
            default -> null;
        };
    }
}
