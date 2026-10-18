/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/Size.java @ c3b4d9536
 */
package dev.tfp.core.item;

import java.util.Locale;

/**
 * TFC's item {@code Size}: which container an item may be stored in. The ordinal order is the TFC order, smallest
 * first. Only the comparisons are used by the rules; the names are the serialized forms.
 */
public enum ItemSize {
    /** Fits in anything. */
    TINY,
    /** Fits in anything. */
    VERY_SMALL,
    /** Fits in small vessels. */
    SMALL,
    /** Fits in large vessels. */
    NORMAL,
    /** Fits in chests. Pit kilns can hold four. */
    LARGE,
    /** Pit kilns can only hold one. */
    VERY_LARGE,
    /** Pit kilns can only hold one. Counts towards overburdened when also {@link ItemWeight#VERY_HEAVY}. */
    HUGE;

    /** The TFC serialized name, the lower-cased constant name. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** TFC {@code Size.isSmallerThan}: strictly smaller. */
    public boolean isSmallerThan(ItemSize other) {
        return this.ordinal() < other.ordinal();
    }

    /** TFC {@code Size.isEqualOrSmallerThan}: smaller or the same. */
    public boolean isEqualOrSmallerThan(ItemSize other) {
        return this.ordinal() <= other.ordinal();
    }
}
