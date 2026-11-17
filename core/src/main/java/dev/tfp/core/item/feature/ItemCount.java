/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item.feature;

import java.util.Objects;

/**
 * One non-empty slot of an item list as a tooltip sees it (PAR-INFRA-50): the item id, a key that is equal for stacks
 * TFC treats as the same item ({@code ItemComponent} equality: the item and its components), and the count.
 *
 * @param itemId   the item id, such as {@code tfp:food/barley_grain}
 * @param identity equal for stacks that merge in the barrel's list (the item id plus a digest of its data)
 * @param count    the stack size, above 0
 */
public record ItemCount(String itemId, String identity, int count) {
    public ItemCount {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(identity, "identity");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be above 0: " + count);
        }
    }

    /** A stack without data: its identity is its item id. */
    public static ItemCount of(String itemId, int count) {
        return new ItemCount(itemId, itemId, count);
    }
}
