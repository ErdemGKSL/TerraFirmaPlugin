/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The vanilla blocks one visual allocation repurposes and that must therefore never be created in a TFC
 * world (M1-DGN-05 {@code suppressed_blocks.json}), plus the look-alike each claimed state is shown as
 * ({@code visual_mappings.yml}). Immutable and thread-safe.
 *
 * @param blocks   suppressed block id → the ways it can appear ({@code creation_paths})
 * @param mappings normalised vanilla state → the state it is shown as
 */
public record SuppressionSet(Map<String, Set<String>> blocks, Map<String, String> mappings) {
    public static final SuppressionSet EMPTY = new SuppressionSet(Map.of(), Map.of());

    public SuppressionSet {
        Map<String, Set<String>> b = new TreeMap<>();
        blocks.forEach((k, v) -> b.put(k, Set.copyOf(v)));
        blocks = java.util.Collections.unmodifiableMap(b);
        Map<String, String> m = new LinkedHashMap<>();
        mappings.forEach((k, v) -> m.put(normalize(k), normalize(v)));
        mappings = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * @param suppressedJson {@code suppressed_blocks.json}
     * @param mappingsYaml   {@code visual_mappings.yml} (null: no replacements known)
     * @throws IllegalArgumentException if the JSON is malformed
     */
    public static SuppressionSet parse(String suppressedJson, String mappingsYaml) {
        Map<String, Set<String>> blocks = new TreeMap<>();
        try {
            JsonObject root = JsonParser.parseString(suppressedJson).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("suppress")) {
                JsonObject o = el.getAsJsonObject();
                Set<String> paths = new TreeSet<>();
                if (o.has("creation_paths")) {
                    for (JsonElement p : o.getAsJsonArray("creation_paths")) {
                        paths.add(p.getAsString());
                    }
                }
                blocks.put(o.get("block").getAsString(), paths);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed suppressed_blocks.json: " + e, e);
        }
        return new SuppressionSet(blocks, mappingsYaml == null ? Map.of() : parseMappings(mappingsYaml));
    }

    /**
     * The {@code block_state_mappings} of our generated file: one {@code key: value} per indented line,
     * keys optionally single-quoted. Comments and other sections are skipped.
     */
    static Map<String, String> parseMappings(String yaml) {
        Map<String, String> out = new LinkedHashMap<>();
        boolean inSection = false;
        for (String raw : yaml.split("\n")) {
            String line = raw.stripTrailing();
            if (line.isBlank() || line.strip().startsWith("#")) {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                inSection = line.startsWith("block_state_mappings:");
                continue;
            }
            if (!inSection) {
                continue;
            }
            String s = line.strip();
            int sep = s.startsWith("'") ? s.indexOf("':") + 1 : s.indexOf(": ");
            if (sep <= 0 || sep + 1 >= s.length()) {
                continue;
            }
            out.put(unquote(s.substring(0, sep)), unquote(s.substring(sep + 1).strip()));
        }
        return out;
    }

    private static String unquote(String s) {
        String t = s.strip();
        if (t.length() >= 2 && (t.startsWith("'") && t.endsWith("'") || t.startsWith("\"") && t.endsWith("\""))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /** {@code minecraft:basalt[axis=y]} → {@code minecraft:basalt}; a missing namespace becomes {@code minecraft:}. */
    public static String blockId(String state) {
        String s = state.strip();
        int cut = s.length();
        for (char c : new char[] {'[', '{'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        String id = s.substring(0, cut);
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** Namespaced, properties sorted by name: {@code basalt[axis=y]} → {@code minecraft:basalt[axis=y]}. */
    public static String normalize(String state) {
        String s = state.strip();
        int open = s.indexOf('[');
        String id = blockId(s);
        if (open < 0 || !s.endsWith("]")) {
            return id;
        }
        TreeMap<String, String> props = new TreeMap<>();
        for (String kv : s.substring(open + 1, s.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                props.put(kv.substring(0, eq).strip(), kv.substring(eq + 1).strip());
            }
        }
        if (props.isEmpty()) {
            return id;
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        props.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }

    /** True if {@code stateOrBlock}'s block is repurposed in this allocation. */
    public boolean suppressed(String stateOrBlock) {
        return blocks.containsKey(blockId(stateOrBlock));
    }

    /** Creation paths datagen listed for the block (empty if not suppressed). */
    public Set<String> creationPaths(String stateOrBlock) {
        return blocks.getOrDefault(blockId(stateOrBlock), Set.of());
    }

    /**
     * The look-alike a real instance of {@code state} is shown as: the mapping of that exact state, else of
     * any state of the same block (datagen maps every state of a block to the same family look-alike).
     */
    public Optional<String> lookAlike(String state) {
        String n = normalize(state);
        String exact = mappings.get(n);
        if (exact != null) {
            return Optional.of(exact);
        }
        String id = blockId(n);
        for (Map.Entry<String, String> e : mappings.entrySet()) {
            if (blockId(e.getKey()).equals(id)) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    /** Suppressed block ids, sorted. */
    public List<String> blockIds() {
        return List.copyOf(blocks.keySet());
    }
}
