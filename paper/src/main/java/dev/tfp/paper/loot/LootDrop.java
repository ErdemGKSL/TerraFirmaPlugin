/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.List;
import java.util.Objects;

/**
 * One stack a loot table produced: the item id, its count (may exceed the item's max stack size; the Paper side splits it)
 * and the item-data functions still to apply, in order.
 */
public record LootDrop(String itemId, int count, List<LootFunction.StackOp> ops) {
    public LootDrop {
        Objects.requireNonNull(itemId, "itemId");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
        ops = List.copyOf(ops);
    }

    public static LootDrop of(String itemId, int count) {
        return new LootDrop(itemId, count, List.of());
    }
}
