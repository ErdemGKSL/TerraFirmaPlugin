/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/LoomRecipe.java @ c3b4d9536 (the fields, getInputCount, getStepCount,
 * getInProgressTexture)
 */
package dev.tfp.core.craft;

/**
 * One loom recipe: a stack of an item is woven through a number of steps and becomes the result.
 *
 * @param id              the recipe id, the TFC file path under {@code tfc:recipe/}
 * @param ingredient      the item id woven
 * @param ingredientCount the number of items the loom holds before it can be pushed, {@code >= 1}
 * @param result          the result stack
 * @param steps           the number of pushes that finish the weave, {@code >= 1}
 * @param texture         the texture shown while the weave is in progress (presentation only)
 */
public record LoomRecipe(String id, String ingredient, int ingredientCount, CraftOutput result, int steps, String texture) {
    public LoomRecipe {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty loom recipe id");
        if (ingredient == null || ingredient.isEmpty()) throw new IllegalArgumentException("loom recipe " + id + " has no ingredient");
        if (ingredientCount < 1) throw new IllegalArgumentException("loom recipe " + id + " has count " + ingredientCount);
        if (result == null) throw new IllegalArgumentException("loom recipe " + id + " has no result");
        if (steps < 1) throw new IllegalArgumentException("loom recipe " + id + " has steps " + steps);
    }
}
