/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AnvilRecipe.java @ c3b4d9536 (getAll, byId, CODEC)
 */
package dev.tfp.paper.anvil.plan;

import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilRecipes;
import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The anvil recipes of the recipe runtime ({@code tfc:anvil}) as core {@link AnvilRecipes}, compiled once per recipe index
 * (a reload swaps the index, so the next call recompiles). Any thread.
 *
 * <p>A recipe whose result is not a plain item (no stack template) cannot be listed as a plan and is skipped; none of TFC's
 * 281 is one.
 */
public final class PlanBook {
    private record Compiled(RecipeIndex index, AnvilRecipes recipes) {
    }

    private final Supplier<RecipeIndex> index;
    private final Supplier<AnvilRecipes> fixed;
    private final AtomicReference<Compiled> compiled = new AtomicReference<>(new Compiled(null, AnvilRecipes.EMPTY));

    public PlanBook(Recipes recipes) {
        this.index = Objects.requireNonNull(recipes, "recipes")::index;
        this.fixed = null;
    }

    /** A book over a given recipe set (tests). */
    public PlanBook(AnvilRecipes recipes) {
        Objects.requireNonNull(recipes, "recipes");
        this.index = null;
        this.fixed = () -> recipes;
    }

    /** The current anvil recipes, in recipe-id order. */
    public AnvilRecipes recipes() {
        if (fixed != null) {
            return fixed.get();
        }
        RecipeIndex now = index.get();
        Compiled c = compiled.get();
        if (c.index() == now) {
            return c.recipes();
        }
        List<AnvilRecipe> list = new ArrayList<>();
        for (RecipeEntry e : now.all(RecipeTypes.ANVIL)) {
            AnvilRecipe r = compile(e);
            if (r != null) {
                list.add(r);
            }
        }
        Compiled next = new Compiled(now, new AnvilRecipes(list));
        compiled.set(next);
        return next.recipes();
    }

    /** One {@code tfc:anvil} entry as a core recipe; null when its result is not an item stack. */
    @SuppressWarnings("unchecked")
    static AnvilRecipe compile(RecipeEntry entry) {
        SchemaRecord r = entry.recipe();
        ItemStackProvider result = r.require("result", ItemStackProvider.class);
        ItemStackTemplate stack = result.stack().orElse(null);
        if (stack == null) {
            return null;
        }
        List<String> names = r.require("rules", List.class);
        List<ForgeRule> rules = new ArrayList<>(names.size());
        for (String n : names) {
            rules.add(ForgeRule.valueOf(n.toUpperCase(Locale.ROOT)));
        }
        return new AnvilRecipe(entry.id(), r.require("ingredient", Ingredient.class), r.get("tier", Integer.class).orElse(0), rules,
            r.get("apply_bonus", Boolean.class).orElse(false), new CraftOutput(stack.id(), Math.max(1, stack.count())));
    }
}
