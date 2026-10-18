/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/recipes/AnvilRecipe.java (hasAny, getAll, byId) and
 * src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java (setAndUpdateSlots, the default recipe choice)
 * @ c3b4d9536
 */
package dev.tfp.core.anvil;

import dev.tfp.core.recipe.ingredient.IngredientContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable set of anvil recipes, with the lookups the anvil uses. Pure and thread-safe.
 */
public final class AnvilRecipes {
    public static final AnvilRecipes EMPTY = new AnvilRecipes(List.of());

    private final List<AnvilRecipe> recipes;
    private final Map<String, AnvilRecipe> byId;

    /**
     * @param recipes the recipes, in registry order; ids must be unique
     */
    public AnvilRecipes(List<AnvilRecipe> recipes) {
        this.recipes = List.copyOf(recipes);
        this.byId = new HashMap<>();
        for (AnvilRecipe recipe : this.recipes) {
            if (byId.put(recipe.id(), recipe) != null) {
                throw new IllegalArgumentException("Duplicate anvil recipe id: " + recipe.id());
            }
        }
    }

    /**
     * @return every recipe, in registry order
     */
    public List<AnvilRecipe> all() {
        return recipes;
    }

    /**
     * @return the recipe with this id, or empty
     */
    public Optional<AnvilRecipe> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * @return every recipe whose input accepts {@code stack} and whose minimum tier is at most {@code anvilTier}, in registry order
     */
    public <S> List<AnvilRecipe> candidates(S stack, int anvilTier, IngredientContext<S> ctx) {
        return recipes.stream().filter(r -> r.matches(stack, anvilTier, ctx)).toList();
    }

    /**
     * @return {@code true} if at least one recipe matches, as {@link #candidates} would return
     */
    public <S> boolean hasAny(S stack, int anvilTier, IngredientContext<S> ctx) {
        return recipes.stream().anyMatch(r -> r.matches(stack, anvilTier, ctx));
    }

    /**
     * The recipe an item gets when it is placed on the anvil and no plan is chosen yet: the only candidate if there is exactly one,
     * otherwise the candidate the player last chose on the anvil, if it is among them.
     *
     * @param candidates       the candidates from {@link #candidates}
     * @param lastRecipeId     the id the anvil remembers, or {@code null}
     * @return the default plan, or empty when the player must choose one
     */
    public static Optional<AnvilRecipe> defaultFor(List<AnvilRecipe> candidates, String lastRecipeId) {
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        if (lastRecipeId == null) {
            return Optional.empty();
        }
        return candidates.stream().filter(r -> r.id().equals(lastRecipeId)).findAny();
    }
}
