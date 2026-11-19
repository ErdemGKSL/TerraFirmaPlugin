/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/AnvilBlockEntityRenderer.java @ c3b4d9536 (the pose stack:
 * translate(0.5, 0.003125 + yOffset, 0.5), scale 0.3, X 90, Z 90 * rotation + 270, then the per-slot translations)
 */
package dev.tfp.core.anvil;

/**
 * How the anvil's contents are drawn on its top face (PAR-METAL-03): TFC's block entity renderer as one display entity
 * transformation per slot. Pure; any thread.
 *
 * <p>TFC builds {@code T(0.5, y, 0.5) S(0.3) Rx(90) Rz(90 r + 270) T(L)} and renders the item centred at the origin, so an
 * item display at {@code (0.5, y, 0.5)} with translation {@code 0.3 Rx Rz L}, left rotation {@code Rx Rz} and scale
 * {@code 0.3} (0.18 for the flux, which TFC draws with another {@code scale(0.6)}) draws the same thing. {@code r} is the
 * facing's 2D data value (south 0, west 1, north 2, east 3).
 */
public final class AnvilVisuals {
    /** TFC {@code poseStack.scale(0.3f)}; the flux is drawn at 0.6 of it. */
    public static final float SCALE = 0.3f;
    public static final float FLUX_SCALE = 0.3f * 0.6f;
    /** Height of the display above the block's base: {@code 0.003125 + yOffset}. */
    private static final double BASE_Y = 0.003125;
    private static final double ROCK_Y = 0.875;
    private static final double METAL_Y = 0.6875;

    /**
     * One display's transformation.
     *
     * @param tx          translation, blocks, relative to the display's position
     * @param rotation    the left rotation as a quaternion {@code x, y, z, w}
     * @param scale       the uniform scale
     */
    public record Pose(double tx, double ty, double tz, double[] rotation, float scale) {
    }

    private AnvilVisuals() {
    }

    /** The display position's height above the block: TFC {@code 0.003125 + (tier == 0 ? 0.875 : 0.6875)}. */
    public static double displayY(int tier) {
        return BASE_Y + (tier == 0 ? ROCK_Y : METAL_Y);
    }

    /** TFC's {@code Z} turn in degrees for a facing's 2D data value: {@code 90 r + 270}. */
    public static double turnDegrees(int rotation) {
        return 90.0 * rotation + 270.0;
    }

    /** The 2D data value of a facing name (south 0, west 1, north 2, east 3); a block without a facing counts as 0. */
    public static int rotation(String facing) {
        int i = AnvilPlacement.FACINGS_2D.indexOf(facing);
        return Math.max(i, 0);
    }

    /** The pose of {@code slot}'s display, or null for a slot that is not drawn. */
    public static Pose pose(int slot, int rotation) {
        double[] local = switch (slot) {
            case AnvilSlots.HAMMER -> new double[] {-0.4, 0, 0};
            case AnvilSlots.INPUT_MAIN -> new double[] {-0.4 + 1.15, 0, 0};
            case AnvilSlots.INPUT_SECOND -> new double[] {-0.4 + 1.15 + 0.4, 0, -0.05};
            case AnvilSlots.CATALYST -> new double[] {-0.4 + 1.15 + 0.4 - 0.9, -0.25, -0.05 + 0.05};
            default -> null;
        };
        if (local == null) {
            return null;
        }
        double b = Math.toRadians(turnDegrees(rotation));
        double a = Math.toRadians(90);
        // v = Rx(a) Rz(b) L
        double zx = local[0] * Math.cos(b) - local[1] * Math.sin(b);
        double zy = local[0] * Math.sin(b) + local[1] * Math.cos(b);
        double zz = local[2];
        double vx = zx;
        double vy = zy * Math.cos(a) - zz * Math.sin(a);
        double vz = zy * Math.sin(a) + zz * Math.cos(a);
        double sa = Math.sin(a / 2);
        double ca = Math.cos(a / 2);
        double sb = Math.sin(b / 2);
        double cb = Math.cos(b / 2);
        // q = qx(a) * qz(b)
        double[] q = {sa * cb, -sa * sb, ca * sb, ca * cb};
        return new Pose(SCALE * vx, SCALE * vy, SCALE * vz, q, slot == AnvilSlots.CATALYST ? FLUX_SCALE : SCALE);
    }
}
