/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A custom block state named by our own vocabulary: a namespaced block id plus property values, e.g.
 * {@code tfp:rock/raw/granite} or {@code tfp:crop/wheat[age=3]}. Feature code uses this instead of
 * CraftEngine state objects; the CraftEngine adapter resolves it. Immutable; properties are sorted so
 * equal states are equal refs.
 */
public record BlockStateRef(String id, SortedMap<String, String> properties) {
    public BlockStateRef {
        Objects.requireNonNull(id, "id");
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("block id must be namespace:path, got '" + id + "'");
        }
        properties = Collections.unmodifiableSortedMap(new TreeMap<>(properties));
    }

    /** The block's default state. */
    public static BlockStateRef of(String id) {
        return new BlockStateRef(id, new TreeMap<>());
    }

    public static BlockStateRef of(String id, Map<String, String> properties) {
        return new BlockStateRef(id, new TreeMap<>(properties));
    }

    /** Parses {@code ns:path} or {@code ns:path[k=v,k2=v2]} (the form {@link #toString()} writes). */
    public static BlockStateRef parse(String text) {
        int open = text.indexOf('[');
        if (open < 0) {
            return of(text.trim());
        }
        if (!text.endsWith("]")) {
            throw new IllegalArgumentException("unterminated properties in '" + text + "'");
        }
        TreeMap<String, String> props = new TreeMap<>();
        String body = text.substring(open + 1, text.length() - 1).trim();
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0 || eq == pair.length() - 1) {
                    throw new IllegalArgumentException("bad property '" + pair + "' in '" + text + "'");
                }
                String key = pair.substring(0, eq).trim();
                if (props.put(key, pair.substring(eq + 1).trim()) != null) {
                    throw new IllegalArgumentException("duplicate property '" + key + "' in '" + text + "'");
                }
            }
        }
        return new BlockStateRef(text.substring(0, open).trim(), props);
    }

    /** A copy with one property set. */
    public BlockStateRef with(String property, String value) {
        TreeMap<String, String> props = new TreeMap<>(properties);
        props.put(property, value);
        return new BlockStateRef(id, props);
    }

    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return id;
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : properties.entrySet()) {
            sb.append(first ? "" : ",").append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
