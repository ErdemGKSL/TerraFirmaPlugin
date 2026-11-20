/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/entity/{AnemometerModel,VaneModel,CalendarClockModel}.java @ c3b4d9536 (the
 *       cube lists), src/main/java/net/dries007/tfc/client/render/blockentity/CalendarClockBlockEntityRenderer.java @ c3b4d9536
 *       (the facing transforms)
 */
package dev.tfp.datagen.instrument;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TFC's Java entity models ({@code LayerDefinition} cube lists) converted to vanilla block models. A part is
 * {@code PartPose} offset plus a {@code y} rotation (a multiple of 90 degrees) and cubes with box UVs; the block entity
 * renderer places the model with {@code translate(0.5, 1.5, 0.5); scale(-1, -1, 1)} and, for the clock, a facing
 * transform, so a point of the model lands at {@code (8 - x, 24 - y, 8 + z)} pixels after the facing transform. Every
 * transform is a multiple of 90 degrees, so each cube stays an axis-aligned element; the faces follow their normals.
 */
final class EntityBoxes {
    static final String RP = InstrumentBlocks.RP;
    private static final Pattern PART = Pattern.compile("PartDefinition\\s+(\\w+)\\s*=\\s*(\\w+)\\.addOrReplaceChild\\(\"(\\w+)\","
        + "\\s*(CubeListBuilder\\.create\\(\\).*?),\\s*PartPose\\.(offset|offsetAndRotation)\\(([^)]*)\\)\\);", Pattern.DOTALL);
    private static final Pattern CUBE = Pattern.compile("\\.texOffs\\((\\d+),\\s*(\\d+)\\)\\.addBox\\(([^,]+?)F,\\s*([^,]+?)F,\\s*([^,]+?)F,"
        + "\\s*([^,]+?)F,\\s*([^,]+?)F,\\s*([^,]+?)F,");

    record Cube(int u, int v, double x, double y, double z, double w, double h, double d) {
    }

    /** A model part: offset, y rotation in degrees, cubes, parent variable name ({@code partdefinition} for the root). */
    record Part(String var, String parent, double[] offset, int yaw, List<Cube> cubes) {
    }

    /** An affine map of the model space (pixels): {@code m} row-major 3x3, {@code t} the translation. */
    record Affine(double[] m, double[] t) {
        static final Affine IDENTITY = new Affine(new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new double[3]);

        static Affine translate(double x, double y, double z) {
            return new Affine(IDENTITY.m, new double[] {x, y, z});
        }

        static Affine rotX(int deg) {
            double c = cos(deg);
            double s = sin(deg);
            return new Affine(new double[] {1, 0, 0, 0, c, -s, 0, s, c}, new double[3]);
        }

        static Affine rotY(int deg) {
            double c = cos(deg);
            double s = sin(deg);
            return new Affine(new double[] {c, 0, s, 0, 1, 0, -s, 0, c}, new double[3]);
        }

        static Affine rotZ(int deg) {
            double c = cos(deg);
            double s = sin(deg);
            return new Affine(new double[] {c, -s, 0, s, c, 0, 0, 0, 1}, new double[3]);
        }

        private static double cos(int deg) {
            return switch (Math.floorMod(deg, 360)) {
                case 0 -> 1;
                case 180 -> -1;
                case 90, 270 -> 0;
                default -> throw new DatagenException("rotation " + deg + " is not a multiple of 90 degrees");
            };
        }

        private static double sin(int deg) {
            return switch (Math.floorMod(deg, 360)) {
                case 90 -> 1;
                case 270 -> -1;
                case 0, 180 -> 0;
                default -> throw new DatagenException("rotation " + deg + " is not a multiple of 90 degrees");
            };
        }

