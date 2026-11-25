/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/resources/assets/tfc/textures/entity/chest/** @ c3b4d9536 (the chest textures, copied; TFC draws a chest
 * with vanilla's chest block entity renderer, so the geometry below is vanilla's ChestRenderer model layers
 * re-expressed as block model elements)
 */
package dev.tfp.datagen.chest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * PAR-DECO-04: block models of the three chest shapes (single, left half, right half), built from vanilla's chest model
 * layers (64 x 64 entity textures, box UV): the body, the lid and the lock. TFC's chests have no block models, the client
 * draws them with the chest block entity renderer; a vanilla client cannot, so each chest is an item display of one of
 * these models. The front (the lock side) is south (+z): the display turns it like a blockstate {@code y}.
 *
 * <p>Geometry (block pixels, y up; vanilla's entity model flipped by {@code scale(1, -1, -1)}): single chest body
 * 14 x 10 x 14 at (1, 0, 1), lid 14 x 5 x 14 at (1, 9, 1), lock 2 x 4 x 1 at (7, 7, 15). Double halves are 15 wide and reach
 * the joined side: the left half (its partner on the clockwise side, west for a south facing chest) spans x 0 to 15 with its
 * lock at the seam (x 0), the right half x 1 to 16 with the lock at x 15. The top row of the body is hidden under the lid in
 * vanilla (they overlap by one pixel); the body here stops at y 9 so the two do not z-fight.
 */
final class ChestModels {
    static final int TEX = 64;

    private ChestModels() {
    }

    /** One cuboid: corners in block pixels, box UV (texture offset and the entity box size) and how many top rows to skip. */
    record Box(double x1, double y1, double z1, double x2, double y2, double z2, int u, int v, int w, int h, int d, int trimTop) {
        Box(double x1, double y1, double z1, double x2, double y2, double z2, int u, int v, int w, int h, int d) {
            this(x1, y1, z1, x2, y2, z2, u, v, w, h, d, 0);
        }
    }

    /** The boxes of {@code type}: {@code single}, {@code left} or {@code right}. */
    static List<Box> boxes(String type) {
        List<Box> boxes = new ArrayList<>();
        switch (type) {
            case "single" -> {
                boxes.add(new Box(1, 0, 1, 15, 9, 15, 0, 19, 14, 10, 14, 1));
                boxes.add(new Box(1, 9, 1, 15, 14, 15, 0, 0, 14, 5, 14));
                boxes.add(new Box(7, 7, 15, 9, 11, 16, 0, 0, 2, 4, 1));
            }
            case "left" -> {
                boxes.add(new Box(0, 0, 1, 15, 9, 15, 0, 19, 15, 10, 14, 1));
                boxes.add(new Box(0, 9, 1, 15, 14, 15, 0, 0, 15, 5, 14));
                boxes.add(new Box(0, 7, 15, 1, 11, 16, 0, 0, 1, 4, 1));
            }
            case "right" -> {
                boxes.add(new Box(1, 0, 1, 16, 9, 15, 0, 19, 15, 10, 14, 1));
                boxes.add(new Box(1, 9, 1, 16, 14, 15, 0, 0, 15, 5, 14));
                boxes.add(new Box(15, 7, 15, 16, 11, 16, 0, 0, 1, 4, 1));
            }
            default -> throw new IllegalArgumentException(type);
        }
        return boxes;
    }

    /** The block model JSON of a chest shape over {@code texture} ({@code tfp:block/...}), particles of {@code particle}. */
    static String model(String texture, String particle, String type) {
        JsonObject m = new JsonObject();
        m.addProperty("parent", "minecraft:block/block");
        JsonArray size = new JsonArray();
        size.add(TEX);
        size.add(TEX);
        m.add("texture_size", size);
        JsonObject tex = new JsonObject();
        tex.addProperty("0", Json.TO_NS + ":" + texture);
        tex.addProperty("particle", Json.TO_NS + ":" + particle);
        m.add("textures", tex);
        JsonArray elements = new JsonArray();
        for (Box b : boxes(type)) {
            elements.add(element(b));
        }
        m.add("elements", elements);
        m.add("display", display());
        return Json.write(m);
    }

    /** The item model of a chest: the single model with vanilla's chest item display transforms. */
    static String itemModel(String parent) {
        JsonObject m = new JsonObject();
        m.addProperty("parent", Json.TO_NS + ":" + parent);
        m.addProperty("gui_light", "front");
        return Json.write(m);
    }

    /** Vanilla {@code item/chest.json}'s display table. */
    private static JsonObject display() {
        JsonObject d = new JsonObject();
        d.add("gui", transform(30, 45, 0, 0, 0, 0, 0.625));
        d.add("ground", transform(0, 0, 0, 0, 3, 0, 0.25));
        d.add("head", transform(0, 180, 0, 0, 0, 0, 1));
        d.add("fixed", transform(0, 180, 0, 0, 0, 0, 0.5));
        return d;
    }

    private static JsonObject transform(double rx, double ry, double rz, double tx, double ty, double tz, double s) {
        JsonObject t = new JsonObject();
        t.add("rotation", arr(rx, ry, rz));
        t.add("translation", arr(tx, ty, tz));
        t.add("scale", arr(s, s, s));
        return t;
    }

    private static JsonObject element(Box b) {
        JsonObject e = new JsonObject();
        e.add("from", arr(b.x1(), b.y1(), b.z1()));
        e.add("to", arr(b.x2(), b.y2(), b.z2()));
        int u = b.u();
        int v = b.v();
        int w = b.w();
        int h = b.h();
        int d = b.d();
        int skip = b.trimTop();
        JsonObject faces = new JsonObject();
        // vanilla Cube: front (u + d, v + d) is the -z entity face, which the model's flip puts on +z (south)
        faces.add("south", face(u + d, v + d + skip, w, h - skip));
        faces.add("north", face(u + 2 * d + w, v + d + skip, w, h - skip));
        faces.add("west", face(u, v + d + skip, d, h - skip));
        faces.add("east", face(u + d + w, v + d + skip, d, h - skip));
        if (skip == 0) {
            faces.add("up", face(u + d, v, w, d));
        }
        faces.add("down", face(u + d + w, v, w, d));
        e.add("faces", faces);
        return e;
    }

    private static JsonObject face(int x, int y, int w, int h) {
        JsonObject f = new JsonObject();
        f.add("uv", arr(x * 16.0 / TEX, y * 16.0 / TEX, (x + w) * 16.0 / TEX, (y + h) * 16.0 / TEX));
        f.addProperty("texture", "#0");
        return f;
    }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(Math.round(d * 10000.0) / 10000.0);
        }
        return a;
    }
}
