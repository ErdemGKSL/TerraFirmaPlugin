/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ChiselRecipe.java @ c3b4d9536
 */
package dev.tfp.core.chisel;

import java.util.List;

/**
 * A chisel recipe: the blocks it accepts, the mode it applies to, the block it places, and an optional extra item the chisel
 * gives back. Pure and immutable; blocks and items are string ids, so the core stays free of platform types.
 *
 * @param id         the recipe's name, for diagnostics
 * @param ingredients the block ids this recipe accepts. The recipe matches any one of them, as TFC's {@code BlockIngredient} does.
 * @param result     the block id placed in the target's position
 * @param mode       the chisel mode the recipe belongs to
 * @param itemOutput the extra item given to the player, or {@code null} when the recipe gives none
 */
public record ChiselRecipe(String id, List<String> ingredients, String result, ChiselMode mode, ItemOutput itemOutput) {
    /**
     * An item given to the player when the recipe is performed.
     *
     * @param item  the item id
     * @param count the number of items
     */
    public record ItemOutput(String item, int count) {
        public ItemOutput {
            if (item == null || item.isEmpty()) {
                throw new IllegalArgumentException("item must not be empty");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + item);
            }
        }
    }

    public ChiselRecipe {
        requireNonEmpty(id, "id");
        requireNonEmpty(result, "result");
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null: " + id);
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("ingredients must not be empty: " + id);
        }
        for (String ingredient : ingredients) {
            requireNonEmpty(ingredient, "ingredient");
        }
        ingredients = List.copyOf(ingredients);
    }

    /**
     * TFC {@code ChiselRecipe.matches}: whether the recipe accepts the block in the given mode.
     *
     * @param block the block id of the target
     * @param mode  the player's current chisel mode
     */
    public boolean matches(String block, ChiselMode mode) {
        return this.mode == mode && ingredients.contains(block);
    }

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
