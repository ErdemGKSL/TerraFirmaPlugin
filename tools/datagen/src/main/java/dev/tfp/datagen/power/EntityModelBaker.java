/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/entity/{WaterWheelModel,WindmillBladeModel,WindmillBladeLatticeModel}.java
 *       @ c3b4d9536 (the cube lists, read from the pinned sources)
 */
package dev.tfp.datagen.power;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-MECH-18: a TFC Java entity model ({@code LayerDefinition} cube list) baked into the elements of a vanilla item model.
 *
 * <p>Every rotation on the way from the root to a cube must be about x (TFC's water wheel and windmill blades are), so a
 * cube's total angle is the sum of its parts' {@code xRot}. A vanilla element turns about one axis by -45 to 45 degrees in
 * 22.5 degree steps, so the angle is split into a multiple of 90 degrees (baked into the axis-aligned box, its faces
 * re-labelled and their UVs turned) and that remainder (the element's own rotation, about the line through the cube's part
 * pivot). The renderer frame ({@code outer}) is a translation plus a multiple of 90 degrees about x, which commutes with the
 * part rotations.
 *
 * <p>The models are larger than the element range ({@code -16..32}), so they are written {@code scale} times smaller about
 * the block centre; the display draws them back at {@code 1 / scale}.
 *
 * <p>UVs: vanilla {@code ModelPart.Cube}'s box layout (face rectangles from {@code texOffs} and the box size) and its
 * per-vertex assignment ({@code Polygon}: vertex 0 gets (u2, v1), 1 (u1, v1), 2 (u1, v2), 3 (u2, v2)) are matched to a vanilla
 * block face ({@code FaceInfo} corner order and {@code BlockFaceUV} rotation), so the texture lands on the same corners as in
 * TFC's renderer (which draws these models without the entity flip).
 */
final class EntityModelBaker {
    private static final Pattern PART = Pattern.compile("PartDefinition\\s+(\\w+)\\s*=\\s*(\\w+)\\.addOrReplaceChild\\(\"(\\w+)\","
        + "\\s*(CubeListBuilder\\.create\\(\\).*?),\\s*PartPose\\.(offset|offsetAndRotation)\\(([^)]*)\\)\\);", Pattern.DOTALL);
    private static final Pattern CUBE = Pattern.compile("\\.texOffs\\((-?\\d+),\\s*(-?\\d+)\\)\\.addBox\\(([^,]+?)F,\\s*([^,]+?)F,"
        + "\\s*([^,]+?)F,\\s*([^,]+?)F,\\s*([^,]+?)F,\\s*([^,]+?)F,");
    private static final Pattern SIZE = Pattern.compile("LayerDefinition\\.create\\(meshdefinition,\\s*(\\d+),\\s*(\\d+)\\)");
    static final String ROOT = "partdefinition";
    /** The element range of a vanilla model. */
    static final double MIN = -16;
    static final double MAX = 32;

    record Box(int u, int v, double x, double y, double z, double w, double h, double d) {
    }

    /** A part: offset (pixels), rotation (radians, x y z), cubes, the parent's variable name. */
    record Part(String var, String parent, double[] offset, double[] rot, List<Box> boxes) {
    }

    private final String file;
    private final Map<String, Part> parts;
    private final int texWidth;
    private final int texHeight;

    private EntityModelBaker(String file, Map<String, Part> parts, int texWidth, int texHeight) {
        this.file = file;
        this.parts = parts;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
    }

    /** Reads the cube lists of a TFC model source. */
    static EntityModelBaker read(String file, String src) {
        Map<String, Part> parts = new LinkedHashMap<>();
        Matcher m = PART.matcher(src);
        while (m.find()) {
            List<Box> boxes = new ArrayList<>();
            Matcher c = CUBE.matcher(m.group(4));
            while (c.find()) {
                boxes.add(new Box(Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), f(c.group(3)), f(c.group(4)),
                    f(c.group(5)), f(c.group(6)), f(c.group(7)), f(c.group(8))));
            }
            if (m.group(4).contains("addBox") && boxes.size() != m.group(4).split("addBox", -1).length - 1) {
                throw new DatagenException(file + ": part " + m.group(1) + " has a cube this reader does not understand");
            }
            String[] a = m.group(6).split(",");
            double[] off = {f(a[0]), f(a[1]), f(a[2])};
            double[] rot = m.group(5).equals("offsetAndRotation") ? new double[] {f(a[3]), f(a[4]), f(a[5])} : new double[3];
            parts.put(m.group(1), new Part(m.group(1), m.group(2), off, rot, boxes));
        }
        Matcher size = SIZE.matcher(src);
        if (parts.isEmpty() || !size.find()) {
            throw new DatagenException(file + " has no parts or no texture size any more");
        }
        return new EntityModelBaker(file, parts, Integer.parseInt(size.group(1)), Integer.parseInt(size.group(2)));
    }

    private static double f(String s) {
        return Double.parseDouble(s.trim().replace("F", ""));
    }

    Map<String, Part> parts() {
        return parts;
    }

    int texWidth() {
        return texWidth;
    }

    /**
     * The model of the parts {@code tops} and everything under them, at rest (every animated {@code xRot} 0).
     *
     * @param outer   the renderer frame: block pixels of a root-space point (a multiple of 90 degrees about x and a shift)
     * @param scale   the model is written this many times smaller about the block centre
     * @param texture the texture reference of every face ({@code #0})
     */
    JsonObject bake(List<String> tops, Affine outer, double scale, String texture) {
        JsonArray elements = new JsonArray();
        for (Part p : parts.values()) {
            if (p.boxes().isEmpty() || tops.stream().noneMatch(t -> under(p, t))) {
                continue;
            }
            double angle = 0;
            Affine g = Affine.IDENTITY;
            for (Part c = p; c != null; c = parts.get(c.parent())) {
                if (Math.abs(c.rot()[1]) > 1e-6 || Math.abs(c.rot()[2]) > 1e-6) {
                    throw new DatagenException(file + ": part " + c.var() + " turns about y or z; only x rotations bake");
                }
                double deg = snap(Math.toDegrees(c.rot()[0]), c.var());
                angle += deg;
                g = Affine.translate(c.offset()[0], c.offset()[1], c.offset()[2]).after(Affine.rotX(deg)).after(g);
            }
            g = outer.after(g);
            int quarter = (int) Math.round(angle / 90.0);
            double rest = angle - 90.0 * quarter;
            // the straight part: the outer frame and the quarter turns; the element turns by rest about x through the pivot
            Affine straight = g.after(Affine.rotX(-rest));
            for (Box b : p.boxes()) {
                elements.add(element(straight, rest, b, scale));
            }
        }
        if (elements.isEmpty()) {
            throw new DatagenException(file + ": no cubes under " + tops);
        }
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("0", texture);
        textures.addProperty("particle", texture);
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /** The angle in degrees, which must be a multiple of 22.5 (TFC writes radians to 4 places). */
    private double snap(double deg, String part) {
        double s = 22.5 * Math.round(deg / 22.5);
        if (Math.abs(s - deg) > 0.05) {
            throw new DatagenException(file + ": part " + part + " turns by " + deg + " degrees, not a multiple of 22.5");
        }
        return s;
    }

    private boolean under(Part p, String top) {
        for (Part c = p; c != null; c = parts.get(c.parent())) {
            if (c.var().equals(top)) {
                return true;
            }
        }
        return false;
    }

    /** Face, its 4 local corner indices (bit 0 x, bit 1 y, bit 2 z) and the Polygon's u1, v1, u2, v2 rectangle keys. */
    private record EntityFace(int[] corners, int u1, int v1, int u2, int v2) {
    }

    // f4..f12 of ModelPart.Cube as indices 4..12; the corner bits: v0 = 0, v1 = x, v2 = x|y, v3 = y, v4 = z, v5 = x|z, v6 = all, v7 = y|z
    private static final EntityFace[] ENTITY_FACES = {
        new EntityFace(new int[] {5, 4, 0, 1}, 5, 10, 6, 11), // DOWN {v5, v4, v0, v1}
        new EntityFace(new int[] {3, 2, 6, 7}, 6, 11, 7, 10), // UP {v2, v3, v7, v6}
        new EntityFace(new int[] {0, 4, 6, 2}, 4, 11, 5, 12), // WEST {v0, v4, v7, v3}
        new EntityFace(new int[] {1, 0, 2, 3}, 5, 11, 6, 12), // NORTH {v1, v0, v3, v2}
        new EntityFace(new int[] {5, 1, 3, 7}, 6, 11, 8, 12), // EAST {v5, v1, v2, v6}
        new EntityFace(new int[] {4, 5, 7, 6}, 8, 11, 9, 12), // SOUTH {v4, v5, v6, v7}
    };
    private static final double[][] ENTITY_NORMALS = {{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}};

    private JsonObject element(Affine straight, double rest, Box b, double scale) {
        double[][] corners = new double[8][];
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < 8; i++) {
            double[] p = {b.x() + ((i & 1) != 0 ? b.w() : 0), b.y() + ((i & 2) != 0 ? b.h() : 0), b.z() + ((i & 4) != 0 ? b.d() : 0)};
            corners[i] = straight.apply(p);
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], corners[i][k]);
                hi[k] = Math.max(hi[k], corners[i][k]);
            }
        }
        for (int k = 0; k < 3; k++) {
            if (hi[k] - lo[k] < 1e-9) {
                throw new DatagenException(file + ": a flat cube (size 0) at " + b);
            }
        }
        JsonObject e = new JsonObject();
        e.add("from", vec(shrink(lo, scale)));
        e.add("to", vec(shrink(hi, scale)));
        for (double[] v : new double[][] {shrink(lo, scale), shrink(hi, scale)}) {
            for (double c : v) {
                if (c < MIN - 1e-9 || c > MAX + 1e-9) {
                    throw new DatagenException(file + ": cube " + b + " lands outside the element range at scale " + scale);
                }
            }
        }
        if (Math.abs(rest) > 1e-9) {
            JsonObject r = new JsonObject();
            r.addProperty("angle", rest);
            r.addProperty("axis", "x");
            r.add("origin", vec(shrink(straight.apply(new double[3]), scale)));
            e.add("rotation", r);
        }
        double[] f = new double[13];
        f[4] = b.u();
        f[5] = b.u() + b.d();
        f[6] = b.u() + b.d() + b.w();
        f[7] = b.u() + b.d() + b.w() + b.w();
        f[8] = b.u() + b.d() + b.w() + b.d();
        f[9] = b.u() + b.d() + b.w() + b.d() + b.w();
        f[10] = b.v();
        f[11] = b.v() + b.d();
        f[12] = b.v() + b.d() + b.h();
        JsonObject faces = new JsonObject();
        for (int i = 0; i < 6; i++) {
            EntityFace ef = ENTITY_FACES[i];
            String face = faceOf(straight.linear(ENTITY_NORMALS[i]));
            // Polygon: vertex 0 gets (u2, v1), 1 (u1, v1), 2 (u1, v2), 3 (u2, v2)
            double[][] uv = {{f[ef.u2()], f[ef.v1()]}, {f[ef.u1()], f[ef.v1()]}, {f[ef.u1()], f[ef.v2()]}, {f[ef.u2()], f[ef.v2()]}};
            int[] cornerOf = new int[4];
            for (int k = 0; k < 4; k++) {
                cornerOf[k] = vanillaCorner(face, corners[ef.corners()[k]], lo, hi);
            }
            faces.add(face, faceUv(cornerOf, uv, b));
        }
        e.add("faces", faces);
        return e;
    }

    private static double[] shrink(double[] p, double scale) {
        return new double[] {8 + (p[0] - 8) / scale, 8 + (p[1] - 8) / scale, 8 + (p[2] - 8) / scale};
    }

    private static String faceOf(double[] n) {
        return n[0] > 0.5 ? "east" : n[0] < -0.5 ? "west" : n[1] > 0.5 ? "up" : n[1] < -0.5 ? "down" : n[2] > 0.5 ? "south" : "north";
    }

    /**
     * The {@code FaceInfo} corner (0 = where the default UV puts (u0, v0), 1 = (u0, v1), 2 = (u1, v1), 3 = (u1, v0)) of a
     * point on {@code face} of the box {@code lo..hi}, from vanilla's default face UVs (down: u = x, v = 16 - z; up: u = x,
     * v = z; north: u = 16 - x, v = 16 - y; south: u = x; west: u = z; east: u = 16 - z; sides v = 16 - y).
     */
    static int vanillaCorner(String face, double[] p, double[] lo, double[] hi) {
        boolean s;
        boolean t;
        switch (face) {
            case "down" -> {
                s = near(p[0], hi[0]);
                t = near(p[2], lo[2]);
            }
            case "up" -> {
                s = near(p[0], hi[0]);
                t = near(p[2], hi[2]);
            }
            case "north" -> {
                s = near(p[0], lo[0]);
                t = near(p[1], lo[1]);
            }
            case "south" -> {
                s = near(p[0], hi[0]);
                t = near(p[1], lo[1]);
            }
            case "west" -> {
                s = near(p[2], hi[2]);
                t = near(p[1], lo[1]);
            }
            default -> {
                s = near(p[2], lo[2]);
                t = near(p[1], lo[1]);
            }
        }
        return !s ? (!t ? 0 : 1) : (t ? 2 : 3);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    /** The face's uv and rotation that put {@code uv[k]} on the vertex at {@code cornerOf[k]}. */
    private JsonObject faceUv(int[] cornerOf, double[][] uv, Box b) {
        for (int rot : new int[] {0, 180, 90, 270}) {
            // BlockFaceUV: corner i shows the rectangle corner (i + rot / 90) % 4: 0 (u0, v0), 1 (u0, v1), 2 (u1, v1), 3 (u1, v0)
            double[] r = new double[4];
            int[] at = new int[4];
            for (int k = 0; k < 4; k++) {
                at[(cornerOf[k] + rot / 90) % 4] = k;
            }
            r[0] = uv[at[0]][0];
            r[1] = uv[at[0]][1];
            r[2] = uv[at[2]][0];
            r[3] = uv[at[2]][1];
            if (near(uv[at[1]][0], r[0]) && near(uv[at[1]][1], r[3]) && near(uv[at[3]][0], r[2]) && near(uv[at[3]][1], r[1])) {
                JsonObject o = new JsonObject();
                JsonArray a = new JsonArray();
                a.add(round(r[0] * 16 / texWidth));
                a.add(round(r[1] * 16 / texHeight));
                a.add(round(r[2] * 16 / texWidth));
                a.add(round(r[3] * 16 / texHeight));
                o.add("uv", a);
                if (rot != 0) {
                    o.addProperty("rotation", rot);
                }
                o.addProperty("texture", "#0");
                return o;
            }
        }
        throw new DatagenException(file + ": no face UV matches the box layout of " + b);
    }

    private static JsonArray vec(double[] v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(round(d));
        }
        return a;
    }

    static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0 + 0.0;
    }

    /** An affine map of block pixels: {@code m} row-major 3x3, {@code t} the translation. */
    record Affine(double[] m, double[] t) {
        static final Affine IDENTITY = new Affine(new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new double[3]);

        static Affine translate(double x, double y, double z) {
            return new Affine(IDENTITY.m, new double[] {x, y, z});
        }

        /** The right-handed turn about x ({@code Axis.XP}, {@code ModelPart.xRot}, a model element's x rotation). */
        static Affine rotX(double deg) {
            double r = Math.toRadians(deg);
            double c = clean(Math.cos(r));
            double s = clean(Math.sin(r));
            return new Affine(new double[] {1, 0, 0, 0, c, -s, 0, s, c}, new double[3]);
        }

        private static double clean(double v) {
            return Math.abs(v) < 1e-12 ? 0 : Math.abs(Math.abs(v) - 1) < 1e-12 ? Math.signum(v) : v;
        }

        double[] apply(double[] p) {
            double[] r = linear(p);
            for (int i = 0; i < 3; i++) {
                r[i] += t[i];
            }
            return r;
        }

        double[] linear(double[] p) {
            double[] r = new double[3];
            for (int i = 0; i < 3; i++) {
                r[i] = m[3 * i] * p[0] + m[3 * i + 1] * p[1] + m[3 * i + 2] * p[2];
            }
            return r;
        }

        /** {@code this} after {@code inner}: {@code this(inner(p))}. */
        Affine after(Affine inner) {
            double[] nm = new double[9];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        nm[3 * i + j] += m[3 * i + k] * inner.m[3 * k + j];
                    }
                }
            }
            return new Affine(nm, apply(inner.t));
        }
    }
}
