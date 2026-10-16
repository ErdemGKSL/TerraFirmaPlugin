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
import dev.tfp.core.tag.TagResolver;
import dev.tfp.core.tag.TagType;
import java.util.HashSet;
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
     * The table defined by datagen's {@code id_map.json}:
     * {@code {"comment": ..., "namespaces": {"tfc": "tfp"}, "blocks": {tfc id: our id}, "items": {...}}}.
     *
     * <p>Checked strictly: every item/block id in one of our namespaces (the map's target
     * namespaces) must be a value of {@code items}/{@code blocks}. Not checked yet, because the map
     * does not list them: vanilla ids ({@code minecraft:}...), and fluids, entity types and food traits
     * in any namespace. Tags in {@code minecraft:} that our data doesn't define count as vanilla
     * (external) tags. Every other tag must be defined in the data.
     */
    static KnownIds fromIdMap(JsonElement json, DecodeContext ctx) {
        ObjectReader r = ObjectReader.of(json, ctx);
        r.optional("comment", Codecs.STRING);
        Map<String, String> namespaces = r.required("namespaces", stringMap(Codecs.STRING, false));
        Map<String, String> blocks = r.required("blocks", stringMap(Codecs.ID, true));
        Map<String, String> items = r.required("items", stringMap(Codecs.ID, true));
        r.finish();
        Set<String> ours = Set.copyOf(namespaces.values());
        Set<String> blockIds = Set.copyOf(blocks.values());
        Set<String> itemIds = Set.copyOf(items.values());
        return new KnownIds() {
            @Override
            public boolean hasId(String registry, String id) {
                if (!ours.contains(Ids.namespace(id))) {
                    return true;
                }
                return switch (registry) {
                    case "item" -> itemIds.contains(id);
                    case "block" -> blockIds.contains(id);
                    default -> true;
                };
            }

            @Override
            public boolean hasExternalTag(String registry, String tag) {
                return Ids.namespace(tag).equals("minecraft");
            }
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
