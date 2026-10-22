/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.Tfp;
import dev.tfp.core.json.Ids;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.tags.TagSnapshot;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enumerates a tag's members for the recipe index (TFC {@code Ingredient.getItems()} over a tag). Our ids come from the
 * tag registry (it knows every member of ours); vanilla ids are those of the server's registries (the "universe")
 * that {@link TagSnapshot#is} accepts, so members reached only through server or common tags are found too. The
 * result is exact for every id the server knows. Memoised per tag; one instance serves one index build (not
 * thread-safe).
 */
public final class TagMembers {
    private final TagSnapshot tags;
    private final Map<TagType, Collection<String>> vanilla;
    private final Map<TagType, Map<String, Set<String>>> memo = new EnumMap<>(TagType.class);

    /** @param vanilla per type, every vanilla id the server has (items, blocks, fluids); missing types are empty */
    public TagMembers(TagSnapshot tags, Map<TagType, ? extends Collection<String>> vanilla) {
        this.tags = tags;
        this.vanilla = new EnumMap<>(TagType.class);
        for (TagType t : TagType.values()) {
            Collection<String> ids = vanilla.get(t);
            this.vanilla.put(t, ids == null ? List.of() : List.copyOf(ids));
        }
    }

    public TagSnapshot snapshot() {
        return tags;
    }

    /** The members of {@code tag} ({@code #} and {@code tfc:} accepted), in a stable order. */
    public Set<String> members(TagType type, String tag) {
        String t = TagSnapshot.normalise(tag);
        return memo.computeIfAbsent(type, k -> new HashMap<>()).computeIfAbsent(t, k -> compute(type, k));
    }

    private Set<String> compute(TagType type, String tag) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : tags.registry().members(type, tag)) {
            if (Ids.namespace(id).equals(Tfp.NAMESPACE) || tags.is(type, id, tag)) {
                out.add(id);
            }
        }
        for (String id : vanilla.get(type)) {
            if (!out.contains(id) && tags.is(type, id, tag)) {
                out.add(id);
            }
        }
        return Set.copyOf(out);
    }
}
