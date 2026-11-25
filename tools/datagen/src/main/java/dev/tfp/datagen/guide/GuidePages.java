/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/patchouli_books/field_guide/en_us/entries/ @ c3b4d9536 (page bodies),
 *       src/main/java/net/dries007/tfc/compat/patchouli/component/TableComponent.java @ c3b4d9536 (table layout: rows of
 *       columns + 1 cells, the legend)
 */
package dev.tfp.datagen.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts one Patchouli page of TFC's field guide into a text page for a vanilla written book (PAR-DATA-04): its
 * title and its body in Patchouli markup with every {@code $(cfg:...)} resolved.
 *
 * <ul>
 *   <li>text: the text and title;</li>
 *   <li>crafting and TFC's recipe pages (heat, knapping, rock knapping, anvil, welding, quern, loom, barrel, glassworking):
 *       the recipe as a text table ({@link GuideRecipes}), then the page text;</li>
 *   <li>spotlight: the item names as the title when the page has none;</li>
 *   <li>multiblock, multimultiblock: the structure's name and the blocks it is made of, then the text;</li>
 *   <li>entity: the entity's name (when the page gives no name), then the text;</li>
 *   <li>image: the caption only (a book cannot draw TFC's screenshots);</li>
 *   <li>TFC tables: one line per row, colour cells as coloured squares, then the legend;</li>
 *   <li>empty and custom pages: dropped (they hold no text).</li>
 * </ul>
 * An unknown page type is an error.
 */
public final class GuidePages {
    /** A converted page: title (null when none) and Patchouli markup text. */
    public record Converted(String title, String text) {
    }

    /** The square a table's colour cell is drawn with. */
    static final String CELL = "■";

    private final GuideNames names;
    private final GuideConfig config;
    private final GuideRecipes recipes;

    public GuidePages(GuideNames names, GuideConfig config, GuideRecipes recipes) {
        this.names = names;
        this.config = config;
        this.recipes = recipes;
    }

    public GuideNames names() {
        return names;
    }

    public static GuidePages of(TfcCheckout tfc) {
        return of(tfc, "en_us");
    }

    /** The converter of {@code locale}: item, tag and fluid names from that locale's lang (English where it has none). */
    public static GuidePages of(TfcCheckout tfc, String locale) {
        GuideNames names = GuideNames.read(tfc, locale);
        return new GuidePages(names, new GuideConfig(tfc, names), new GuideRecipes(tfc, names));
    }

    /** The page as text, or null when it holds none. */
    public Converted convert(JsonObject p, String source) {
        String type = p.get("type").getAsString();
        String title = str(p, "title");
        String text = str(p, "text");
        String body = switch (type) {
            case "patchouli:text" -> text;
            case "patchouli:crafting" -> {
                List<String> parts = new ArrayList<>();
                for (String k : new String[] {"recipe", "recipe2"}) {
                    if (p.has(k)) {
                        parts.add(recipes.describe(p.get(k).getAsString(), source));
                    }
                }
                yield join(String.join("$(br2)", parts), text);
            }
            case "tfc:heat_recipe", "tfc:knapping_recipe", "tfc:anvil_recipe", "tfc:welding_recipe", "tfc:quern_recipe",
                 "tfc:loom_recipe", "tfc:sealed_barrel_recipe", "tfc:instant_barrel_recipe", "tfc:glassworking_recipe" ->
                join(recipes.describe(required(p, "recipe", source), source), text);
            case "tfc:rock_knapping_recipe" -> {
                List<String> ids = new ArrayList<>();
                for (JsonElement e : p.getAsJsonArray("recipes")) {
                    ids.add(e.getAsString());
                }
                yield join(recipes.knappingGroup(ids, source), text);
            }
            case "patchouli:spotlight" -> {
                if (title == null || title.isBlank()) {
                    List<String> items = new ArrayList<>();
                    for (String id : required(p, "item", source).split(",")) {
                        String n = names.item(id.trim());
                        if (!items.contains(n)) {
                            items.add(n);
                        }
                    }
                    title = String.join(" / ", items);
                }
                yield text;
            }
            case "patchouli:multiblock" -> {
                title = blankToNull(str(p, "name"));
                // a registered multiblock (multiblock_id) has no mapping in the page
                yield join(p.has("multiblock") && p.get("multiblock").isJsonObject()
                    ? blocks(List.of(p.getAsJsonObject("multiblock"))) : null, text);
            }
            case "tfc:multimultiblock" -> {
                title = blankToNull(str(p, "name"));
                List<JsonObject> mbs = new ArrayList<>();
                for (JsonElement e : p.getAsJsonArray("multiblocks")) {
                    if (e.isJsonObject()) { // a string is a registered multiblock's id, without a mapping
                        mbs.add(e.getAsJsonObject());
                    }
                }
                yield join(blocks(mbs), text);
            }
            case "patchouli:entity" -> {
                String name = blankToNull(str(p, "name"));
                title = name != null ? name : names.entity(required(p, "entity", source));
                yield text;
            }
            case "patchouli:image" -> text == null ? null : "$(o)" + text + "$()";
            case "tfc:table", "tfc:table_small" -> {
                title = blankToNull(title);
                yield join(table(p, source), text);
            }
            case "patchouli:empty", "patchouli:custom" -> null;
            default -> throw new DatagenException(source + ": unsupported field guide page type " + type);
        };
        if (body == null || body.isBlank()) {
            if (title == null || title.isBlank()) {
                return null;
            }
            body = "";
        }
        return new Converted(title == null ? null : config.substitute(title, source), config.substitute(body, source));
    }

    /** The blocks of one or more multiblocks, by name, in pattern order. */
    private String blocks(List<JsonObject> multiblocks) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonObject mb : multiblocks) {
            if (!mb.has("mapping")) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : mb.getAsJsonObject("mapping").entrySet()) {
                String state = e.getValue().getAsString();
                String bare = GuideNames.strip(state);
                if (bare.equals("minecraft:air") || bare.isEmpty()) {
                    continue;
                }
                out.add(bare.startsWith("#") ? names.tag(bare) : names.block(bare));
            }
        }
        return out.isEmpty() ? null : "$(o)Built from: " + String.join(", ", out) + "$()";
    }

    /** A TFC table: rows of {@code columns + 1} cells (the first names the row), then the legend. */
    private String table(JsonObject p, String source) {
        int columns = p.get("columns").getAsInt();
        JsonArray cells = p.getAsJsonArray("strings");
        int width = columns + 1;
        if (cells.size() % width != 0) {
            throw new DatagenException(source + ": table of " + cells.size() + " cells is not rows of " + width);
        }
        List<String> lines = new ArrayList<>();
        for (int row = 0; row < cells.size() / width; row++) {
            StringBuilder line = new StringBuilder();
            boolean colour = false;
            for (int c = 0; c < width; c++) {
                JsonObject cell = cells.get(row * width + c).getAsJsonObject();
                if (cell.has("fill")) {
                    String hex = cell.get("fill").getAsString().replace("0x", "");
                    line.append("$(#").append(hex).append(')').append(CELL).append("$()");
                    colour = true;
                    continue;
                }
                String t = cell.has("text") ? cell.get("text").getAsString().strip() : "";
                boolean bold = cell.has("bold") && cell.get("bold").getAsBoolean();
                if (t.isEmpty()) {
                    continue;
                }
                if (c == 0) {
                    line.append(bold ? GuideRecipes.bold(t) : "$(l)" + t + "$()");
                    line.append(colour || columns > 3 ? " " : ": ");
                } else {
                    if (line.length() > 0 && !line.toString().endsWith(" ")) {
                        line.append(colour ? " " : ", ");
                    }
                    line.append(bold ? GuideRecipes.bold(t) : t);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString().strip());
            }
        }
        if (p.has("legend") && p.get("legend").isJsonArray()) {
            for (JsonElement e : p.getAsJsonArray("legend")) {
                JsonObject l = e.getAsJsonObject();
                String hex = l.get("color").getAsString().replace("0x", "");
                lines.add("$(#" + hex + ")" + CELL + "$() " + l.get("text").getAsString());
            }
        }
        return String.join("$(br)", lines);
    }

    private static String join(String head, String text) {
        if (head == null || head.isBlank()) {
            return text;
        }
        return text == null || text.isBlank() ? head : head + "$(br2)" + text;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String required(JsonObject o, String key, String source) {
        String s = str(o, key);
        if (s == null) {
            throw new DatagenException(source + ": page without '" + key + "'");
        }
        return s;
    }
}
