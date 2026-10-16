/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.ingredient;

import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.tag.TagLookup;

/**
 * Everything an {@link Ingredient} needs to test a stack of platform type {@code S}. The Paper
 * side implements it over {@code ItemStack} (item id through the CraftEngine adapter, item data
 * from the PDC); tests implement it over plain records. Implementations must be safe to call from
 * the thread that owns the stack.
 */
public interface IngredientContext<S> {
    /** The stack's item id ({@code tfp:...} or {@code minecraft:...}); never null for non-empty stacks. */
    String itemId(S stack);

    TagLookup tags();

    FoodHooks<S> food();

    HeatHooks<S> heat();

    FluidHooks<S> fluids();
}
