/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

/**
 * One item stack a gather produces: the registry id of the item (for example {@code tfc:rock/loose/granite} or
 * {@code minecraft:stick}) and its count, which is at least 1.
 */
public record GatherYield(String itemId, int count) {
    public GatherYield {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1, was " + count);
        }
    }
}
