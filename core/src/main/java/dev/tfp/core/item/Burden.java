/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (CarryCount, getCarryCount)
 */
package dev.tfp.core.item;

import java.util.List;

/**
 * TFC's {@code Helpers.CarryCount}: how many huge, very heavy items a subject carries. One gives exhausted, more than
 * one gives overburdened. TFC applies the effects every 20 ticks while overburdening is enabled.
 */
public enum Burden {
    /** No huge, very heavy item. */
    NONE,
    /** One huge, very heavy item: the exhausted effect. */
    ONE,
    /** Two or more: the overburdened effect. */
    MORE_THAN_ONE;

    /** Whether any burden effect applies. */
    public boolean isNonZero() {
        return this != NONE;
    }

    /** TFC {@code Helpers.getCarryCount}: counts up to two matching items and stops there. */
    public static Burden of(List<ItemSizing> carried) {
        int count = 0;
        for (ItemSizing item : carried) {
            if (item.weight() == ItemWeight.VERY_HEAVY && item.size() == ItemSize.HUGE) {
                count++;
                if (count == 2) {
                    break;
                }
            }
        }
        return switch (count) {
            case 0 -> NONE;
            case 1 -> ONE;
            default -> MORE_THAN_ONE;
        };
    }
}
