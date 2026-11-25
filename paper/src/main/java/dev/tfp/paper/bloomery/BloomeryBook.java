/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BloomeryRecipe.java @ c3b4d9536 (the recipe fields)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.core.bloomery.BloomeryRecipe;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The bloomery recipes of the recipe runtime ({@code tfc:bloomery}) as core {@link BloomeryRecipe}s in recipe-id order,
 * compiled once per recipe index. A recipe whose fluid is not one fluid, whose catalyst is not one item or whose result is
 * not an item stack is left out (TFC's only recipe, the raw iron bloom, is a plain one). Any thread.
 */
public final class BloomeryBook {
    private record Cache(RecipeIndex index, List<BloomeryRecipe> recipes) {
    }

    private final Supplier<RecipeIndex> index;
    private final List<BloomeryRecipe> fixed;
    private final AtomicReference<Cache> cache = new AtomicReference<>(new Cache(null, List.of()));

    public BloomeryBook(Recipes recipes) {
        this.index = Objects.requireNonNull(recipes, "recipes")::index;
        this.fixed = null;
    }

    /** A book over given recipes (tests). */
    public BloomeryBook(List<BloomeryRecipe> recipes) {
        this.index = null;
        this.fixed = List.copyOf(recipes);
    }

    /** The current recipes. */
    public List<BloomeryRecipe> get() {
        if (fixed != null) {
            return fixed;
        }
        RecipeIndex now = index.get();
        Cache c = cache.get();
        if (c.index() == now) {
            return c.recipes();
        }
        List<BloomeryRecipe> list = new ArrayList<>();
        for (RecipeEntry e : now.all(RecipeTypes.BLOOMERY)) {
            compile(e.recipe()).ifPresent(list::add);
        }
        List<BloomeryRecipe> next = List.copyOf(list);
        cache.set(new Cache(now, next));
        return next;
    }

    /** One {@code tfc:bloomery} record. */
    static Optional<BloomeryRecipe> compile(SchemaRecord r) {
        SizedFluidIngredient fluid = r.require("fluid", SizedFluidIngredient.class);
        SizedIngredient catalyst = r.require("catalyst", SizedIngredient.class);
        ItemStackProvider result = r.require("result", ItemStackProvider.class);
        if (!(fluid.ingredient() instanceof FluidIngredient.Single f)
            || !(catalyst.ingredient() instanceof Ingredient.Item c) || result.stack().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BloomeryRecipe(f.fluid(), fluid.amount(), c.item(), catalyst.count(),
            result.stack().get().id(), r.require("duration", Integer.class)));
    }
}
