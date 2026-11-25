/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrateBlockEntity.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/CrateBlock.java @ c3b4d9536
 */
package dev.tfp.core.misc;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** PAR-DECO-24: the pure rules of TFC's crate (36 slots, one kind of item, a double click moves a whole inventory in). */
public final class CrateRules {
    /** {@code CrateBlockEntity.SLOTS}. */
    public static final int SLOTS = 36;
    /** {@code CrateBlockEntity.DOUBLE_CLICK_TICKS}. */
    public static final int DOUBLE_CLICK_TICKS = 10;
    /** {@code CrateBlockEntity.NO_CLICK}. */
    public static final long NO_CLICK = Long.MIN_VALUE / 2;

    private CrateRules() {
    }

    /** {@code CrateBlockEntity.checkDoubleClick}: the second insert click within 10 game ticks of the first. */
    public static boolean isDoubleClick(long gameTime, long lastClickTick) {
        return gameTime - lastClickTick <= DOUBLE_CLICK_TICKS;
    }

    /**
     * {@code CrateBlockEntity.isItemValid}: the item must fit a chest ({@code TFCChestBlockEntity.isValid}), must not be a
     * container (contents, vessel, bundle or container component), and must match the first non-empty slot: the same item
     * and components, or food that differs only in its creation date.
     *
     * @param slots the 36 slots, null or an item for each; a null entry is empty
     * @param same  same item and components, or stackable food except the creation date
     */
    public static <T> boolean accepts(T candidate, List<T> slots, Predicate<T> fitsChest, Predicate<T> isContainer,
            BiPredicate<T, T> same) {
        if (!fitsChest.test(candidate) || isContainer.test(candidate)) {
            return false;
        }
        for (T contained : slots) {
            if (contained != null) {
                return same.test(contained, candidate);
            }
        }
        return true;
    }

    /**
     * {@code ItemHandlerHelper.calcRedstoneFromInventory}: the comparator signal. {@code fill} is the sum over the non-empty
     * slots of {@code count / min(slotLimit, maxStackSize)}; {@code nonEmpty} the number of non-empty slots.
     */
    public static int comparatorSignal(double fill, int nonEmpty) {
        float f = (float) (fill / SLOTS);
        return (int) Math.floor(f * 14.0f) + (nonEmpty > 0 ? 1 : 0);
    }
}
