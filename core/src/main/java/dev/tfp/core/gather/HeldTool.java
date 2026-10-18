/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The tool state a loot table is evaluated with: which {@link ToolTag}s the held item (or the enchantment on it) carries.
 * An empty hand is {@link #NONE}.
 */
public record HeldTool(Set<ToolTag> tags) {
    public static final HeldTool NONE = new HeldTool(Set.of());

    public HeldTool {
        Objects.requireNonNull(tags, "tags");
        tags = Set.copyOf(tags);
    }

    public static HeldTool of(ToolTag... tags) {
        return new HeldTool(new HashSet<>(List.of(tags)));
    }

    public boolean has(ToolTag tag) {
        return tags.contains(tag);
    }
}
