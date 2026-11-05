/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

/**
 * The six faces of an ore face overlay (PAR-INFRA-15, design {@code docs/research/ore-entity-renderer-design.md} §3.1):
 * the {@code item_display} transform that lays the overlay quad item ({@code tfp:ore_overlay/<ore>}, one 16 x 16 face in
 * the model's {@code z = 8} plane facing {@code -z}) onto one block face, and the vanilla face shade it is tinted with.
 *
 * <p>The display entity stands at the centre of the neighbour cell in the face direction (it takes that cell's light);
 * its translation is {@code -normal * (0.5 - }{@value #OFFSET}{@code )}, so the quad lies {@value #OFFSET} in front of the
 * face. The item renderer turns item models 180 degrees about y, so with the identity rotation the quad is a south face
 * with vanilla's south UV; {@link #rotation()} turns it onto the other faces so the texture lies exactly as vanilla draws
 * the block face ({@code tfc:block/ore}'s overlay, full {@code [0, 0, 16, 16]} UV). Pure and immutable.
 */
public enum OverlayFace {
    // Quarter turns: 0.70710677 = sin 45 degrees. DOWN/UP: +/-90 about x; WEST/EAST: -/+90 about y; NORTH: 180 about y.
    DOWN(0, -1, 0, new float[] {0.70710677f, 0, 0, 0.70710677f}, 0x808080),
    UP(0, 1, 0, new float[] {-0.70710677f, 0, 0, 0.70710677f}, 0xFFFFFF),
    NORTH(0, 0, -1, new float[] {0, 1, 0, 0}, 0xCCCCCC),
    SOUTH(0, 0, 1, new float[] {0, 0, 0, 1}, 0xCCCCCC),
    WEST(-1, 0, 0, new float[] {0, -0.70710677f, 0, 0.70710677f}, 0x999999),
    EAST(1, 0, 0, new float[] {0, 0.70710677f, 0, 0.70710677f}, 0x999999);

    /** Gap between the quad and the block face, in blocks (z-fights only beyond about 90 m; the cutoff is at most 64 m). */
    public static final float OFFSET = 0.01f;

    private final int dx;
    private final int dy;
    private final int dz;
    private final float[] rotation;
    private final int shade;

    OverlayFace(int dx, int dy, int dz, float[] rotation, int shade) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.rotation = rotation;
        this.shade = shade;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    /** The display's left rotation as a unit quaternion {@code (x, y, z, w)} (a copy). */
    public float[] rotation() {
        return rotation.clone();
    }

    /** The display's translation from the neighbour cell's centre: {@code -normal * (0.5 - OFFSET)}. */
    public float[] translation() {
        float t = 0.5f - OFFSET;
        return new float[] {-dx * t, -dy * t, -dz * t};
    }

    /**
     * The vanilla block face shade as an RGB colour for the {@code custom_model_data} tint: up 1.0, north/south 0.8,
     * east/west 0.6, down 0.5 (the quad has {@code shade: false}, so the tint does the shading).
     */
    public int shade() {
        return shade;
    }

    /** Rotates {@code v} by {@link #rotation()} (q v q*). */
    public double[] rotate(double[] v) {
        double qx = rotation[0];
        double qy = rotation[1];
        double qz = rotation[2];
        double qw = rotation[3];
        // t = 2 * cross(q.xyz, v); v' = v + w * t + cross(q.xyz, t)
        double tx = 2 * (qy * v[2] - qz * v[1]);
        double ty = 2 * (qz * v[0] - qx * v[2]);
        double tz = 2 * (qx * v[1] - qy * v[0]);
        return new double[] {
            v[0] + qw * tx + (qy * tz - qz * ty),
            v[1] + qw * ty + (qz * tx - qx * tz),
            v[2] + qw * tz + (qx * ty - qy * tx)};
    }
}
