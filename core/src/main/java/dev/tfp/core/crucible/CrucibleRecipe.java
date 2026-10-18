/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536
 */
package dev.tfp.core.crucible;

import java.util.Objects;

/**
 * A melting recipe as the crucible uses it: TFC's {@code tfc:heating} recipe, reduced to its rule fields. The ingredient
 * is an opaque key, because matching items is platform work; the caller says whether an item matched before it asks for
 * this recipe.
 *
 * @param ingredient     the ingredient key, opaque to core (for example {@code item:minecraft:copper_door})
 * @param temperature    the temperature the item must reach before it melts, in degrees
 * @param resultFluid    the fluid id made, such as {@code tfc:metal/copper}; empty when the recipe makes no fluid
 * @param resultAmount   the fluid amount made in mB, before durability scaling
 * @param useDurability  whether the fluid amount scales down with the item's wear
 * @param hasResultItem  whether the recipe also makes an item (the output item is platform data)
 */
public record CrucibleRecipe(
    String ingredient,
    float temperature,
    String resultFluid,
    int resultAmount,
    boolean useDurability,
    boolean hasResultItem
) {
    public CrucibleRecipe {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(resultFluid, "resultFluid");
        if (resultAmount < 0) {
            throw new IllegalArgumentException("resultAmount must be >= 0: " + resultAmount);
        }
        if (!(temperature >= 0) || Float.isInfinite(temperature)) {
            throw new IllegalArgumentException("temperature must be >= 0 and finite: " + temperature);
        }
    }

    /**
     * TFC {@code HeatingRecipe.isValidTemperature}: the item melts once it reaches the recipe temperature, inclusive.
     *
     * @param itemTemperature the item's temperature
     */
    public boolean isValidTemperature(float itemTemperature) {
        return itemTemperature >= temperature;
    }

    /**
     * TFC {@code HeatingRecipe.assembleFluid}: the fluid amount made from one item. When the recipe uses durability and the
     * item has a durability bar, the amount is {@code floor(amount x (1 - damage / maxDamage))}. Pass {@code maxDamage} of 0 for
     * an item that cannot wear; the amount is then the recipe's full amount.
     *
     * @param damage    the item's current damage
     * @param maxDamage the item's maximum damage, or 0 when it has no durability
     * @return the fluid amount in mB, zero when the recipe makes no fluid
     */
    public int fluidAmount(int damage, int maxDamage) {
        if (useDurability && !resultFluid.isEmpty() && resultAmount > 0 && maxDamage > 0) {
            return (int) Math.floor(resultAmount * (1 - (float) damage / maxDamage));
        }
        return resultAmount;
    }
}
