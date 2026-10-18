/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import java.util.List;
import java.util.Objects;

/**
 * One entry of a loot pool: an item, or a group of alternatives. Each TFC pool has rolls 1 and one entry.
 */
public sealed interface LootEntry {

    /**
     * A {@code set_count} function: when its conditions pass, the stack count becomes a value in {@code [min, max]}. A constant count
     * has {@code min == max} and consumes no roll; a uniform count consumes one roll.
     */
    record SetCount(List<HarvestCondition> conditions, int min, int max) {
        public SetCount {
            conditions = List.copyOf(conditions);
            if (min < 1 || max < min) {
                throw new IllegalArgumentException("count must satisfy 1 <= min <= max, was " + min + ".." + max);
            }
        }
    }

    /** An item stack, yielded when its conditions pass. Its count is 1 unless a {@link SetCount} function applies. */
    record Item(String itemId, List<HarvestCondition> conditions, List<SetCount> counts) implements LootEntry {
        public Item {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            conditions = List.copyOf(conditions);
            counts = List.copyOf(counts);
        }
    }

    /**
     * {@code minecraft:alternatives}: when its conditions pass, the first child whose own conditions pass yields, and the rest are not
     * tried. Children are tried in order.
     */
    record Alternatives(List<HarvestCondition> conditions, List<LootEntry> children) implements LootEntry {
        public Alternatives {
            Objects.requireNonNull(children, "children");
            conditions = List.copyOf(conditions);
            children = List.copyOf(children);
            if (children.isEmpty()) {
                throw new IllegalArgumentException("alternatives need at least one child");
            }
        }
    }
}
