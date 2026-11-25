/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/generated/resources/data/tfc/painting_variant/*.json, data/minecraft/tags/painting_variant/placeable.json,
 *        src/main/resources/data/tfc/trim_material/*.json, assets/minecraft/atlases/armor_trims.json,
 *        assets/tfc/textures/painting/*.png, assets/tfc/textures/color_palettes/trims/*.png,
 *        assets/tfc/lang/*.json (painting.tfc.* and trim_material.tfc.*) @ c3b4d9536 (copied, renamed)
 */
package dev.tfp.datagen.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PAR-DATA-11: TFC's five painting variants and 14 armour trim materials (gems and metals) as {@code tfp:} entries of the
 * server's {@code painting_variant} and {@code trim_material} registries, in the bootstrap datapack.
 *
 * <ul>
 * <li>Paintings: TFC's {@code asset_id}, size and the {@code minecraft:painting_variant/placeable} tag entries, renamed to
 * {@code tfp:}. A title and author component is added (TFC names them only by lang key). The 16/32/48 px textures go to the
 * resource pack under {@code tfp:painting/<name>}.</li>
 * <li>Trim materials: the {@code asset_name} (TFC's {@code <name>_tfc}, here {@code <name>_tfp}) and description. The
 * {@code ingredient} and {@code item_model_index} fields of TFC's 1.21.1 format no longer exist in 1.21.11 and are dropped;
 * the ingredient pairs go to {@link #INGREDIENTS_FILE} for the {@code /tfp trim} command. The 14 palette textures ship as
 * {@code tfp:color_palettes/trims/<name>} and one {@code armor_trims} atlas adds the paletted permutations for every vanilla
 * pattern (the vanilla palette key and textures; the atlas file merges with vanilla's).</li>
 * </ul>
 */
public final class PaintingsAndTrims {
    public static final String NS = Json.TO_NS;
    public static final String PAINTING_SOURCE = TfcCheckout.GENERATED + "data/tfc/painting_variant/";
    public static final String PLACEABLE_SOURCE = TfcCheckout.GENERATED + "data/minecraft/tags/painting_variant/placeable.json";
    public static final String TRIM_SOURCE = TfcCheckout.MAIN + "data/tfc/trim_material/";
    public static final String PAINTING_TEXTURES = TfcCheckout.ASSETS + "textures/painting/";
    public static final String PALETTE_TEXTURES = TfcCheckout.ASSETS + "textures/color_palettes/trims/";
    public static final String LANG = TfcCheckout.ASSETS + "lang/";
    /** The bootstrap datapack ({@code SeasonalBiomes.OUT}). */
    public static final String DATAPACK = "datapack/tfp_seasonal_biomes/data/";
    public static final String PAINTING_TARGET = DATAPACK + NS + "/painting_variant/";
    public static final String TRIM_TARGET = DATAPACK + NS + "/trim_material/";
    public static final String PLACEABLE_TARGET = DATAPACK + "minecraft/tags/painting_variant/placeable.json";
    public static final String RP = "craftengine/tfp/resourcepack/assets/";
    public static final String LANG_RP = RP + "tfp_registry/lang/";
    public static final String ATLAS = RP + "minecraft/atlases/armor_trims.json";
    /** Trim material ingredient item ids (renamed) to the material name, for {@code /tfp trim}. */
    public static final String INGREDIENTS_FILE = "data/tfp/data_maps/trim_materials.json";
    /** Vanilla armour trim patterns (1.21.11), whose humanoid textures get the TFC palettes. */
    static final List<String> PATTERNS = List.of("coast", "sentry", "dune", "wild", "ward", "eye", "vex", "tide", "snout", "rib",
        "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt");

    private PaintingsAndTrims() {
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(PLACEABLE_SOURCE) && !tfc.list(TRIM_SOURCE).isEmpty();
    }

    /** What was shipped: painting names and trim material names to their ingredient item id. */
    public record Result(List<String> paintings, Map<String, String> trimIngredients) {
    }

    public static Result run(TfcCheckout tfc, OutputTree out) {
        List<String> paintings = new ArrayList<>();
        for (String src : tfc.list(PAINTING_SOURCE)) {
            if (!src.endsWith(".json")) {
                continue;
            }
            String name = src.substring(PAINTING_SOURCE.length(), src.length() - ".json".length());
            JsonObject o = Json.rename(Json.parse(tfc.readString(src), src)).getAsJsonObject();
            if (!o.has("asset_id") || !o.has("width") || !o.has("height")) {
                throw new DatagenException("painting " + name + " lacks asset_id, width or height: " + o);
            }
            String texture = PAINTING_TEXTURES + name + ".png";
            if (!tfc.exists(texture)) {
                throw new DatagenException("painting " + name + " has no texture " + texture);
            }
            o.add("title", translate("painting." + NS + "." + name + ".title"));
            o.add("author", translate("painting." + NS + "." + name + ".author"));
            out.put(PAINTING_TARGET + name + ".json", Json.write(o));
            out.put(RP + NS + "/textures/painting/" + name + ".png", tfc.read(texture));
            paintings.add(name);
        }
        JsonElement placeable = Json.rename(Json.parse(tfc.readString(PLACEABLE_SOURCE), PLACEABLE_SOURCE));
        for (JsonElement v : placeable.getAsJsonObject().getAsJsonArray("values")) {
            String id = v.getAsString();
            if (!id.startsWith(NS + ":") || !paintings.contains(id.substring(NS.length() + 1))) {
                throw new DatagenException("placeable tag names " + id + ", which is not a shipped painting variant");
            }
        }
        out.put(PLACEABLE_TARGET, Json.write(placeable));

        Map<String, String> ingredients = new TreeMap<>();
        JsonObject permutations = new JsonObject();
        for (String src : tfc.list(TRIM_SOURCE)) {
            if (!src.endsWith(".json")) {
                continue;
            }
            String name = src.substring(TRIM_SOURCE.length(), src.length() - ".json".length());
            JsonObject in = Json.parse(tfc.readString(src), src).getAsJsonObject();
            String palette = PALETTE_TEXTURES + name + ".png";
            if (!tfc.exists(palette)) {
                throw new DatagenException("trim material " + name + " has no palette " + palette);
            }
            String assetName = name + "_" + NS;
            JsonObject description = in.getAsJsonObject("description").deepCopy();
            description.addProperty("translate", "trim_material." + NS + "." + name);
            JsonObject o = new JsonObject();
            o.addProperty("asset_name", assetName);
            o.add("description", description);
            out.put(TRIM_TARGET + name + ".json", Json.write(o));
            out.put(RP + NS + "/textures/color_palettes/trims/" + name + ".png", tfc.read(palette));
            permutations.addProperty(assetName, NS + ":color_palettes/trims/" + name);
            ingredients.put(name, Json.renameId(in.get("ingredient").getAsString()));
        }
        out.put(ATLAS, Json.write(atlas(permutations)));
        JsonObject map = new JsonObject();
        ingredients.forEach((name, item) -> map.addProperty(item, name));
        out.put(INGREDIENTS_FILE, Json.write(map));
        writeLang(tfc, out, paintings, ingredients.keySet());
        return new Result(List.copyOf(paintings), Map.copyOf(ingredients));
    }

    private static JsonObject translate(String key) {
        JsonObject o = new JsonObject();
        o.addProperty("translate", key);
        return o;
    }

    private static JsonObject atlas(JsonObject permutations) {
        JsonArray textures = new JsonArray();
        for (String p : PATTERNS) {
            textures.add("trims/entity/humanoid/" + p);
            textures.add("trims/entity/humanoid_leggings/" + p);
        }
        JsonObject source = new JsonObject();
        source.addProperty("type", "paletted_permutations");
        source.addProperty("palette_key", "trims/color_palettes/trim_palette");
        source.add("textures", textures);
        source.add("permutations", permutations);
        JsonArray sources = new JsonArray();
        sources.add(source);
        JsonObject root = new JsonObject();
        root.add("sources", sources);
        return root;
    }

    /** TFC's titles, authors and material names, under {@code painting.tfp.*} and {@code trim_material.tfp.*}. */
    private static void writeLang(TfcCheckout tfc, OutputTree out, List<String> paintings, java.util.Set<String> materials) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            JsonObject o = new JsonObject();
            for (String p : paintings) {
                for (String part : List.of("title", "author")) {
                    copy(src, o, "painting." + Json.FROM_NS + "." + p + "." + part, "painting." + NS + "." + p + "." + part);
                }
            }
            for (String m : materials) {
                copy(src, o, "trim_material." + Json.FROM_NS + "." + m, "trim_material." + NS + "." + m);
            }
            if (o.size() > 0) {
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
        if (!out.contains(LANG_RP + "en_us.json")
            || Json.parse(new String(out.get(LANG_RP + "en_us.json"), java.nio.charset.StandardCharsets.UTF_8), "lang")
                .getAsJsonObject().size() != paintings.size() * 2 + materials.size()) {
            throw new DatagenException("TFC's English lang lacks a painting or trim material name");
        }
    }

    private static void copy(JsonObject from, JsonObject to, String fromKey, String toKey) {
        if (from.has(fromKey)) {
            to.addProperty(toKey, from.get(fromKey).getAsString());
        }
    }
}
