/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import com.google.gson.JsonElement;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.Ids;
import dev.tfp.core.json.ObjectReader;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.tag.TagResolver;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ids that exist outside our data files (items, blocks, fluids, entity types, food traits)
 * and the tags that exist outside our data (vanilla tags). The loader resolves every reference
 * against this table plus what it loaded.
 */
public interface KnownIds extends TagResolver.IdTable {
    /** Datagen's id map, written at the top of the data root (M1-DGN-02, {@code tools/datagen DataExport}). */
    String ID_MAP_FILE = "id_map.json";

    /** Datagen's list of tags referenced but defined outside our data (NeoForge {@code c:}, vanilla), next to the id map. */
    String EXTERNAL_TAGS_FILE = "external_tags.json";

    /** Whether {@code id} exists in {@code registry} (a {@link TagType} directory, or e.g. {@code food_trait}). */
    boolean hasId(String registry, String id);

    /** Whether {@code #tag} of {@code registry} exists outside our data. */
    boolean hasExternalTag(String registry, String tag);

    @Override
    default boolean hasId(TagType type, String id) {
        return hasId(type.directory(), id);
    }

    @Override
    default boolean hasExternalTag(TagType type, String tag) {
        return hasExternalTag(type.directory(), tag);
    }

    /** An explicit table: exactly these ids and external tags exist. */
    static KnownIds of(Map<String, Set<String>> ids, Map<String, Set<String>> externalTags) {
        Map<String, Set<String>> i = copy(ids);
        Map<String, Set<String>> t = copy(externalTags);
        return new KnownIds() {
            @Override
            public boolean hasId(String registry, String id) {
                return i.getOrDefault(registry, Set.of()).contains(id);
            }

            @Override
            public boolean hasExternalTag(String registry, String tag) {
                return t.getOrDefault(registry, Set.of()).contains(tag);
            }
        };
    }

    /**
     * Accepts every id, and treats tags in {@code externalTagNamespaces} as external. Only for
     * checking upstream TFC data without an id map; never for the shipped content.
     */
    static KnownIds permissive(Set<String> externalTagNamespaces) {
        Set<String> ns = Set.copyOf(externalTagNamespaces);
        return new KnownIds() {
            @Override
            public boolean hasId(String registry, String id) {
                return true;
            }

            @Override
            public boolean hasExternalTag(String registry, String tag) {
                return ns.contains(Ids.namespace(tag));
            }
        };
    }

    /**
     * Reads {@link #ID_MAP_FILE} and {@link #EXTERNAL_TAGS_FILE} from the top of a generated data root
     * (see {@link #fromIdMap}). Blocking file IO: call it off the server threads.
     */
    static KnownIds fromDataRoot(Path dataRoot) throws IOException {
        Path idMap = dataRoot.resolve(ID_MAP_FILE);
        Path tags = dataRoot.resolve(EXTERNAL_TAGS_FILE);
        try (Reader a = Files.newBufferedReader(idMap, StandardCharsets.UTF_8);
             Reader b = Files.newBufferedReader(tags, StandardCharsets.UTF_8)) {
            return fromIdMap(StrictJson.parse(a, idMap.toString()), DecodeContext.root(idMap.toString()),
                    StrictJson.parse(b, tags.toString()), DecodeContext.root(tags.toString()));
        }
    }

