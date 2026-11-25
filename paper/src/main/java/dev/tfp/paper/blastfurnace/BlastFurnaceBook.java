/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/BlastFurnaceRecipe.java @ c3b4d9536 (the CODEC field set)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceRecipe;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.recipe.fluid.FluidStackTemplate;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
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
 * The blast furnace recipes of the recipe runtime ({@code tfc:blast_furnace}) as core {@link BlastFurnaceRecipe}s in
 * recipe-id order, compiled once per recipe index. A recipe whose input is not one fluid or whose catalyst is not one item
 * is left out (TFC's only recipe, pig iron, is a plain one). Any thread.
 */
public final class BlastFurnaceBook {
    private record Cache(RecipeIndex index, List<BlastFurnaceRecipe> recipes) {
    }

    private final Supplier<RecipeIndex> index;
    private final List<BlastFurnaceRecipe> fixed;
    private final AtomicReference<Cache> cache = new AtomicReference<>(new Cache(null, List.of()));

    public BlastFurnaceBook(Recipes recipes) {
        this.index = Objects.requireNonNull(recipes, "recipes")::index;
        this.fixed = null;
    }

    /** A book over given recipes (tests). */
    public BlastFurnaceBook(List<BlastFurnaceRecipe> recipes) {
        this.index = null;
        this.fixed = List.copyOf(recipes);
    }

    /** The current recipes. */
    public List<BlastFurnaceRecipe> get() {
        if (fixed != null) {
            return fixed;
        }
        RecipeIndex now = index.get();
        Cache c = cache.get();
        if (c.index() == now) {
            return c.recipes();
        }
        List<BlastFurnaceRecipe> list = new ArrayList<>();
        for (RecipeEntry e : now.all(RecipeTypes.BLAST_FURNACE)) {
            compile(e.recipe()).ifPresent(list::add);
        }
        List<BlastFurnaceRecipe> next = List.copyOf(list);
        cache.set(new Cache(now, next));
        return next;
    }

    /** One {@code tfc:blast_furnace} record. */
    static Optional<BlastFurnaceRecipe> compile(SchemaRecord r) {
        SizedFluidIngredient fluid = r.require("fluid", SizedFluidIngredient.class);
        Ingredient catalyst = r.require("catalyst", Ingredient.class);
        FluidStackTemplate result = r.require("result", FluidStackTemplate.class);
        if (!(fluid.ingredient() instanceof FluidIngredient.Single f) || !(catalyst instanceof Ingredient.Item c)) {
            return Optional.empty();
        }
        return Optional.of(new BlastFurnaceRecipe(f.fluid(), fluid.amount(), c.item(), result.id(), result.amount()));
    }
}
