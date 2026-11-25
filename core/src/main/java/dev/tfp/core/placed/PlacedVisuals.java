/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/PlacedItemBlockEntityRenderer.java @ c3b4d9536
 *       (renderContent: the item branch)
 */
package dev.tfp.core.placed;

/**
 * Where an item display draws one slot of a placed item block, from TFC's renderer. Translations are relative to the
 * block's minimum corner; the display has yaw and pitch 0 and the {@code FIXED} item transform; its transformation is
 * TFC's pose followed by a half turn around Y that cancels the half turn vanilla's item display renderer applies before drawing.
 *
 * <ul>
 *   <li>Small items: centre of the quadrant ({@code 0.25 + slotX * 0.5}), lifted 0.125 for a 3D (block) model or
 *       0.03125 for a flat one, scaled by half, a further {@code slot * -0.0001} (scaled) against z-fighting.</li>
 *   <li>A large item: centre of the block, lifted 0.25 or 0.03125, full size, lowered by 0.001 / 0.002 on even x / z.</li>
 *   <li>3D models turn around Y by the slot's rotation; flat models are laid down (X 90) and then turned around Z.</li>
 * </ul>
 * TFC's per-item block models (pots, jugs, vessels, jars) are not used: every item draws its item model (ledger).
 */
public final class PlacedVisuals {
    /** A display pose: translation, left rotation as a quaternion {@code {x, y, z, w}}, uniform scale. */
    public record Pose(double tx, double ty, double tz, double[] rotation, float scale) {}

    private PlacedVisuals() {
    }

    /** TFC {@code slotX}: slots 0 and 2 lie on the high x half. */
    public static int slotX(int slot) {
        return slot % 2 == 0 ? 1 : 0;
    }

    /** TFC {@code slotZ}: slots 0 and 1 lie on the high z half. */
    public static int slotZ(int slot) {
        return slot < 2 ? 1 : 0;
    }

    /**
     * The pose of {@code slot}.
     *
     * @param large    the block holds one large item (slot 0)
     * @param degrees  the slot's rotation
     * @param asBlock  the item draws a 3D block model (TFC {@code model.isGui3d()})
     * @param blockX   the block's x (TFC's large-item height adjust)
     * @param blockZ   the block's z
     */
    public static Pose pose(int slot, boolean large, float degrees, boolean asBlock, int blockX, int blockZ) {
        double tx;
        double ty;
        double tz;
        float scale;
        if (large) {
            double adjust = (blockX % 2 == 0 ? -0.001f : 0f) + (blockZ % 2 == 0 ? -0.002f : 0f);
            tx = 0.5;
            ty = (asBlock ? 0.25 : 0.03125) + adjust;
            tz = 0.5;
            scale = 1f;
        } else {
            tx = 0.25 + slotX(slot) * 0.5;
            ty = (asBlock ? 0.125 : 0.03125) + slot * -0.0001f * 0.5;
            tz = 0.25 + slotZ(slot) * 0.5;
            scale = 0.5f;
        }
        double[] q = asBlock ? axis(0, 1, 0, degrees) : mul(axis(1, 0, 0, 90), axis(0, 0, 1, degrees));
        q = mul(q, axis(0, 1, 0, -180));
        return new Pose(tx, ty, tz, q, scale);
    }

    /** The quaternion of a turn of {@code degrees} around a unit axis. */
    static double[] axis(double x, double y, double z, double degrees) {
        double half = Math.toRadians(degrees) / 2;
        double s = Math.sin(half);
        return new double[] {x * s, y * s, z * s, Math.cos(half)};
    }

    /** Hamilton product {@code a * b} (b applied first). */
    static double[] mul(double[] a, double[] b) {
        return new double[] {
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
        };
    }
}
