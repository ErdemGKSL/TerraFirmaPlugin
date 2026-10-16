/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.hooks;

/**
 * Food queries used by ingredients ({@code tfc:not_rotten}, {@code tfc:has_trait}, ...).
 * Implemented by the food system (M3); {@code S} is the platform item stack type.
 */
public interface FoodHooks<S> {
    /** TFC {@code FoodCapability.isRotten}. Non-food items are never rotten. */
    boolean isRotten(S stack);

    /** TFC {@code FoodCapability.hasTrait}; {@code trait} is a food trait id such as {@code tfc:salted}. */
    boolean hasTrait(S stack, String trait);
}
