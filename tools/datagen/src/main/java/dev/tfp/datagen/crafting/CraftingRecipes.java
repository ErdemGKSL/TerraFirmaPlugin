/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/crafting @ c3b4d9536 (the plain shaped and shapeless recipes)
 */
package dev.tfp.datagen.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.toolblocktags.ToolBlockTags;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-02: TFC's plain workbench recipes ({@code minecraft:crafting_shaped} / {@code minecraft:crafting_shapeless} under
 * {@code data/tfc/recipe/crafting}) as CraftEngine recipe YAML in {@code craftengine/tfp/configuration/crafting/}.
 *
 * <p>Every plain recipe ends in exactly one bucket, written to {@value #REPORT}:
 * <ul>
 *   <li><b>emitted</b>: a CE {@code shaped}/{@code shapeless} recipe {@code tfp:crafting/<path>};</li>
 *   <li><b>routed</b> to PAR-PROC-03: an ingredient CE cannot express ({@code tfc:fluid_content} buckets of fluid,
 *       {@code tfc:and} with {@code tfc:not_rotten} and any other custom ingredient type);</li>
 *   <li><b>pending</b>: the result or a whole ingredient slot is an item that is not a CE item of this generation yet
 *       (CE fails a recipe naming an unknown item), or the result is a CE item the server cannot build yet
 *       ({@link #unbuildable}). The report names the missing ids and their owners; the recipe is emitted by the
 *       generation that adds or fixes the item.</li>
 * </ul>
 *
 * <p>Ingredients: an item id maps {@code tfc:x} to the CE item {@code tfp:x} (vanilla ids stay); a tag is expanded to its
 * members ({@link ItemTags}) because CE resolves tags only through vanilla tags and CE {@code settings.tags}, and our CE
 * items carry no TFC tags; a vanilla tag stays a {@code #minecraft:} element next to TFC's additions. Members that are not
 * CE items yet are left out of the slot (they cannot be held as a CE item either); the slot is pending only when nothing is
 * left. Must run after every generator that writes CE items, and after {@code CommonTags}.
 */
public final class CraftingRecipes {
    public static final String SOURCE = TfcCheckout.GENERATED + "data/" + Json.FROM_NS + "/recipe/crafting/";
    public static final String DIR = "craftengine/tfp/configuration/crafting/";
    public static final String REPORT = "reports/crafting-recipes.json";
    static final String ROUTE = "PAR-PROC-03";
    static final String SHAPED = "minecraft:crafting_shaped";
    static final String SHAPELESS = "minecraft:crafting_shapeless";
    private static final String CONFIG = "craftengine/tfp/configuration/";
    private static final String COVERAGE = "reports/tfc-item-coverage.json";
    private static final String COMMON_TAGS = "data/neoforge_common_tags.json";
    private static final String FAMILIES = "craftengine/tfp/families/";
    private static final Pattern FAMILY_TFC = Pattern.compile("^ +tfc: (" + Json.FROM_NS + ":[a-z0-9_/]+)\\s*$");
    /** The follow-up that makes our {@code tfp:mineable/*} block tags real server tags. */
    static final String TOOL_TAGS = "PAR-PROC-24";
    private static final Pattern TOOL_TAG = Pattern.compile("^ +mineable_tag: ([a-z0-9_]+:[a-z0-9_/]+)\\s*$");
    private static final Pattern ITEM_KEY = Pattern.compile("^ {2}([a-z0-9_]+:[a-z0-9_/.\\-]+):\\s*$");

    /** Where one plain TFC recipe went. {@code reason} for routed ones; {@code missing} TFC/vanilla ids for pending ones. */
    public record Outcome(String id, Bucket bucket, String reason, Set<String> missing, Set<String> dropped) {
    }

    public enum Bucket { EMITTED, ROUTED, PENDING }

    /** An ingredient CE cannot express. */
    private static final class Unsupported extends Exception {
        Unsupported(String reason) {
            super(reason, null, false, false);
        }
    }

    private CraftingRecipes() {
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.isDirectory(SOURCE);
    }

    /** Converts every plain recipe; writes the YAML files and the report. Returns the outcome per recipe, sorted by id. */
    public static List<Outcome> run(TfcCheckout tfc, OutputTree out) {
        Set<String> ceItems = ceItems(out);
        ItemTags tags = new ItemTags(tfc, commonTags(out));
        Map<String, String> owners = owners(out);
        Map<String, String> unbuildable = unbuildable(out);
        unbuildable.forEach((ce, why) -> owners.put(Json.FROM_NS + ce.substring(Json.TO_NS.length()), "CE item cannot be built: " + why));
        Map<String, Map<String, Object>> files = new TreeMap<>();
        List<Outcome> outcomes = new ArrayList<>();
        for (String file : tfc.list(SOURCE)) {
            if (!file.endsWith(".json")) {
                continue;
            }
            String path = file.substring(SOURCE.length(), file.length() - ".json".length());
            JsonObject json = Json.parse(tfc.readString(file), file).getAsJsonObject();
            String type = json.get("type").getAsString();
            if (!type.equals(SHAPED) && !type.equals(SHAPELESS)) {
                continue; // advanced, casting and food combining recipes: PAR-PROC-03
            }
            String id = Json.TO_NS + ":crafting/" + path;
            Converter c = new Converter(ceItems, unbuildable.keySet(), tags);
            Map<String, Object> recipe;
            try {
                recipe = c.recipe(json, type.equals(SHAPED));
            } catch (Unsupported e) {
                outcomes.add(new Outcome(id, Bucket.ROUTED, e.getMessage(), Set.of(), Set.of()));
                continue;
            }
            if (recipe == null) {
                outcomes.add(new Outcome(id, Bucket.PENDING, null, sorted(c.blocking), sorted(c.dropped)));
                continue;
            }
            files.computeIfAbsent(fileOf(path), k -> new LinkedHashMap<>()).put(id, recipe);
            outcomes.add(new Outcome(id, Bucket.EMITTED, null, Set.of(), sorted(c.dropped)));
        }
        if (outcomes.isEmpty()) {
            throw new DatagenException("TFC has no plain crafting recipes at " + SOURCE);
        }
        String header = "Generated by tools/datagen from TerraFirmaCraft @ " + tfc.head().substring(0, 9)
            + " (PAR-PROC-02). Do not edit: fix the generator and regenerate.";
        files.forEach((name, recipes) -> out.put(DIR + name + ".yml", Yaml.write(header, Map.of("recipes", recipes))));
        out.put(REPORT, Json.write(report(outcomes, owners)));
        return outcomes;
    }

    /** A sorted unmodifiable copy ({@code Set.copyOf} iterates in a per-JVM order: the report changed between runs). */
    private static Set<String> sorted(Set<String> s) {
        return java.util.Collections.unmodifiableSortedSet(new TreeSet<>(s));
    }

    /**
     * The file of a recipe: {@code wood/planks/oak} → {@code wood_recipes}; a recipe at the top of the folder →
     * {@code general_recipes} (the suffix keeps the names apart from the item files, e.g. {@code metal.yml}).
     */
    static String fileOf(String path) {
        int slash = path.indexOf('/');
        return (slash < 0 ? "general" : path.substring(0, slash)) + "_recipes";
    }

    /** One recipe's conversion; collects what is missing. */
    private static final class Converter {
        private final Set<String> ceItems;
        private final Set<String> unbuildable;
        private final ItemTags tags;
        final Set<String> blocking = new TreeSet<>();
        final Set<String> dropped = new TreeSet<>();

        Converter(Set<String> ceItems, Set<String> unbuildable, ItemTags tags) {
            this.ceItems = ceItems;
            this.unbuildable = unbuildable;
            this.tags = tags;
        }

        /** The CE recipe, or null when it is pending. */
        Map<String, Object> recipe(JsonObject json, boolean shaped) throws Unsupported {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", shaped ? "shaped" : "shapeless");
            if (json.has("category")) {
                r.put("category", json.get("category").getAsString());
            }
            if (json.has("group")) {
                r.put("group", json.get("group").getAsString());
            }
            if (shaped) {
                List<Object> pattern = new ArrayList<>();
                json.getAsJsonArray("pattern").forEach(p -> pattern.add(p.getAsString()));
                r.put("pattern", pattern);
                Map<String, Object> key = new TreeMap<>();
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("key").entrySet()) {
                    key.put(e.getKey(), slot(e.getValue()));
                }
                r.put("ingredients", key);
            } else {
                List<Object> ingredients = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray("ingredients")) {
                    ingredients.add(slot(e));
                }
                r.put("ingredients", ingredients);
            }
            JsonObject result = json.getAsJsonObject("result");
            for (String k : result.keySet()) {
                if (!k.equals("id") && !k.equals("count")) {
                    throw new Unsupported("result " + k);
                }
            }
            String resultId = result.get("id").getAsString();
            String ceResult = item(resultId);
            if (ceResult == null || unbuildable.contains(ceResult)) {
                blocking.add(resultId); // CE builds the result at load; an unbuildable one fails every recipe
            }
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("id", ceResult == null ? resultId : ceResult);
            res.put("count", result.has("count") ? result.get("count").getAsInt() : 1);
            r.put("result", res);
            return blocking.isEmpty() ? r : null;
        }

        /** One slot: a single string or a list of alternatives. Records a blocking id when nothing is left. */
        private Object slot(JsonElement ingredient) throws Unsupported {
            Set<String> elements = new TreeSet<>();
            Set<String> missing = new TreeSet<>();
            collect(ingredient, elements, missing);
            if (elements.isEmpty()) {
                blocking.addAll(missing.isEmpty() ? Set.of(ingredient.toString()) : missing);
                return List.of();
            }
            dropped.addAll(missing);
            return elements.size() == 1 ? elements.iterator().next() : List.copyOf(elements);
        }

        private void collect(JsonElement ingredient, Set<String> elements, Set<String> missing) throws Unsupported {
            if (ingredient.isJsonArray()) {
                for (JsonElement e : ingredient.getAsJsonArray()) {
                    collect(e, elements, missing);
                }
                return;
            }
            JsonObject o = ingredient.getAsJsonObject();
            if (o.has("type")) {
                throw new Unsupported("ingredient " + o.get("type").getAsString());
            }
            if (o.has("item")) {
                String id = o.get("item").getAsString();
                String ce = item(id);
                (ce == null ? missing : elements).add(ce == null ? id : ce);
            } else if (o.has("tag")) {
                String tag = o.get("tag").getAsString();
                ItemTags.Members m = tags.resolve(tag);
                if (m.isEmpty()) {
                    missing.add("#" + tag);
                }
                elements.addAll(m.vanillaTags());
                for (String id : m.items()) {
                    String ce = item(id);
                    (ce == null ? missing : elements).add(ce == null ? id : ce);
                }
            } else {
                throw new Unsupported("ingredient " + o);
            }
        }

        /** The CE/vanilla id of a TFC or vanilla item, or null when it is not an item CE knows. */
        private String item(String id) {
            if (id.startsWith(Json.FROM_NS + ":")) {
                String ce = Json.TO_NS + id.substring(Json.FROM_NS.length());
                return ceItems.contains(ce) ? ce : null;
            }
            return id.startsWith("minecraft:") && !id.equals("minecraft:air") ? id : null;
        }
    }

    /** The CE item ids the tree defines: the keys of every top-level {@code items:} section of the CE configuration. */
    static Set<String> ceItems(OutputTree out) {
        return scan(out).keySet();
    }

    /**
     * CE item ids whose stack the server cannot build (id → reason): a {@code mineable_tag} argument (the item's vanilla
     * {@code tool} component rule) naming a block tag that is neither vanilla nor shipped in the bootstrap datapack
     * ({@link ToolBlockTags#isServerTag}, {@value #TOOL_TAGS}): CE's build of the {@code tool} component would throw, and a
     * recipe with such a result makes CE fail its whole recipe load ("Cannot parse component minecraft:tool").
     */
    public static Map<String, String> unbuildable(OutputTree out) {
        Map<String, String> bad = new TreeMap<>();
        scan(out).forEach((id, tag) -> {
            if (!tag.isEmpty()) {
                bad.put(id, "its tool component names block tag " + tag + ", which the server lacks (" + TOOL_TAGS + ")");
            }
        });
        return bad;
    }

    /** CE item id → its {@code mineable_tag} that is no server tag ({@code ""} when none). */
    private static Map<String, String> scan(OutputTree out) {
        Map<String, String> ids = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : out.files().entrySet()) {
            String path = f.getKey();
            if (!path.startsWith(CONFIG) || !path.endsWith(".yml") || path.startsWith(DIR)) {
                continue;
            }
            boolean inItems = false;
            String current = null;
            for (String line : new String(f.getValue(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!line.startsWith(" ")) {
                    inItems = line.equals("items:");
                    current = null;
                    continue;
                }
                Matcher m = ITEM_KEY.matcher(line);
                if (inItems && m.matches()) {
                    current = m.group(1);
                    ids.putIfAbsent(current, "");
                    continue;
                }
                Matcher t = TOOL_TAG.matcher(line);
                if (inItems && current != null && t.matches() && !ToolBlockTags.isServerTag(out, t.group(1))) {
                    ids.put(current, t.group(1));
                }
            }
        }
        return ids;
    }

    private static Map<String, JsonArray> commonTags(OutputTree out) {
        Map<String, JsonArray> tags = new TreeMap<>();
        byte[] b = out.get(COMMON_TAGS);
        if (b != null) {
            JsonObject items = Json.parse(new String(b, StandardCharsets.UTF_8), COMMON_TAGS).getAsJsonObject()
                .getAsJsonObject("item");
            items.entrySet().forEach(e -> tags.put(e.getKey(), e.getValue().getAsJsonArray()));
        }
        return tags;
    }

    /**
     * TFC item id → who will make it a CE item: the owning parity item from the PAR-PROC-19 coverage report (when
     * generated), or the family file of a family block that is not a CE block yet.
     */
    private static Map<String, String> owners(OutputTree out) {
        Map<String, String> owners = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : out.files().entrySet()) {
            if (f.getKey().startsWith(FAMILIES) && f.getKey().endsWith(".yml")) {
                String family = "family block of " + f.getKey().substring("craftengine/tfp/".length());
                for (String line : new String(f.getValue(), StandardCharsets.UTF_8).split("\n")) {
                    Matcher m = FAMILY_TFC.matcher(line);
                    if (m.matches()) {
                        owners.putIfAbsent(m.group(1), family);
                    }
                }
            }
        }
        byte[] b = out.get(COVERAGE);
        if (b != null) {
            JsonObject items = Json.parse(new String(b, StandardCharsets.UTF_8), COVERAGE).getAsJsonObject()
                .getAsJsonObject("items");
            items.entrySet().forEach(e -> {
                if (!e.getValue().getAsString().startsWith("ce:")) {
                    owners.put(e.getKey(), e.getValue().getAsString());
                }
            });
        }
        return owners;
    }

    private static JsonObject report(List<Outcome> outcomes, Map<String, String> owners) {
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Generated by tools/datagen (PAR-PROC-02). Every plain TFC crafting recipe: emitted as CE "
            + "YAML, routed to " + ROUTE + " (an ingredient CE cannot express), or pending (an item that is not a CE item "
            + "yet; emitted once it is). Do not edit.");
        long emitted = outcomes.stream().filter(o -> o.bucket() == Bucket.EMITTED).count();
        long routed = outcomes.stream().filter(o -> o.bucket() == Bucket.ROUTED).count();
        long pending = outcomes.stream().filter(o -> o.bucket() == Bucket.PENDING).count();
        root.addProperty("total", outcomes.size());
        root.addProperty("emitted", emitted);
        root.addProperty("routed", routed);
        root.addProperty("pending", pending);
        Map<String, Integer> reasons = new TreeMap<>();
        Map<String, Integer> waiting = new TreeMap<>();
        JsonObject routedRows = new JsonObject();
        JsonObject pendingRows = new JsonObject();
        JsonObject partialRows = new JsonObject();
        for (Outcome o : outcomes) {
            switch (o.bucket()) {
                case ROUTED -> {
                    reasons.merge(o.reason(), 1, Integer::sum);
                    routedRows.addProperty(o.id(), ROUTE + ": " + o.reason());
                }
                case PENDING -> {
                    JsonArray missing = new JsonArray();
                    Set<String> by = new TreeSet<>();
                    for (String m : o.missing()) {
                        String owner = m.startsWith("#") || m.startsWith("{") ? "tag with no CE item yet"
                            : owners.getOrDefault(m, m.startsWith(Json.FROM_NS + ":") ? "block item with no CE item yet" : "not an item");
                        missing.add(m + " (" + owner + ")");
                        by.add(owner);
                    }
                    by.forEach(w -> waiting.merge(w, 1, Integer::sum));
                    pendingRows.add(o.id(), missing);
                }
                case EMITTED -> {
                    if (!o.dropped().isEmpty()) {
                        JsonArray dropped = new JsonArray();
                        o.dropped().forEach(dropped::add);
                        partialRows.add(o.id(), dropped);
                    }
                }
            }
        }
        JsonObject r = new JsonObject();
        reasons.forEach(r::addProperty);
        root.add("routed_by_reason", r);
        JsonObject w = new JsonObject();
        waiting.forEach(w::addProperty);
        root.add("pending_by_owner", w);
        root.add("routed_recipes", routedRows);
        root.add("pending_recipes", pendingRows);
        root.addProperty("comment_partial", "Emitted recipes with ingredient alternatives left out because they are not CE "
            + "items yet (the slot accepts the rest).");
        root.add("partial_recipes", partialRows);
        return root;
    }
}
