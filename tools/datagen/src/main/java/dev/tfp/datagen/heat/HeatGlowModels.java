/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.heat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * PAR-HEAT-01: the heat glow of hot items on vanilla clients. For every CraftEngine item that can be heated (an
 * {@code item_heat} definition names it, directly or through an item tag) and whose model is a flat
 * {@code item/generated} or {@code item/handheld} one, this writes
 * <ul>
 *   <li>a glow mask {@code textures/item/heat_glow/<path>.png}: the item's own {@code layer0} texture in white at
 *       {@value #MASK_OPACITY_PERCENT} % of its opacity (a pure function of the TFC texture, no drawn art);</li>
 *   <li>the mask model {@code models/item/heat_glow/<path>.json}: the item's model with the mask as {@code layer0};</li>
 *   <li>the 1.21.4 item model definition {@code items/heat_glow/<path>.json}: a {@code range_dispatch} on
 *       {@code custom_model_data} floats[0] with one entry per glowing heat level (threshold {@link #index}): the
 *       item's model with the mask drawn over it, tinted in that level's {@link #GLOW glow colour}; the plain item
 *       model below the first glowing level;</li>
 *   <li>the runtime list {@value #RUNTIME}: CraftEngine id to the glow definition's id.</li>
 * </ul>
 * The plugin sets the packet copy of a hot stack's {@code item_model} to the glow definition and floats[0] to the level
 * index ({@code dev.tfp.paper.heat.HeatGlow}). The CraftEngine item entries stay as they are, so CraftEngine still
 * generates the plain {@code items/<path>.json} itself. Items whose model is not flat (block items, custom elements) or
 * whose texture is not in our pack are skipped and listed in {@value #REPORT}.
 *
 * <p>Runs after every CraftEngine item generator and {@code CommonTags} (it reads their outputs from the tree).
 */
public final class HeatGlowModels {
    public static final String CONFIG = "craftengine/tfp/configuration/";
    public static final String ASSETS = "craftengine/tfp/resourcepack/assets/";
    public static final String RUNTIME = "visuals/heat_glow.json";
    public static final String REPORT = "reports/heat_glow.json";
    /** The path segment under which the glow definitions, models and masks live. */
    public static final String DIR = "heat_glow/";
    /** The first heat level that glows (TFC's first red level; below it a metal does not visibly glow). */
    public static final HeatLevel FIRST_GLOWING = HeatLevel.FAINT_RED;
    static final int MASK_OPACITY_PERCENT = 60;

    /**
     * The glow colour of each glowing level, an incandescence ramp from dull red to white (RGB). The mask is drawn over
     * the item at {@value #MASK_OPACITY_PERCENT} % in this colour, so the item takes on the colour while its shading
     * stays visible.
     */
    static final Map<HeatLevel, Integer> GLOW = glowColours();

    private static final Pattern ENTRY = Pattern.compile("^  ([a-z0-9_.-]+:[a-z0-9_./-]+):\\s*$");
    private static final Pattern SECTION = Pattern.compile("^([A-Za-z_]+)(#[A-Za-z_]*)?:\\s*$");
    private static final Set<String> FLAT_PARENTS = Set.of("item/generated", "minecraft:item/generated",
        "item/handheld", "minecraft:item/handheld", "item/handheld_rod", "minecraft:item/handheld_rod");

    private HeatGlowModels() {
    }

    private static Map<HeatLevel, Integer> glowColours() {
        Map<HeatLevel, Integer> m = new LinkedHashMap<>();
        m.put(HeatLevel.FAINT_RED, 0x7A1A0E);
        m.put(HeatLevel.DARK_RED, 0xA3200F);
        m.put(HeatLevel.BRIGHT_RED, 0xE8341A);
        m.put(HeatLevel.ORANGE, 0xFF7A1A);
        m.put(HeatLevel.YELLOW, 0xFFC93A);
        m.put(HeatLevel.YELLOW_WHITE, 0xFFE98A);
        m.put(HeatLevel.WHITE, 0xFFF6D0);
        m.put(HeatLevel.BRILLIANT_WHITE, 0xFFFFFF);
        return Map.copyOf(m);
    }

    /** The {@code custom_model_data} floats[0] value of a heat level: its ordinal plus one (0 = no level). */
    public static int index(HeatLevel level) {
        return level.ordinal() + 1;
    }

    /** The glowing levels in order. */
    static List<HeatLevel> glowing() {
        List<HeatLevel> out = new ArrayList<>();
        for (HeatLevel l : HeatLevel.values()) {
            if (l.ordinal() >= FIRST_GLOWING.ordinal()) {
                out.add(l);
            }
        }
        return out;
    }

    /** What the step wrote. */
    public record Result(SortedMap<String, String> glowing, SortedMap<String, String> skipped) {
    }

    public static Result run(OutputTree out) {
        SortedMap<String, byte[]> files = out.files();
        Set<String> heatable = heatable(files);
        Set<String> items = templateModelItems(files);
        SortedMap<String, String> glowing = new TreeMap<>();
        SortedMap<String, String> skipped = new TreeMap<>();
        for (String id : new TreeSet<>(heatable)) {
            if (!items.contains(id)) {
                continue; // not one of our CraftEngine items (vanilla items, TFC items not ported yet)
            }
            String ns = id.substring(0, id.indexOf(':'));
            String path = id.substring(id.indexOf(':') + 1);
            String model = ns + ":item/" + path;
            Flat flat = flat(files, model);
            if (flat.reason() != null) {
                skipped.put(id, flat.reason());
                continue;
            }
            String texFile = textureFile(flat.texture());
            byte[] png = files.get(texFile);
            if (png == null) {
                skipped.put(id, "texture " + flat.texture() + " is not in our pack");
                continue;
            }
            String glowRef = ns + ":item/" + DIR + path;
            out.put(ASSETS + ns + "/textures/item/" + DIR + path + ".png", mask(png, texFile));
            byte[] mcmeta = files.get(texFile + ".mcmeta");
            if (mcmeta != null) {
                out.put(ASSETS + ns + "/textures/item/" + DIR + path + ".png.mcmeta", mcmeta);
            }
            out.put(ASSETS + ns + "/models/item/" + DIR + path + ".json", Json.write(maskModel(model, glowRef)));
            out.put(ASSETS + ns + "/items/" + DIR + path + ".json", Json.write(definition(model, glowRef)));
            glowing.put(id, ns + ":" + DIR + path);
        }
        JsonObject runtime = new JsonObject();
        runtime.addProperty("comment", "Generated by tools/datagen (PAR-HEAT-01). CraftEngine id -> item_model of its heat "
            + "glow definition; custom_model_data floats[0] = heat level ordinal + 1. Do not edit.");
        runtime.addProperty("format", 1);
        runtime.addProperty("first_glowing_index", index(FIRST_GLOWING));
        JsonObject map = new JsonObject();
        glowing.forEach(map::addProperty);
        runtime.add("items", map);
        out.put(RUNTIME, Json.write(runtime));
        JsonObject report = new JsonObject();
        report.addProperty("heatable_ids", heatable.size());
        report.addProperty("glowing", glowing.size());
        JsonObject sk = new JsonObject();
        skipped.forEach(sk::addProperty);
        report.add("skipped", sk);
        out.put(REPORT, Json.write(report));
        return new Result(glowing, skipped);
    }

    /** The item model definition: plain below the first glowing level, base + tinted mask per glowing level. */
    static JsonObject definition(String model, String glowModel) {
        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        JsonArray entries = new JsonArray();
        for (HeatLevel level : glowing()) {
            JsonObject base = plain(model);
            JsonObject glow = plain(glowModel);
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:constant");
            tint.addProperty("value", GLOW.get(level));
            JsonArray tints = new JsonArray();
            tints.add(tint);
            glow.add("tints", tints);
            JsonArray models = new JsonArray();
            models.add(base); // drawn first
            models.add(glow); // drawn over the base
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "minecraft:composite");
            composite.add("models", models);
            JsonObject entry = new JsonObject();
            entry.addProperty("threshold", index(level));
            entry.add("model", composite);
            entries.add(entry);
        }
        dispatch.add("entries", entries);
        dispatch.add("fallback", plain(model));
        JsonObject root = new JsonObject();
        root.add("model", dispatch);
        return root;
    }

    private static JsonObject plain(String model) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", model);
        return m;
    }

    static JsonObject maskModel(String model, String glowTexture) {
        JsonObject m = new JsonObject();
        m.addProperty("parent", model);
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", glowTexture);
        m.add("textures", textures);
        return m;
    }

    /** The item's texture in white, at {@value #MASK_OPACITY_PERCENT} % of its alpha. */
    static byte[] mask(byte[] png, String what) {
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(what, e);
        }
        if (src == null) {
            throw new DatagenException("not a PNG: " + what);
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = src.getRGB(x, y) >>> 24;
                int ma = (a * MASK_OPACITY_PERCENT + 50) / 100;
                out.setRGB(x, y, ma == 0 ? 0 : (ma << 24) | 0xFFFFFF);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(out, "png", bytes)) {
                throw new DatagenException("no PNG writer available");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** A model's flat-item texture, or why it has none. */
    record Flat(String texture, String reason) {
    }

    /** Follows the parents of {@code model} inside our pack to its flat item parent and its {@code layer0}. */
    static Flat flat(Map<String, byte[]> files, String model) {
        String layer0 = null;
        String m = model;
        Set<String> seen = new HashSet<>();
        while (true) {
            if (FLAT_PARENTS.contains(m)) {
                return layer0 == null ? new Flat(null, "no layer0 texture") : new Flat(layer0, null);
            }
            if (!seen.add(m)) {
                return new Flat(null, "parent cycle at " + m);
            }
            byte[] bytes = files.get(modelFile(m));
            if (bytes == null) {
                return new Flat(null, "model " + m + " is not in our pack");
            }
            JsonObject json = Json.parse(new String(bytes, StandardCharsets.UTF_8), modelFile(m)).getAsJsonObject();
            if (json.has("textures") && layer0 == null && json.getAsJsonObject("textures").has("layer0")) {
                layer0 = json.getAsJsonObject("textures").get("layer0").getAsString();
            }
            if (json.has("textures") && json.getAsJsonObject("textures").keySet().stream()
                    .anyMatch(k -> k.startsWith("layer") && !k.equals("layer0"))) {
                return new Flat(null, "more than one layer");
            }
            if (json.has("elements") || json.has("loader")) {
                return new Flat(null, "not a flat item model");
            }
            if (!json.has("parent")) {
                return new Flat(null, "no flat item parent");
            }
            m = json.get("parent").getAsString();
        }
        // layer0 references (#name) do not occur in flat item models; a "#" texture fails the file lookup and is skipped
    }

    static String modelFile(String model) {
        String ns = model.contains(":") ? model.substring(0, model.indexOf(':')) : "minecraft";
        String path = model.substring(model.indexOf(':') + 1);
        return ASSETS + ns + "/models/" + path + ".json";
    }

    static String textureFile(String texture) {
        String ns = texture.contains(":") ? texture.substring(0, texture.indexOf(':')) : "minecraft";
        String path = texture.substring(texture.indexOf(':') + 1);
        return ASSETS + ns + "/textures/" + path + ".png";
    }

    /**
     * The CraftEngine item ids whose entry takes its model from its template ({@code <ns>:item/<path>}, the rule of every
     * {@code tfp:item/*} template): entries of {@code items} sections with a {@code template:} and no own {@code model:}.
     */
    static Set<String> templateModelItems(Map<String, byte[]> files) {
        Set<String> out = new TreeSet<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            String path = f.getKey();
            if (!path.startsWith(CONFIG) || !path.endsWith(".yml") || path.startsWith(CONFIG + "templates/")) {
                continue;
            }
            boolean inItems = false;
            String entry = null;
            boolean template = false;
            boolean ownModel = false;
            for (String line : new String(f.getValue(), StandardCharsets.UTF_8).split("\n", -1)) {
                Matcher section = SECTION.matcher(line);
                Matcher e = ENTRY.matcher(line);
                boolean boundary = section.matches() || e.matches() || (!line.isBlank() && !line.startsWith(" ")
                    && !line.startsWith("#"));
                if (boundary && entry != null) {
                    if (template && !ownModel) {
                        out.add(entry);
                    }
                    entry = null;
                }
                if (section.matches()) {
                    inItems = section.group(1).equals("items");
                } else if (inItems && e.matches()) {
                    entry = e.group(1);
                    template = false;
                    ownModel = false;
                } else if (entry != null) {
                    template |= line.startsWith("    template: ");
                    ownModel |= line.startsWith("    model:");
                }
            }
            if (entry != null && template && !ownModel) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Every item id an {@code item_heat} definition of our data names: {@code item} ingredients and the members of
     * {@code tag} ingredients (our item tags plus NeoForge's {@code c:} members, nested tags expanded), the way the
     * plugin's registries resolve them.
     */
    static Set<String> heatable(Map<String, byte[]> files) {
        Map<String, List<String>> neoforge = new HashMap<>();
        byte[] nf = files.get("data/neoforge_common_tags.json");
        if (nf != null) {
            JsonObject root = Json.parse(new String(nf, StandardCharsets.UTF_8), "neoforge_common_tags").getAsJsonObject();
            if (root.has("item")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("item").entrySet()) {
                    List<String> values = new ArrayList<>();
                    e.getValue().getAsJsonArray().forEach(v -> values.add(entry(v)));
                    neoforge.put(e.getKey(), values);
                }
            }
        }
        Map<String, Set<String>> cache = new HashMap<>();
        Set<String> out = new TreeSet<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            if (!f.getKey().startsWith("data/") || !f.getKey().contains("/item_heat/") || !f.getKey().endsWith(".json")) {
                continue;
            }
            JsonObject def = Json.parse(new String(f.getValue(), StandardCharsets.UTF_8), f.getKey()).getAsJsonObject();
            JsonElement ing = def.get("ingredient");
            List<JsonElement> ings = new ArrayList<>();
            if (ing != null && ing.isJsonArray()) {
                ing.getAsJsonArray().forEach(ings::add);
            } else if (ing != null) {
                ings.add(ing);
            }
            for (JsonElement i : ings) {
                if (!i.isJsonObject()) {
                    continue;
                }
                JsonObject o = i.getAsJsonObject();
                if (o.has("item")) {
                    out.add(o.get("item").getAsString());
                } else if (o.has("tag")) {
                    out.addAll(tag(o.get("tag").getAsString(), files, neoforge, cache));
                }
            }
        }
        return out;
    }

    private static Set<String> tag(String tag, Map<String, byte[]> files, Map<String, List<String>> neoforge,
            Map<String, Set<String>> cache) {
        Set<String> cached = cache.get(tag);
        if (cached != null) {
            return cached;
        }
        cache.put(tag, Set.of()); // a cycle reads as empty
        String ns = tag.substring(0, tag.indexOf(':'));
        String file = "data/" + ns + "/tags/item/" + tag.substring(tag.indexOf(':') + 1) + ".json";
        List<String> values = new ArrayList<>(neoforge.getOrDefault(tag, List.of()));
        byte[] bytes = files.get(file);
        if (bytes != null) {
            JsonObject json = Json.parse(new String(bytes, StandardCharsets.UTF_8), file).getAsJsonObject();
            if (json.has("replace") && json.get("replace").getAsBoolean()) {
                values.clear();
            }
            if (json.has("values")) {
                json.getAsJsonArray("values").forEach(v -> values.add(entry(v)));
            }
        }
        Set<String> out = new TreeSet<>();
        for (String v : values) {
            if (v.startsWith("#")) {
                out.addAll(tag(v.substring(1), files, neoforge, cache));
            } else {
                out.add(v);
            }
        }
        cache.put(tag, Set.copyOf(out));
        return out;
    }

    private static String entry(JsonElement v) {
        return v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
    }
}
