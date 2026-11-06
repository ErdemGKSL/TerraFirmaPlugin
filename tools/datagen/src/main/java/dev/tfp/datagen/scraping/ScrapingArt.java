/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/ScrapingBlockModel.java @ c3b4d9536 (drawTiles: one quad per cell at
 * y = 0.01, uv = the cell's 4 x 4 px of the texture, input texture where unscraped, output where scraped),
 * file: src/generated/resources/data/tfc/recipe/scraping/ @ c3b4d9536 (input_texture, output_texture)
 */
package dev.tfp.datagen.scraping;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.craft.ScrapingRules;
import dev.tfp.core.scraping.ScrapingVisuals;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * PAR-PROC-05: the look of the scraping surface for vanilla clients. TFC's {@code ScrapingBlockModel} is a dynamic model
 * that draws, per cell, a 4 x 4 px quad of the recipe's input or output texture; here the same quads are static block models
 * chosen per cell by a 1.21.4 item model definition on the {@code custom_model_data} flags of the item a display entity
 * shows (names in {@link ScrapingVisuals}). For every TFC scraping recipe:
 *
 * <ul>
 *   <li>its two textures, copied from TFC to {@code textures/scraping/<key>.png};</li>
 *   <li>per texture 16 tile models {@code block/scraping/<key>/cell_<i>} on 16 shared parents {@code block/scraping/cell_<i>}
 *       (one flat up face at TFC's height, tint index 0);</li>
 *   <li>the item model definition {@code items/scraping/<input key>.json}: a composite of 16 {@code condition}s on flag
 *       {@code i}, the output tile tinted by colour 1 when true, the input tile tinted by colour 0 when false.</li>
 * </ul>
 */
public final class ScrapingArt {
    public static final String RECIPES = TfcCheckout.GENERATED + "data/tfc/recipe/scraping/";
    public static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    public static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    /** TFC's cell size in texture pixels ({@code xOffset * 4f / 16f} of the sprite). */
    static final int CELL_PX = 16 / ScrapingRules.GRID;

    private ScrapingArt() {
    }

    /** True when the checkout has the scraping recipes (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return !tfc.list(RECIPES).isEmpty();
    }

    /** Writes the textures, models and item model definitions. Returns the number of recipes. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        Map<String, String[]> recipes = recipes(tfc);
        TreeSet<String> textures = new TreeSet<>();
        recipes.values().forEach(t -> {
            textures.add(t[0]);
            textures.add(t[1]);
        });
        for (int cell = 0; cell < ScrapingRules.CELLS; cell++) {
            out.put(modelPath(ScrapingVisuals.cellParent(cell)), Json.write(parent(cell)));
        }
        for (String texture : textures) {
            out.put(RP + "textures/" + ScrapingVisuals.FOLDER + "/" + ScrapingVisuals.key(texture) + ".png",
                tfc.read(TEXTURES + folder(texture) + ScrapingVisuals.key(texture) + ".png"));
            for (int cell = 0; cell < ScrapingRules.CELLS; cell++) {
                out.put(modelPath(ScrapingVisuals.tile(texture, cell)), Json.write(tile(texture, cell)));
            }
        }
        for (String[] t : recipes.values()) {
            String item = ScrapingVisuals.itemModel(t[0]);
            out.put(RP + "items/" + item.substring(item.indexOf(':') + 1) + ".json", Json.write(definition(t[0], t[1])));
        }
        return recipes.size();
    }

    /** Recipe file name to {input texture, output texture}, in our namespace, sorted by name. Fails on a duplicate look. */
    static Map<String, String[]> recipes(TfcCheckout tfc) {
        Map<String, String[]> out = new LinkedHashMap<>();
        TreeSet<String> looks = new TreeSet<>();
        for (String path : tfc.list(RECIPES)) {
            if (!path.endsWith(".json")) {
                continue;
            }
            JsonObject r = Json.parse(tfc.readString(path), path).getAsJsonObject();
            if (!"tfc:scraping".equals(r.get("type").getAsString())) {
                throw new DatagenException(path + ": not a tfc:scraping recipe");
            }
            String in = Json.renameId(r.get("input_texture").getAsString());
            String outTex = Json.renameId(r.get("output_texture").getAsString());
            if (!looks.add(ScrapingVisuals.itemModel(in))) {
                throw new DatagenException(path + ": a second scraping recipe with the input texture " + in
                    + " (the look is named by it)");
            }
            out.put(path.substring(path.lastIndexOf('/') + 1), new String[] {in, outTex});
        }
        if (out.isEmpty()) {
            throw new DatagenException("no scraping recipes under " + RECIPES);
        }
        return out;
    }

    /** {@code item/} or {@code block/}: the texture folder of a recipe texture id. */
    static String folder(String textureId) {
        String path = textureId.substring(textureId.indexOf(':') + 1);
        return path.substring(0, path.indexOf('/') + 1);
    }

    static String modelPath(String modelId) {
        return RP + "models/" + modelId.substring(modelId.indexOf(':') + 1) + ".json";
    }

    /** The parent of one cell: one flat element at TFC's height with an up face cut from {@code #layer}. */
    static JsonObject parent(int cell) {
        int x = ScrapingVisuals.column(cell) * CELL_PX;
        int z = ScrapingVisuals.row(cell) * CELL_PX;
        BigDecimal y = BigDecimal.valueOf(ScrapingVisuals.SURFACE_Y);
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "#layer");
        JsonObject up = new JsonObject();
        up.add("uv", ints(x, z, x + CELL_PX, z + CELL_PX));
        up.addProperty("texture", "#layer");
        up.addProperty("tintindex", 0);
        JsonObject faces = new JsonObject();
        faces.add("up", up);
        JsonObject element = new JsonObject();
        element.add("from", vec(x, y, z));
        element.add("to", vec(x + CELL_PX, y, z + CELL_PX));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject m = new JsonObject();
        m.add("textures", textures);
        m.add("elements", elements);
        return m;
    }

    static JsonObject tile(String texture, int cell) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer", ScrapingVisuals.texture(texture));
        JsonObject m = new JsonObject();
        m.addProperty("parent", ScrapingVisuals.cellParent(cell));
        m.add("textures", textures);
        return m;
    }

    /** The item model definition of one recipe. */
    static JsonObject definition(String input, String output) {
        JsonArray models = new JsonArray();
        for (int cell = 0; cell < ScrapingRules.CELLS; cell++) {
            JsonObject c = new JsonObject();
            c.addProperty("type", "minecraft:condition");
            c.addProperty("property", "minecraft:custom_model_data");
            c.addProperty("index", cell);
            c.add("on_true", tinted(ScrapingVisuals.tile(output, cell), ScrapingVisuals.OUTPUT_TINT));
            c.add("on_false", tinted(ScrapingVisuals.tile(input, cell), ScrapingVisuals.INPUT_TINT));
            models.add(c);
        }
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", models);
        JsonObject root = new JsonObject();
        root.add("model", composite);
        return root;
    }

    private static JsonObject tinted(String model, int tint) {
        JsonObject t = new JsonObject();
        t.addProperty("type", "minecraft:custom_model_data");
        t.addProperty("index", tint);
        t.addProperty("default", -1);
        JsonArray tints = new JsonArray();
        tints.add(t);
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", model);
        m.add("tints", tints);
        return m;
    }

    private static JsonArray ints(int... v) {
        JsonArray a = new JsonArray();
        for (int i : v) {
            a.add(i);
        }
        return a;
    }

    private static JsonElement vec(int x, BigDecimal y, int z) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }
}
