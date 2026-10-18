/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (areStacksStackableExceptCreationDate, mergeItemStacks)
 */
package dev.tfp.paper.food;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Paper side of TFC's food stack rule, independent of any event wiring: two stacks merge when everything but
 * the {@value FoodPdc#KEY} entry is similar and their food states stack by the core rule
 * ({@link FoodState#stacksWith}). The merge hooks (inventory clicks, pickups, hopper moves) call this.
 */
public final class FoodStacks {
    private FoodStacks() {
    }

    /**
     * Whether {@code a} and {@code b} may merge at player tick {@code now}. {@code foodDecayModifier} is the
     * item's food-data decay modifier (both stacks are the same item when this can return {@code true}).
     * Two stacks without a food entry fall back to {@link ItemStack#isSimilar}; a dated and an undated stack
     * never merge.
     */
    public static boolean canMerge(ItemStack a, ItemStack b, float foodDecayModifier, long now, FoodConfig config) {
        if (a == null || b == null) {
            return false;
        }
        Optional<FoodState> fa = FoodPdc.read(a);
        Optional<FoodState> fb = FoodPdc.read(b);
        if (fa.isEmpty() && fb.isEmpty()) {
            return a.isSimilar(b);
        }
        if (fa.isEmpty() || fb.isEmpty()) {
            return false;
        }
        return FoodPdc.withoutFood(a).isSimilar(FoodPdc.withoutFood(b))
                && fa.get().stacksWith(fb.get(), foodDecayModifier, now, config);
    }

    /**
     * Writes the merged food state into {@code target} (TFC {@code mergeItemStacks}: the earlier creation date
     * wins) after {@link #canMerge} said yes. Amounts are the caller's business. No-op when either is undated.
     */
    public static void mergeInto(ItemStack target, ItemStack other) {
        Optional<FoodState> ft = FoodPdc.read(target);
        Optional<FoodState> fo = FoodPdc.read(other);
        if (ft.isPresent() && fo.isPresent()) {
            FoodPdc.write(target, ft.get().mergedWith(fo.get()));
        }
    }
}
