/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BloomeryRecipe.java @ c3b4d9536 (matchesInput, matchesCatalyst,
 *       consumeInput, getDuration, the CODEC field set)
 */
package dev.tfp.core.bloomery;

import java.util.Objects;

/**
 * A bloomery recipe: the molten primary input it needs, the catalyst it burns, the item it makes and its burn time.
 * Immutable, pure and thread-safe. Item and fluid ids are strings such as {@code tfc:metal/cast_iron}.
 *
 * <p>TFC's {@code SizedFluidIngredient} and {@code SizedIngredient} are reduced to an id and a count. TFC's fluid
 * ingredient test compares the fluid only, not the amount, so {@link #matchesInput(String)} compares the fluid id only.
 * The catalyst test in TFC also requires the stack to hold at least {@code catalystCount} items, which is applied by
 * {@link #matchesCatalyst(String, int)}.
 *
 * @param inputFluid    the fluid id of the primary input (the metal the molten items make)
 * @param inputAmount   millibuckets of {@code inputFluid} needed for one bloom, at least 1
 * @param catalyst      the item id of the catalyst (the fuel)
 * @param catalystCount catalyst items needed for one bloom, at least 1
 * @param result        the item id of the bloom that is made
 * @param duration      burn time in calendar ticks from lighting to completion, at least 1
 */
public record BloomeryRecipe(String inputFluid, int inputAmount, String catalyst, int catalystCount, String result, int duration) {
    public BloomeryRecipe {
        Objects.requireNonNull(inputFluid, "inputFluid");
        Objects.requireNonNull(catalyst, "catalyst");
        Objects.requireNonNull(result, "result");
        if (inputAmount < 1) {
            throw new IllegalArgumentException("inputAmount must be >= 1: " + inputAmount);
        }
        if (catalystCount < 1) {
            throw new IllegalArgumentException("catalystCount must be >= 1: " + catalystCount);
        }
        if (duration < 1) {
            throw new IllegalArgumentException("duration must be >= 1: " + duration);
        }
    }

    /** TFC {@code matchesInput(FluidStack)}: whether a fluid id is the primary input (amount is not tested). */
    public boolean matchesInput(String fluid) {
        return inputFluid.equals(fluid);
    }

    /**
     * TFC {@code matchesCatalyst(ItemStack)}: the item is the catalyst and the stack holds at least {@code catalystCount}.
     *
     * @param item  the item id of the stack
     * @param count the stack size
     */
    public boolean matchesCatalyst(String item, int count) {
        return catalyst.equals(item) && count >= catalystCount;
    }

    /** TFC {@code consumeInput}: whether one unit is a primary (molten) input of this recipe. */
    public boolean isPrimary(BloomeryItem unit) {
        return unit.fluid() != null && matchesInput(unit.fluid());
    }
}
