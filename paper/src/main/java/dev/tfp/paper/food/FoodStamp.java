/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (setCreationDate, applyTrait on a new stack)
 */
package dev.tfp.paper.food;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * Dates a newly made food stack (M3-SURV-01): TFC gives a new food stack the rounded current player tick
 * ({@code FoodComponent} created with {@code Calendars.get().getTicks()}), then applies the item's starting
 * traits one by one ({@code FoodCapability.applyTrait}, which moves the creation date). Called by the
 * CraftEngine {@code tfp:food_stamp} item processor on every CraftEngine build (give, loot, recipe result).
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack.
 */
public final class FoodStamp {
    private FoodStamp() {
    }

    /**
     * Writes a food state created at player tick {@code now} with {@code traits} applied in order, unless the
     * stack already carries a food entry (a copy keeps its date; a newer-version entry is never touched).
     *
     * @param foodDecayModifier the item's food-data decay modifier (only matters for a rotten check while
     *                          applying traits)
     * @return true if the stack was stamped
     */
    public static boolean stamp(ItemStack stack, long now, List<FoodTrait> traits, float foodDecayModifier,
            FoodConfig config) {
        if (stack == null || stack.isEmpty() || FoodPdc.has(stack)) {
            return false;
        }
        return FoodPdc.write(stack, created(now, traits, foodDecayModifier, config));
    }

    /** The state {@link #stamp} writes. */
    public static FoodState created(long now, List<FoodTrait> traits, float foodDecayModifier, FoodConfig config) {
        FoodState state = FoodState.createdAt(now, config);
        for (FoodTrait t : traits) {
            state = state.withTraitApplied(t, foodDecayModifier, now, config);
        }
        return state;
    }
}