    /**
     * The table defined by datagen's two root files.
     *
     * <p>{@code id_map.json}: {@code {"comment", "namespaces": {"tfc": "tfp"}, "blocks"|"items"|"fluids"|
     * "entity_types"|"food_traits": {tfc id: our id}, "external_ids": {registry: [id, ...]}}}. An id in one of
     * our namespaces (the map's target namespaces) exists iff it is a value of the matching map; any other id
     * (vanilla {@code minecraft:}, NeoForge-provided like {@code minecraft:milk}) exists iff it is listed in
     * {@code external_ids} for that registry. Registries not covered here are unknown (false).
     *
     * <p>{@code external_tags.json}: {@code {"comment", "namespaces": {ns: where they come from},
     * "tags": {registry: [tag, ...]}}}. A tag our data doesn't define exists iff it is listed there; listed tags
     * must be in a declared external namespace, never one of ours. So NeoForge {@code c:} common tags and vanilla
     * tags are accepted exactly as datagen found them referenced, and nothing else.
     */
    static KnownIds fromIdMap(JsonElement idMap, DecodeContext idCtx, JsonElement externalTags, DecodeContext tagCtx) {
        ObjectReader r = ObjectReader.of(idMap, idCtx);
        r.optional("comment", Codecs.STRING);
        Map<String, String> namespaces = r.required("namespaces", stringMap(Codecs.STRING, false));
        Set<String> ours = Set.copyOf(namespaces.values());
        Map<String, Set<String>> own = new HashMap<>();
        for (Map.Entry<String, String> e : ID_MAP_SECTIONS.entrySet()) {
            own.put(e.getValue(), Set.copyOf(r.required(e.getKey(), stringMap(Codecs.ID, true)).values()));
        }
        Map<String, List<String>> externalIds = r.required("external_ids", listMap());
        r.finish();
        own.forEach((registry, values) -> values.forEach(id -> {
            if (!ours.contains(Ids.namespace(id))) {
                throw idCtx.error(registry + " '" + id + "' is not in one of our namespaces " + ours);
            }
        }));
        externalIds.forEach((registry, values) -> values.forEach(id -> {
            if (ours.contains(Ids.namespace(id))) {
                throw idCtx.field("external_ids").field(registry).error("'" + id + "' is in one of our namespaces");
            }
        }));

        ObjectReader t = ObjectReader.of(externalTags, tagCtx);
        t.optional("comment", Codecs.STRING);
        Map<String, String> tagNamespaces = t.required("namespaces", stringMap(Codecs.STRING, false));
        Map<String, List<String>> tags = t.required("tags", listMap());
        t.finish();
        tags.forEach((registry, values) -> values.forEach(tag -> {
            String ns = Ids.namespace(tag);
            if (!tagNamespaces.containsKey(ns) || ours.contains(ns)) {
                throw tagCtx.field("tags").field(registry).error("'#" + tag + "' is not in a declared external namespace "
                        + tagNamespaces.keySet());
            }
        }));
        return of(merge(own, externalIds), tags.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue()))));
    }

    /** id_map section → registry name used in references. */
    Map<String, String> ID_MAP_SECTIONS = Map.of(
            "blocks", "block", "items", "item", "fluids", "fluid", "entity_types", "entity_type", "food_traits", "food_trait");

    private static Map<String, Set<String>> merge(Map<String, Set<String>> own, Map<String, List<String>> external) {
        Map<String, Set<String>> out = new HashMap<>();
        own.forEach((k, v) -> out.computeIfAbsent(k, x -> new HashSet<>()).addAll(v));
        external.forEach((k, v) -> out.computeIfAbsent(k, x -> new HashSet<>()).addAll(v));
        return out;
    }

    /** {@code {registry: [id, ...]}}; ids unique per registry. */
    private static Codec<Map<String, List<String>>> listMap() {
        return (json, ctx) -> {
            if (!json.isJsonObject()) {
                throw ctx.error("expected an object, got " + Codecs.describe(json));
            }
            Map<String, List<String>> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                List<String> values = Codecs.ID.listOf().decode(e.getValue(), ctx.field(e.getKey()));
                if (new HashSet<>(values).size() != values.size()) {
                    throw ctx.field(e.getKey()).error("duplicate entries");
                }
                out.put(e.getKey(), values);
            }
            return out;
        };
    }

    private static Codec<Map<String, String>> stringMap(Codec<String> values, boolean unique) {
        return (json, ctx) -> {
            if (!json.isJsonObject()) {
                throw ctx.error("expected an object, got " + Codecs.describe(json));
            }
            Map<String, String> out = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                String value = values.decode(e.getValue(), ctx.field(e.getKey()));
                if (!seen.add(value) && unique) {
                    throw ctx.field(e.getKey()).error("two entries map to '" + value + "'");
                }
                out.put(e.getKey(), value);
            }
            return out;
        };
    }

    private static Map<String, Set<String>> copy(Map<String, Set<String>> m) {
        return m.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    }
}
