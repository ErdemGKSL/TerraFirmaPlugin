/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java (weld and getWeldStatus, which look up the first
 * matching welding recipe and then check its tier) @ c3b4d9536
 */
package dev.tfp.core.anvil;

import dev.tfp.core.recipe.ingredient.IngredientContext;
import java.util.List;
import java.util.Optional;

/**
 * An immutable set of welding recipes. Pure and thread-safe.
 */
public final class WeldingRecipes {
    public static final WeldingRecipes EMPTY = new WeldingRecipes(List.of());

    private final List<WeldingRecipe> recipes;

    /**
     * @param recipes the recipes, in registry order
     */
    public WeldingRecipes(List<WeldingRecipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    /**
     * @return every recipe whose inputs accept the two stacks, in registry order. Tier is not checked.
     */
    public <S> List<WeldingRecipe> candidates(S main, S secondary, IngredientContext<S> ctx) {
        return recipes.stream().filter(r -> r.matches(main, secondary, ctx)).toList();
    }

    /**
     * The first recipe that accepts the two stacks. TFC then checks the anvil tier with {@link WeldingRecipe#isCorrectTier(int)}, and
     * reports a tier problem rather than trying another recipe.
     */
    public <S> Optional<WeldingRecipe> find(S main, S secondary, IngredientContext<S> ctx) {
        return recipes.stream().filter(r -> r.matches(main, secondary, ctx)).findFirst();
    }
}
