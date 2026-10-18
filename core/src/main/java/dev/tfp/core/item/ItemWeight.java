/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/Weight.java @ c3b4d9536
 */
package dev.tfp.core.item;

import java.util.Locale;

/**
 * TFC's item {@code Weight}. The weight sets the maximum stack size: the lighter the item, the more fit in one stack.
 * Weight does not depend on size and size does not depend on weight.
 */
public enum ItemWeight {
    VERY_LIGHT(64),
    LIGHT(32),
    MEDIUM(16),
    HEAVY(4),
    VERY_HEAVY(1);

    private final int stackSize;

    ItemWeight(int stackSize) {
        this.stackSize = stackSize;
    }

    /** The maximum stack size for items of this weight (TFC {@code Weight.stackSize}). */
    public int stackSize() {
        return stackSize;
    }

    /** The TFC serialized name, the lower-cased constant name. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** TFC {@code Weight.isSmallerThan}: lighter, i.e. a larger stack size. */
    public boolean isSmallerThan(ItemWeight other) {
        return this.stackSize > other.stackSize;
    }
}
