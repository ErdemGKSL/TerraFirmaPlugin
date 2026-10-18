/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (the main item slot)
 */
package dev.tfp.core.barrel;

import java.util.Objects;

/**
 * The stack in a barrel's main item slot: an item identifier and a count. An empty stack has a count of zero, whatever the
 * identifier, so every empty stack compares equal to {@link #EMPTY}.
 *
 * @param item  the item identifier, ignored while empty
 * @param count the count, {@code >= 0}
 */
public record BarrelStack(String item, int count) {
    public static final BarrelStack EMPTY = new BarrelStack("", 0);

    public BarrelStack {
        Objects.requireNonNull(item, "item");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        if (count == 0) {
            item = "";
        }
    }

    public boolean isEmpty() {
        return count == 0;
    }
}