        double[] apply(double[] p) {
            double[] r = new double[3];
            for (int i = 0; i < 3; i++) {
                r[i] = m[3 * i] * p[0] + m[3 * i + 1] * p[1] + m[3 * i + 2] * p[2] + t[i];
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

    /** The transform the renderer adds before the common one (entity space, pixels). */
    record Pose(Affine affine) {
        static final Pose IDENTITY = new Pose(Affine.IDENTITY);

        /** {@code CalendarClockBlockEntityRenderer}: translate, then the rotations (the last listed is applied first). */
        static Pose clock(String facing) {
            return new Pose(switch (facing) {
                case "up" -> Affine.IDENTITY;
                case "north" -> Affine.translate(0, 16, -16).after(Affine.rotX(90));
                case "south" -> Affine.translate(0, 16, 16).after(Affine.rotX(-90)).after(Affine.rotY(-180));
                case "east" -> Affine.translate(-16, 16, 0).after(Affine.rotZ(-90)).after(Affine.rotY(90));
                case "west" -> Affine.translate(16, 16, 0).after(Affine.rotZ(90)).after(Affine.rotY(-90));
                case "down" -> Affine.translate(0, 32, 0).after(Affine.rotZ(180));
                default -> throw new DatagenException("unknown clock facing " + facing);
            });
        }
    }

    private final Map<String, Part> parts;
    private final Map<String, Integer> restYaw;
    private final String texture;
    private final String textureModelId;

    private EntityBoxes(Map<String, Part> parts, Map<String, Integer> restYaw, String texture, String textureModelId) {
        this.parts = parts;
        this.restYaw = restYaw;
        this.texture = texture;
        this.textureModelId = textureModelId;
    }

    /**
     * Reads the cube lists of {@code file}. {@code texture}: the entity texture name; {@code textureModelId}: where the
     * pack puts it ({@code tfp:block/instrument/...}). The rest angles of the moving parts are the {@code setupAnim}
     * values at angle 0.
     */
    static EntityBoxes read(TfcCheckout tfc, String file, String texture, String textureModelId) {
        String src = tfc.readString(file);
        Map<String, Part> parts = new LinkedHashMap<>();
        Matcher m = PART.matcher(src);
        while (m.find()) {
            List<Cube> cubes = new ArrayList<>();
            Matcher c = CUBE.matcher(m.group(4));
            while (c.find()) {
                cubes.add(new Cube(Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Double.parseDouble(c.group(3)),
                    Double.parseDouble(c.group(4)), Double.parseDouble(c.group(5)), Double.parseDouble(c.group(6)),
                    Double.parseDouble(c.group(7)), Double.parseDouble(c.group(8))));
            }
            String[] a = m.group(6).split(",");
            double[] off = {f(a[0]), f(a[1]), f(a[2])};
            int yaw = 0;
            if (m.group(5).equals("offsetAndRotation")) {
                if (Math.abs(f(a[3])) > 1e-6 || Math.abs(f(a[5])) > 1e-6) {
                    throw new DatagenException(file + ": part " + m.group(1) + " is rotated about x or z");
                }
                yaw = (int) Math.round(Math.toDegrees(f(a[4])));
            }
            parts.put(m.group(1), new Part(m.group(1), m.group(2), off, yaw, cubes));
        }
        if (parts.isEmpty()) {
            throw new DatagenException(file + " has no parts any more");
        }
        Map<String, Integer> rest = new LinkedHashMap<>();
        String flat = src.replaceAll("\\s+", " ");
        if (file.endsWith("AnemometerModel.java")) {
            need(flat, file, "spinny.yRot = anemometer.getAngle(partialTick);");
            rest.put("spinny", 0);
        } else if (file.endsWith("VaneModel.java")) {
            need(flat, file, "spinny.yRot = (float) (vane.getAngle(partialTick) + Math.PI / 2);");
            rest.put("spinny", 90);
        } else {
            for (String h : List.of("minute_hand.yRot = clock.getAngles()[0] + Mth.PI;", "hour_hand.yRot = clock.getAngles()[1] + Mth.PI;",
                "month_hand.yRot = clock.getAngles()[2] + Mth.PI;")) {
                need(flat, file, h);
            }
            rest.put("minute_hand", 180);
            rest.put("hour_hand", 180);
            rest.put("month_hand", 180);
        }
        return new EntityBoxes(parts, rest, texture, textureModelId);
    }

    private static void need(String src, String file, String needle) {
        if (!src.contains(needle)) {
            throw new DatagenException(file + " no longer contains \"" + needle + "\"");
        }
    }

    private static double f(String s) {
        return Double.parseDouble(s.trim().replace("F", ""));
    }

    void copyTexture(TfcCheckout tfc, OutputTree out) {
        String png = TfcCheckout.ASSETS + "textures/entity/" + texture + ".png";
        if (!tfc.exists(png)) {
            throw new DatagenException("TFC has no entity texture " + texture);
        }
        out.put(RP + "textures/" + textureModelId.substring(textureModelId.indexOf(':') + 1) + ".png", tfc.read(png));
    }

    private boolean under(Part p, String top) {
        for (Part c = p; c != null; c = parts.get(c.parent())) {
            if (c.var().equals(top)) {
                return true;
            }
        }
        return false;
    }

    private Affine chain(Part leaf) {
        Affine a = Affine.IDENTITY;
        for (Part c = leaf; c != null; c = parts.get(c.parent())) {
            int yaw = restYaw.getOrDefault(c.var(), c.yaw());
            a = Affine.translate(c.offset()[0], c.offset()[1], c.offset()[2]).after(Affine.rotY(yaw)).after(a);
        }
        return a;
    }

    /**
     * Writes the model of {@code tops} (the named parts and everything below them) and returns its id.
     *
     * @param shiftY pixels the model is lowered (the vane on an anemometer: {@code translate(0, 0.5625, 0)})
     */
    String model(OutputTree out, String name, List<String> tops, Pose pose, double shiftY) {
        JsonArray elements = new JsonArray();
        Affine flip = new Affine(new double[] {-1, 0, 0, 0, -1, 0, 0, 0, 1}, new double[] {8, 24, 8});
        Affine place = flip.after(Affine.translate(0, shiftY, 0)).after(pose.affine());
        for (Part p : parts.values()) {
            if (p.cubes().isEmpty() || tops.stream().noneMatch(t -> under(p, t))) {
                continue;
            }
            Affine g = place.after(chain(p));
            for (Cube c : p.cubes()) {
                elements.add(element(g, c));
            }
        }
        if (elements.isEmpty()) {
            throw new DatagenException("entity model " + name + " has no cubes for " + tops);
        }
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("0", textureModelId);
        textures.addProperty("particle", textureModelId);
        model.add("textures", textures);
        model.add("elements", elements);
        out.put(RP + "models/block/instrument/" + name + ".json", Json.write(model));
        return Json.TO_NS + ":block/instrument/" + name;
    }

    private static final String[] FACES = {"up", "down", "north", "south", "west", "east"};
    private static final double[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private static JsonObject element(Affine g, Cube c) {
        double[] a = g.apply(new double[] {c.x(), c.y(), c.z()});
        double[] b = g.apply(new double[] {c.x() + c.w(), c.y() + c.h(), c.z() + c.d()});
        JsonObject e = new JsonObject();
        JsonArray from = new JsonArray();
        JsonArray to = new JsonArray();
        for (int i = 0; i < 3; i++) {
            from.add(round(Math.min(a[i], b[i])));
            to.add(round(Math.max(a[i], b[i])));
        }
        e.add("from", from);
        e.add("to", to);
        JsonObject faces = new JsonObject();
        for (int i = 0; i < 6; i++) {
            double[] n = g.linear(NORMALS[i]);
            String face = n[0] > 0.5 ? "east" : n[0] < -0.5 ? "west" : n[1] > 0.5 ? "up" : n[1] < -0.5 ? "down" : n[2] > 0.5 ? "south"
                : "north";
            double[] uv = uv(FACES[i], c);
            JsonObject f = new JsonObject();
            JsonArray arr = new JsonArray();
            for (double v : uv) {
                arr.add(round(v * 16 / 64));
            }
            f.add("uv", arr);
            f.addProperty("texture", "#0");
            faces.add(face, f);
        }
        e.add("faces", faces);
        return e;
    }

    /** The box UV layout of {@code ModelPart.Cube} at {@code texOffs(u, v)} for width w, height h, depth d. */
    private static double[] uv(String face, Cube c) {
        double u = c.u();
        double v = c.v();
        double w = c.w();
        double h = c.h();
        double d = c.d();
        return switch (face) {
            case "up" -> new double[] {u + d, v, u + d + w, v + d};
            case "down" -> new double[] {u + d + w, v, u + d + 2 * w, v + d};
            case "west" -> new double[] {u, v + d, u + d, v + d + h};
            case "north" -> new double[] {u + d, v + d, u + d + w, v + d + h};
            case "east" -> new double[] {u + d + w, v + d, u + 2 * d + w, v + d + h};
            default -> new double[] {u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h};
        };
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0 + 0.0;
    }
}
