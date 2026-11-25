/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/ @ c3b4d9536 (the recipes the field guide shows),
 *       src/main/resources/assets/tfc/patchouli_books/field_guide/en_us/templates/ @ c3b4d9536 (what each recipe page shows)
 */
package dev.tfp.datagen.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The field guide's recipe pages as text tables (PAR-DATA-04): a vanilla book cannot draw TFC's recipe widgets, so each
 * recipe is written out in Patchouli markup. A grid (crafting pattern, knapping pattern) uses {@code -} for an empty
 * square; both {@code -} and the key letters are 6 px wide in the vanilla font, so the columns line up.
 *
 * <p>Recipes are read from TFC's generated data ({@code data/<ns>/recipe/<path>.json}). A vanilla recipe the checkout
 * does not hold ({@code minecraft:book}) is shown by its result name only. A missing TFC recipe is an error.
 */
public final class GuideRecipes {
    static final String RECIPES = TfcCheckout.GENERATED + "data/";
    static final String ARROW = " -> ";

    private final TfcCheckout tfc;
    private final GuideNames names;

    public GuideRecipes(TfcCheckout tfc, GuideNames names) {
        this.tfc = tfc;
        this.names = names;
    }

    /** The recipe JSON, or null for a vanilla recipe the checkout does not hold. */
    JsonObject recipe(String id, String source) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            throw new DatagenException(source + ": recipe id " + id + " has no namespace");
        }
        String file = RECIPES + id.substring(0, colon) + "/recipe/" + id.substring(colon + 1) + ".json";
        if (!tfc.exists(file)) {
            if (id.startsWith("minecraft:")) {
                return null;
            }
            throw new DatagenException(source + ": field guide recipe " + id + " is not in the checkout (" + file + ")");
        }
        return Json.parse(tfc.readString(file), file).getAsJsonObject();
    }

    /** One recipe as markup lines, headed by its kind and result. */
    public String describe(String id, String source) {
        JsonObject r = recipe(id, source);
        if (r == null) {
            return bold("Crafting") + ": " + GuideNames.humanize(id);
        }
        String type = r.get("type").getAsString();
        return switch (type) {
            case "minecraft:crafting_shaped", "tfc:advanced_shaped_crafting" -> shaped(r);
            case "minecraft:crafting_shapeless", "tfc:advanced_shapeless_crafting" -> shapeless(r);
            case "tfc:heating" -> heating(r);
            case "tfc:knapping" -> knapping(r, List.of(r));
            case "tfc:anvil" -> anvil(r);
            case "tfc:welding" -> welding(r);
            case "tfc:quern" -> bold("Quern") + ": " + ingredient(r.get("ingredient")) + ARROW + result(r.get("result"));
            case "tfc:loom" -> bold("Loom") + ": " + ingredient(r.get("ingredient")) + ARROW + result(r.get("result"));
            case "tfc:barrel_sealed", "tfc:barrel_instant" -> barrel(r, type.equals("tfc:barrel_sealed"));
            case "tfc:glassworking" -> glassworking(r);
            default -> throw new DatagenException(source + ": field guide recipe " + id + " has an unsupported type " + type);
        };
    }

    /** Several knapping recipes of one shape (TFC's rock knapping page: one per rock category). */
    public String knappingGroup(List<String> ids, String source) {
        List<JsonObject> recipes = new ArrayList<>();
        for (String id : ids) {
            JsonObject r = recipe(id, source);
            if (r == null || !r.get("type").getAsString().equals("tfc:knapping")) {
                throw new DatagenException(source + ": rock knapping recipe " + id + " is not a knapping recipe");
            }
            recipes.add(r);
        }
        if (recipes.isEmpty()) {
            throw new DatagenException(source + ": rock knapping page without recipes");
        }
        return knapping(recipes.getFirst(), recipes);
    }

    private String shaped(JsonObject r) {
        StringBuilder sb = new StringBuilder(bold("Crafting") + ": " + result(r.get("result")));
        for (JsonElement row : r.getAsJsonArray("pattern")) {
            sb.append("$(br)").append(row.getAsString().replace(' ', '-'));
        }
        Map<String, String> legend = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : r.getAsJsonObject("key").entrySet()) {
            legend.put(e.getKey(), ingredient(e.getValue()));
        }
        legend.forEach((k, v) -> sb.append("$(br)").append(k).append(" = ").append(v));
        return sb.toString();
    }

    private String shapeless(JsonObject r) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonElement e : r.getAsJsonArray("ingredients")) {
            counts.merge(ingredient(e), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((name, n) -> parts.add(n > 1 ? n + " x " + name : name));
        return bold("Crafting") + ": " + result(r.get("result")) + "$(br)" + String.join(" + ", parts);
    }

    private String heating(JsonObject r) {
        float t = r.get("temperature").getAsFloat();
        List<String> out = new ArrayList<>();
        if (r.has("result_item")) {
            out.add(result(r.get("result_item")));
        }
        if (r.has("result_fluid")) {
            out.add(fluid(r.getAsJsonObject("result_fluid")));
        }
        String heat = new GuideConfig(tfc, names).heat((int) t);
        return bold("Heating") + ": " + ingredient(r.get("ingredient")) + ARROW
            + (out.isEmpty() ? "nothing" : String.join(" + ", out)) + "$(br)at " + (heat.isEmpty() ? "" : heat + "$() ")
            + "(" + GuideConfig.temperature((int) t) + ")";
    }

    private String knapping(JsonObject first, List<JsonObject> all) {
        String kind = GuideNames.humanize(first.get("knapping_type").getAsString());
        List<String> results = new ArrayList<>();
        for (JsonObject r : all) {
            String name = result(r.get("result"));
            if (!results.contains(name)) {
                results.add(name);
            }
        }
        StringBuilder sb = new StringBuilder(bold(kind + " Knapping") + ": " + String.join(" / ", results));
        JsonArray pattern = first.getAsJsonArray("pattern");
        for (JsonElement row : pattern) {
            StringBuilder line = new StringBuilder();
            for (char c : row.getAsString().toCharArray()) {
                line.append(c == ' ' ? '-' : 'X');
            }
            sb.append("$(br)").append(line);
        }
        sb.append("$(br)X = keep, - = remove");
        return sb.toString();
    }

    private String anvil(JsonObject r) {
        StringBuilder sb = new StringBuilder(bold("Anvil") + ": " + ingredient(r.get("ingredient")) + ARROW
            + result(r.get("result")));
        if (r.has("tier")) {
            sb.append("$(br)").append(tier(r.get("tier").getAsInt())).append(" anvil");
        }
        if (r.has("rules")) {
            List<String> rules = new ArrayList<>();
            for (JsonElement e : r.getAsJsonArray("rules")) {
                rules.add(GuideNames.humanize(e.getAsString()));
            }
            sb.append("$(br)Rules: ").append(String.join(", ", rules));
        }
        return sb.toString();
    }

    private String welding(JsonObject r) {
        StringBuilder sb = new StringBuilder(bold("Welding") + ": " + ingredient(r.get("first_input")) + " + "
            + ingredient(r.get("second_input")) + ARROW + result(r.get("result")));
        if (r.has("tier")) {
            sb.append("$(br)").append(tier(r.get("tier").getAsInt())).append(" anvil");
        }
        return sb.toString();
    }

    private String barrel(JsonObject r, boolean sealed) {
        List<String> in = new ArrayList<>();
        if (r.has("input_item")) {
            in.add(ingredient(r.get("input_item")));
        }
        if (r.has("input_fluid")) {
            in.add(fluid(r.getAsJsonObject("input_fluid")));
        }
        List<String> out = new ArrayList<>();
        if (r.has("output_item")) {
            out.add(result(r.get("output_item")));
        }
        if (r.has("output_fluid")) {
            out.add(fluid(r.getAsJsonObject("output_fluid")));
        }
        String head = sealed ? "Sealed Barrel" : "Barrel";
        String s = bold(head) + ": " + String.join(" + ", in) + ARROW + (out.isEmpty() ? "nothing" : String.join(" + ", out));
        if (sealed && r.has("duration")) {
            s += "$(br)Takes " + GuideConfig.duration(r.get("duration").getAsInt());
        }
        return s;
    }

    private String glassworking(JsonObject r) {
        List<String> ops = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("operations")) {
            String op = e.getAsString();
            String key = names.key("glass_operation." + GuideNames.dotted(op));
            ops.add(key != null ? key : GuideNames.humanize(op));
        }
        return bold("Glassworking") + ": " + result(r.get("result")) + "$(br)From " + ingredient(r.get("batch"))
            + "$(br)Steps: " + String.join(", ", ops);
    }

    String tier(int tier) {
        String v = names.key("tfc.tooltip.tier_" + tier);
        return v != null ? v : "Tier " + tier;
    }

    /** An ingredient as text: an item, a tag, a list of alternatives, or TFC's compound ingredients. */
    String ingredient(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "nothing";
        }
        if (e.isJsonPrimitive()) {
            String s = e.getAsString();
            return s.startsWith("#") ? names.tag(s) : names.item(s);
        }
        if (e.isJsonArray()) {
            List<String> alts = new ArrayList<>();
            for (JsonElement a : e.getAsJsonArray()) {
                String s = ingredient(a);
                if (!alts.contains(s)) {
                    alts.add(s);
                }
            }
            return String.join(" or ", alts);
        }
        JsonObject o = e.getAsJsonObject();
        String base;
        if (o.has("item")) {
            base = names.item(o.get("item").getAsString());
        } else if (o.has("tag")) {
            base = names.tag(o.get("tag").getAsString());
        } else if (o.has("type")) {
            base = typed(o);
        } else if (o.has("ingredient")) {
            base = ingredient(o.get("ingredient"));
        } else {
            base = "anything";
        }
        int count = o.has("count") ? o.get("count").getAsInt() : 1;
        return count > 1 ? count + " x " + base : base;
    }

    private String typed(JsonObject o) {
        String type = o.get("type").getAsString();
        switch (type) {
            case "tfc:and" -> {
                List<String> main = new ArrayList<>();
                List<String> mods = new ArrayList<>();
                for (JsonElement c : o.getAsJsonArray("children")) {
                    JsonObject co = c.getAsJsonObject();
                    if (co.has("item") || co.has("tag") || co.has("children")) {
                        main.add(ingredient(c));
                    } else {
                        mods.add(ingredient(c));
                    }
                }
                String m = main.isEmpty() ? "anything" : String.join(" and ", main);
                return mods.isEmpty() ? m : m + " (" + String.join(", ", mods) + ")";
            }
            case "neoforge:compound" -> {
                return ingredient(o.get("children"));
            }
            case "tfc:not_rotten" -> {
                return "not rotten";
            }
            case "tfc:fluid_content" -> {
                return "container of " + fluid(o.getAsJsonObject("fluid"));
            }
            default -> {
                return GuideNames.humanize(type).toLowerCase(Locale.ROOT);
            }
        }
    }

    /** A fluid stack or fluid ingredient: {@code <amount> mB <name>}. */
    String fluid(JsonObject o) {
        String name;
        if (o.has("id")) {
            name = names.fluid(o.get("id").getAsString());
        } else if (o.has("fluid")) {
            name = names.fluid(o.get("fluid").getAsString());
        } else if (o.has("tag")) {
            name = names.tag(o.get("tag").getAsString());
        } else {
            name = "fluid";
        }
        return o.has("amount") ? o.get("amount").getAsInt() + " mB " + name : name;
    }

    /** An item result ({@code {id, count}} or TFC's {@code {stack: {...}, modifiers}}), bold, with its count. */
    String result(JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return "nothing";
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("stack")) {
            return result(o.get("stack"));
        }
        if (!o.has("id")) {
            return o.has("modifiers") ? "the input item" : "nothing";
        }
        int count = o.has("count") ? o.get("count").getAsInt() : 1;
        String name = names.item(o.get("id").getAsString());
        return (count > 1 ? count + " x " : "") + name;
    }

    static String bold(String s) {
        return "$(l)" + s + "$()";
    }
}
