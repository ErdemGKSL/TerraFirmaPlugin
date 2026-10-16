/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tag;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, fully resolved tags: for each {@link TagType}, tag id → the flattened set of member
 * ids (nested tags expanded, {@code remove} applied, insertion order kept). Built by
 * {@link TagResolver}; shared across threads as part of a registry snapshot.
 *
 * <p>Tags marked external (vanilla tags our data refers to but does not define, listed in the
 * datagen id table) exist but have no members here.
 */
public final class TagRegistry implements TagLookup {
    public static final TagRegistry EMPTY = new TagRegistry(Map.of(), Map.of());

    private final Map<TagType, Map<String, Set<String>>> tags;
    private final Map<TagType, Set<String>> external;

    TagRegistry(Map<TagType, Map<String, Set<String>>> tags, Map<TagType, Set<String>> external) {
        Map<TagType, Map<String, Set<String>>> copy = new EnumMap<>(TagType.class);
        tags.forEach((type, byTag) -> {
            Map<String, Set<String>> inner = new LinkedHashMap<>();
            byTag.forEach((tag, ids) -> inner.put(tag, Collections.unmodifiableSet(new LinkedHashSet<>(ids))));
            copy.put(type, Collections.unmodifiableMap(inner));
        });
        Map<TagType, Set<String>> ext = new EnumMap<>(TagType.class);
        external.forEach((type, set) -> ext.put(type, Set.copyOf(set)));
        this.tags = Collections.unmodifiableMap(copy);
        this.external = Collections.unmodifiableMap(ext);
    }

    @Override
    public boolean contains(TagType type, String tag, String id) {
        return members(type, tag).contains(id);
    }

    @Override
    public boolean exists(TagType type, String tag) {
        return tags.getOrDefault(type, Map.of()).containsKey(tag) || external.getOrDefault(type, Set.of()).contains(tag);
    }

    /** Members of a tag; empty if unknown or external. */
    public Set<String> members(TagType type, String tag) {
        return tags.getOrDefault(type, Map.of()).getOrDefault(tag, Set.of());
    }

    /** All tags defined in our data for {@code type}. */
    public Set<String> tags(TagType type) {
        return tags.getOrDefault(type, Map.of()).keySet();
    }

    public int size(TagType type) {
        return tags.getOrDefault(type, Map.of()).size();
    }
}
