/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.registry.KnownIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * M1-DGN-02: TFC data JSON → {@code data/**} in TFC's own schemas, with ids renamed {@code tfc:} → {@code tfp:}.
 *
 * <p>Sources: {@code src/generated/resources/data/tfc/{tfc,recipe,tags}/**} (TFC's Java datagen), plus every
 * other namespace's {@code tags/**} in that tree ({@code c:}, {@code minecraft:} tags are referenced by TFC
 * ingredients), plus the hand-written tags under {@code src/main/resources/data/<ns>/tags} (worldgen tags only
 * at the pinned commit). Output layout mirrors a datapack: {@code data/<ns>/<kind>/...}, where the {@code tfc}
 * namespace and TFC's {@code tfc/} registry folder both become {@code tfp}.
 *
 * <p>Also writes {@code data/id_map.json}: every TFC block id (from blockstates), item id (from item models),
 * fluid, entity type and food trait (from TFC's Java registrations) with its id here, plus the vanilla/NeoForge ids
 * the data references; and {@code data/external_tags.json}: the {@code c:}/{@code minecraft:} tags the data
 * references but TFC does not define (NeoForge common tags, vanilla tags). Runtime code resolves ids and undefined
 * tags only through these tables.
 */
public final class DataExport {
    /** Kinds exported from data/tfc of the generated tree. */
    static final List<String> TFC_KINDS = List.of("tfc", "recipe", "tags");
    /** The ore vein configured features, below {@code data/<ns>/}. */
    static final String VEIN_DIR = "worldgen/configured_feature/vein";
    /** The tree and forest entry configured features (M2-GEN-12), below {@code data/<ns>/}. */
    static final String TREE_DIR = "worldgen/configured_feature/tree";

    private DataExport() {
    }

    public static Map<String, Integer> run(TfcCheckout tfc, OutputTree out) {
        Map<String, Integer> counts = new TreeMap<>();
        String generated = TfcCheckout.GENERATED + "data/";
        if (!tfc.isDirectory(generated + "tfc")) {
            throw new DatagenException("TFC generated data is missing (" + generated + "tfc). Run TFC's data "
                + "generation in the checkout first; datagen never invents data.");
        }
        for (String ns : tfc.childDirs(generated)) {
            List<String> kinds = ns.equals(Json.FROM_NS) ? TFC_KINDS : List.of("tags");
            for (String kind : kinds) {
                exportTree(tfc, out, generated + ns + "/" + kind + "/", ns, kind, counts);
            }
        }
        String main = TfcCheckout.MAIN + "data/";
        for (String ns : tfc.childDirs(main)) {
            exportTree(tfc, out, main + ns + "/tags/", ns, "tags", counts);
        }
        // M2-GEN-10: the ore vein features (hand-written by TFC's mcresources, so under src/main). The runtime reads
        // them through the vein tag order above; the DataLoader leaves worldgen/ to the datapack.
        exportTree(tfc, out, main + Json.FROM_NS + "/" + VEIN_DIR + "/", Json.FROM_NS, VEIN_DIR, counts);
        // M2-GEN-12: the tree features, forest entries and tree structure templates (also hand-written, under src/main).
        exportTree(tfc, out, main + Json.FROM_NS + "/" + TREE_DIR + "/", Json.FROM_NS, TREE_DIR, counts);
        final int templates = TreeStructures.run(tfc, out);
        if (templates > 0) {
            counts.put(Json.TO_NS + "/worldgen/tree_structure", templates);
        }
        DataReferences refs = DataReferences.collect(out);
        out.put("data/" + KnownIds.ID_MAP_FILE, Json.write(idMap(tfc, refs)));
        out.put("data/" + KnownIds.EXTERNAL_TAGS_FILE, Json.write(externalTags(refs)));
        return counts;
    }

    private static void exportTree(TfcCheckout tfc, OutputTree out, String dir, String ns, String kind,
                                   Map<String, Integer> counts) {
        for (String file : tfc.list(dir)) {
            if (!file.endsWith(".json")) {
                throw new DatagenException("unexpected non-JSON data file " + file);
            }
            String rest = file.substring(dir.length());
            String target = "data/" + rename(ns) + "/" + rename(kind) + "/" + rest;
            JsonElement json = Json.rename(Json.parse(tfc.readString(file), file));
            out.put(target, Json.write(json));
            counts.merge(rename(ns) + "/" + rename(kind), 1, Integer::sum);
        }
    }

    private static String rename(String segment) {
        return segment.equals(Json.FROM_NS) ? Json.TO_NS : segment;
    }

    /**
     * Namespaces whose tags may be referenced without being defined in our data, with where they come from.
     * TFC's data references them; the platform that defines them (NeoForge, vanilla) is not in the checkout.
     */
    static final Map<String, String> EXTERNAL_TAG_NAMESPACES = new TreeMap<>(Map.of(
        "c", "NeoForge common tags. Members come from NeoForge and other mods, not from the TFC checkout, so datagen "
            + "does not invent them; TFC's own additions to c: tags are exported under data/c/tags.",
        "minecraft", "Vanilla tags (TFC's additions to them are exported under data/minecraft/tags)."));

    /**
     * Block ids from blockstates, item ids from item models, fluids/entity types/food traits from TFC's Java
     * registrations ({@link TfcRegistries}); plus the ids outside our namespace that the data references
     * ({@code external_ids}: vanilla and NeoForge ids, which the checkout cannot verify). All sorted.
     */
    static JsonObject idMap(TfcCheckout tfc, DataReferences refs) {
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Generated by tools/datagen. TFC id -> our id. Blocks from TFC blockstates, "
            + "items from TFC item models, fluids/entity_types/food_traits from TFC's Java registrations. "
            + "external_ids: ids outside our namespace referenced by the data (vanilla/NeoForge; not verifiable "
            + "from the TFC checkout). Do not edit.");
        JsonObject ns = new JsonObject();
        ns.addProperty(Json.FROM_NS, Json.TO_NS);
        root.add("namespaces", ns);
        root.add("blocks", ids(tfc, TfcCheckout.ASSETS + "blockstates/"));
        root.add("items", ids(tfc, TfcCheckout.ASSETS + "models/item/"));
        root.add("fluids", mapping(TfcRegistries.fluids(tfc)));
        root.add("entity_types", mapping(TfcRegistries.entityTypes(tfc)));
        root.add("food_traits", mapping(TfcRegistries.foodTraits(tfc)));
        JsonObject external = new JsonObject();
        for (String registry : List.of("block", "entity_type", "fluid", "food_trait", "item")) {
            SortedSet<String> found = refs.externalIds(registry, Json.TO_NS);
            if (!found.isEmpty()) {
                external.add(registry, array(found));
            }
        }
        root.add("external_ids", external);
        return root;
    }

    /**
     * {@code data/external_tags.json}: every tag the data references but does not define, in a namespace of
     * {@link #EXTERNAL_TAG_NAMESPACES}. The loader accepts exactly these; an undefined tag anywhere else (e.g. a
     * missing {@code tfp:} tag) is left out and therefore fails ContentDataLoadTest.
     */
    static JsonObject externalTags(DataReferences refs) {
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Generated by tools/datagen from the tags TFC's data references but does not "
            + "define. The loader accepts exactly these undefined tags. Do not edit.");
        JsonObject ns = new JsonObject();
        EXTERNAL_TAG_NAMESPACES.forEach(ns::addProperty);
        root.add("namespaces", ns);
        JsonObject tags = new JsonObject();
        refs.tags().forEach((registry, found) -> {
            List<String> kept = found.stream()
                .filter(t -> EXTERNAL_TAG_NAMESPACES.containsKey(t.substring(0, t.indexOf(':')))).toList();
            if (!kept.isEmpty()) {
                tags.add(registry, array(kept));
            }
        });
        root.add("tags", tags);
        return root;
    }

    private static JsonObject mapping(Collection<String> paths) {
        JsonObject o = new JsonObject();
        for (String p : new java.util.TreeSet<>(paths)) {
            o.addProperty(Json.FROM_NS + ":" + p, Json.TO_NS + ":" + p);
        }
        return o;
    }

    private static JsonArray array(Collection<String> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }

    private static JsonObject ids(TfcCheckout tfc, String dir) {
        List<String> paths = new ArrayList<>();
        for (String f : tfc.list(dir)) {
            if (f.endsWith(".json")) {
                tfc.read(f); // the set of ids is an input too
                paths.add(f.substring(dir.length(), f.length() - ".json".length()));
            }
        }
        paths.sort(null);
        JsonObject o = new JsonObject();
        for (String p : paths) {
            o.addProperty(Json.FROM_NS + ":" + p, Json.TO_NS + ":" + p);
        }
        return o;
    }
}
