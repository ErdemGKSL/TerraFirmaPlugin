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
 *
 * <p>Lighting (PAR-INFRA-41). The client lights an item quad with the two entity directional lights,
 * {@link #itemLight(double[])}, from the baked normal (the quad's face direction snapped to an axis at bake time) turned by
 * the pose's normal matrix; {@code shade: false} does not change that. A flat quad would be drawn at down 0.40, north/south
 * 0.74, east/west 0.50 of its tint, so the specks read darker than TFC draws them against the rock. The quad element is
 * therefore tilted {@value #TILT_DEGREES} degrees about x in the model (its baked normal still snaps to {@code -z});
 * {@link #rightRotation()} turns the geometry flat again, which tilts the lit normal toward the texture top (world up on the
 * side faces); {@link #scale()} mirrors the down face across the quad plane (vertices and winding unchanged, normal flipped
 * to point up); and {@link #tint()} divides the vanilla shade by the light the lit normal gets. Speck and rock then carry the
 * same face shade, so their ratio is TFC's at any light level (the lightmap multiplies both).
 */
public enum OverlayFace {
    // Quarter turns: 0.70710677 = sin 45 degrees. DOWN/UP: +/-90 about x; WEST/EAST: -/+90 about y; NORTH: 180 about y.
    DOWN(0, -1, 0, new float[] {0.70710677f, 0, 0, 0.70710677f}, 0x808080),
    UP(0, 1, 0, new float[] {-0.70710677f, 0, 0, 0.70710677f}, 0xFFFFFF),
    NORTH(0, 0, -1, new float[] {0, 1, 0, 0}, 0xCCCCCC),
    SOUTH(0, 0, 1, new float[] {0, 0, 0, 1}, 0xCCCCCC),
    WEST(-1, 0, 0, new float[] {0, -0.70710677f, 0, 0.70710677f}, 0x999999),
    EAST(1, 0, 0, new float[] {0, 0.70710677f, 0, 0.70710677f}, 0x999999);

    /** The quad element's rotation about x in the model ({@code origin [8, 8, 8]}); the display untilts it. */
    public static final float TILT_DEGREES = -22.5f;

    /** The client's entity lights (overworld): {@code Lighting} {@code (0.2, 1, -0.7)} and {@code (-0.2, 1, 0.7)}, normalised. */
    static final double[] LIGHT0 = normalise(0.2, 1.0, -0.7);
    static final double[] LIGHT1 = normalise(-0.2, 1.0, 0.7);

    /** The baked normal of the tilted quad in the model: its face direction ({@code north}), not the tilted geometry's. */
    static final double[] BAKED_NORMAL = {0, 0, -1};

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
     * east/west 0.6, down 0.5: what the rock under the overlay is drawn with. The overlay item is sent with {@link #tint()},
     * which also undoes the item light.
     */
    public int shade() {
        return shade;
    }

    /**
     * The colour 0 of {@code custom_model_data} the overlay item is sent with: {@code round(255 * shade / itemLight(litNormal))}
     * per channel, so the drawn speck is the vanilla face shade times the lightmap like the rock under it: north/south
     * {@code 0xE3E3E3}, east/west {@code 0xC6C6C6}, up {@code 0xFFFFFF}, down (mirrored, lit from above) {@code 0x808080}.
     */
    public int tint() {
        double shadeFraction = switch (this) {
            case UP -> 1.0;
            case NORTH, SOUTH -> 0.8;
            case WEST, EAST -> 0.6;
            case DOWN -> 0.5;
        };
        int c = (int) Math.round(255 * Math.min(1.0, shadeFraction / itemLight(litNormal())));
        return c << 16 | c << 8 | c;
    }

    /** The display's right rotation {@code (x, y, z, w)}: the untilt, {@value #TILT_DEGREES} degrees about x (a copy). */
    public float[] rightRotation() {
        double half = Math.toRadians(TILT_DEGREES) / 2;
        return new float[] {(float) Math.sin(half), 0, 0, (float) Math.cos(half)};
    }

    /** The display's scale: {@code (1, 1, -1)} on the down face (mirror across the quad plane), else {@code (1, 1, 1)}. */
    public float[] scale() {
        return this == DOWN ? new float[] {1, 1, -1} : new float[] {1, 1, 1};
    }

    /**
     * The world normal the client lights the quad with: the baked {@code -z} normal turned 180 degrees about y (the item
     * display renderer), by {@link #rightRotation()}, {@link #scale()} and {@link #rotation()}.
     */
    public double[] litNormal() {
        double[] n = {-BAKED_NORMAL[0], BAKED_NORMAL[1], -BAKED_NORMAL[2]};
        n = rotate(rightRotation(), n);
        float[] s = scale();
        n = new double[] {n[0] * s[0], n[1] * s[1], n[2] * s[2]}; // inverse transpose of a +/-1 diagonal is itself
        return rotate(rotation, n);
    }

    /**
     * The client's light factor of an item quad with world normal {@code n}:
     * {@code min(1, 0.4 + 0.6 * (max(0, n.L0) + max(0, n.L1)))} ({@code light.glsl} {@code minecraft_mix_light}).
     */
    public static double itemLight(double[] n) {
        double a = Math.max(0, n[0] * LIGHT0[0] + n[1] * LIGHT0[1] + n[2] * LIGHT0[2]);
        double b = Math.max(0, n[0] * LIGHT1[0] + n[1] * LIGHT1[1] + n[2] * LIGHT1[2]);
        return Math.min(1.0, 0.4 + 0.6 * (a + b));
    }

    private static double[] normalise(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l, y / l, z / l};
    }

    /** Rotates {@code v} by {@link #rotation()} (q v q*). */
    public double[] rotate(double[] v) {
        return rotate(rotation, v);
    }

    /** Rotates {@code v} by the unit quaternion {@code q} {@code (x, y, z, w)} (q v q*). */
    public static double[] rotate(float[] q, double[] v) {
        double qx = q[0];
        double qy = q[1];
        double qz = q[2];
        double qw = q[3];
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
