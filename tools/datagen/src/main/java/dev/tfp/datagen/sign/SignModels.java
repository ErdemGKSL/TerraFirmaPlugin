/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/textures/entity/signs/** @ c3b4d9536 (the sign textures, copied; TFC draws the boards
 * with vanilla's sign block entity renderers, so the geometry below is vanilla's SignRenderer and HangingSignRenderer model
 * layers re-expressed as block model elements)
 */
package dev.tfp.datagen.sign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * PAR-DECO-03: block models of the four TFC sign shapes, built from vanilla's sign model layers (64 x 32 entity textures,
 * box UV). TFC's signs have no block models (their blockstates only name a particle), the client draws them with the sign
 * block entity renderers; a vanilla client cannot, so each board is an item display of one of these models. Rotation 0 is a
 * sign whose front faces south (+z); the display turns it like a blockstate {@code y}.
 */
final class SignModels {
    static final int TEX_W = 64;
    static final int TEX_H = 32;
    /** Vanilla {@code SignRenderer.RENDER_SCALE}: standing and wall signs are drawn at 2/3. */
    static final double SCALE = 2.0 / 3.0;

    private SignModels() {
    }

    /** One cuboid: its corners in block pixels and its box UV (entity pixels: texture offset and size). */
    record Box(double x1, double y1, double z1, double x2, double y2, double z2, int u, int v, int w, int h, int d,
               double originX, double angle) {
        Box(double x1, double y1, double z1, double x2, double y2, double z2, int u, int v, int w, int h, int d) {
            this(x1, y1, z1, x2, y2, z2, u, v, w, h, d, 0, 0);
        }
    }

    /** The model of a list of boxes over one texture ({@code tfp:block/...}). */
    static String model(String texture, List<Box> boxes) {
        JsonObject m = new JsonObject();
        JsonArray size = new JsonArray();
        size.add(TEX_W);
        size.add(TEX_H);
        m.add("texture_size", size);
        JsonObject tex = new JsonObject();
        tex.addProperty("0", Json.TO_NS + ":" + texture);
        tex.addProperty("particle", Json.TO_NS + ":" + texture);
        m.add("textures", tex);
        JsonArray elements = new JsonArray();
        for (Box b : boxes) {
            elements.add(element(b));
        }
        m.add("elements", elements);
        return Json.write(m);
    }

    private static JsonObject element(Box b) {
        JsonObject e = new JsonObject();
        e.add("from", arr(b.x1(), b.y1(), b.z1()));
        e.add("to", arr(b.x2(), b.y2(), b.z2()));
        if (b.angle() != 0) {
            JsonObject r = new JsonObject();
            r.add("origin", arr(b.originX(), (b.y1() + b.y2()) / 2, (b.z1() + b.z2()) / 2));
            r.addProperty("axis", "y");
            r.addProperty("angle", b.angle());
            e.add("rotation", r);
        }
        int u = b.u();
        int v = b.v();
        int w = b.w();
        int h = b.h();
        int d = b.d();
        JsonObject faces = new JsonObject();
        boolean flat = d == 0;
        // vanilla Cube: front (u + d, v + d) is the -z entity face, which the sign model's z flip puts on +z (south)
        faces.add("south", face(u + d, v + d, w, h));
        faces.add("north", face(flat ? u + w : u + 2 * d + w, flat ? v : v + d, w, h));
        if (!flat) {
            faces.add("west", face(u, v + d, d, h));
            faces.add("east", face(u + d + w, v + d, d, h));
            faces.add("up", face(u + d, v, w, d));
            faces.add("down", face(u + d + w, v, w, d));
        }
        e.add("faces", faces);
        return e;
    }

    /** A face over {@code w x h} entity pixels at (x, y), as model uv (0..16 of the 64 x 32 texture), with the texture. */
    private static JsonObject face(int x, int y, int w, int h) {
        JsonObject f = new JsonObject();
        f.add("uv", arr(x * 16.0 / TEX_W, y * 16.0 / TEX_H, (x + w) * 16.0 / TEX_W, (y + h) * 16.0 / TEX_H));
        f.addProperty("texture", "#0");
        return f;
    }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(round(d));
        }
        return a;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    // ---- the four shapes ------------------------------------------------------------------------------------------------

    /** Standing sign: board 24 x 12 x 2 at (0, 0), post 2 x 14 x 2 at (0, 14), drawn at 2/3 with the post on the floor. */
    static List<Box> standing() {
        List<Box> boxes = new ArrayList<>();
        double bx = 24 * SCALE / 2;
        double bz = 2 * SCALE / 2;
        // model y runs down from the block centre (8 px): the board is -14..-2, the post -2..12 (flipped, then scaled)
        boxes.add(new Box(8 - bx, 8 + 2 * SCALE, 8 - bz, 8 + bx, 8 + 14 * SCALE, 8 + bz, 0, 0, 24, 12, 2));
        double px = 2 * SCALE / 2;
        boxes.add(new Box(8 - px, 8 - 12 * SCALE, 8 - px, 8 + px, 8 + 2 * SCALE, 8 + px, 0, 14, 2, 14, 2));
        return boxes;
    }

    /** Wall sign: the board alone, 3 px above the floor of the block, 1 px from the north face (vanilla translate 0, -0.3125, -0.4375). */
    static List<Box> wall() {
        double bx = 24 * SCALE / 2;
        double bz = 2 * SCALE / 2;
        double cy = 8 - 5;
        double cz = 8 - 7;
        return List.of(new Box(8 - bx, cy + 2 * SCALE, cz - bz, 8 + bx, cy + 14 * SCALE, cz + bz, 0, 0, 24, 12, 2));
    }

    /**
     * Ceiling hanging sign: the board 14 x 10 x 2 at (0, 12), hung 10 px below the 16 px line, and its chains: two crossed
     * planes at each end, or (attached to a block beside it) the flat V plane. Vanilla also draws the plank (16 x 2 x 4 at
     * (0, 0)) only on the wall variant.
     */
    static List<Box> ceilingHanging(boolean attached) {
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(1, 0, 7, 15, 10, 9, 0, 12, 14, 10, 2));
        if (attached) {
            boxes.add(new Box(2, 10, 8, 14, 16, 8, 14, 6, 12, 6, 0));
        } else {
            for (double x : new double[] {3, 13}) {
                boxes.add(new Box(x - 1.5, 10, 8, x + 1.5, 16, 8, 0, 6, 3, 6, 0, x, -45));
                boxes.add(new Box(x - 1.5, 10, 8, x + 1.5, 16, 8, 6, 6, 3, 6, 0, x, 45));
            }
        }
        return boxes;
    }

    /** Wall hanging sign: the plank (the beam it hangs from) over the board. */
    static List<Box> wallHanging() {
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(1, 0, 7, 15, 10, 9, 0, 12, 14, 10, 2));
        boxes.add(new Box(0, 14, 6, 16, 16, 10, 0, 0, 16, 2, 4));
        return boxes;
    }
}
