/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (weld, getWeldStatus, canWeldNow),
 * file: src/main/java/net/dries007/tfc/common/recipes/WeldingRecipe.java @ c3b4d9536 (assemble),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (the hammer branch of interactWithAnvil)
 */
package dev.tfp.paper.anvil.weld;

import dev.tfp.core.anvil.WeldingRecipe;
import dev.tfp.core.anvil.WeldingRecipes;
import dev.tfp.core.craft.CraftOutput;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The welding recipes of the recipe runtime ({@code tfc:welding}) as core {@link WeldingRecipes}, with each recipe's result
 * provider, compiled once per recipe index. Any thread.
 */
public final class WeldBook {
    /** One compiled recipe: the core rule and the provider that makes the result stack. */
    public record Entry(WeldingRecipe recipe, ItemStackProvider provider) {
    }

    /** The compiled set. */
    public record Compiled(WeldingRecipes recipes, List<Entry> entries) {
        /** The entry of {@code recipe} (by id). */
        public Optional<Entry> entry(WeldingRecipe recipe) {
            return entries.stream().filter(e -> e.recipe().id().equals(recipe.id())).findFirst();
        }
    }

    private record Cache(RecipeIndex index, Compiled compiled) {
    }

    private static final Compiled EMPTY = new Compiled(WeldingRecipes.EMPTY, List.of());

    private final Supplier<RecipeIndex> index;
    private final Compiled fixed;
    private final AtomicReference<Cache> cache = new AtomicReference<>(new Cache(null, EMPTY));

    public WeldBook(Recipes recipes) {
        this.index = Objects.requireNonNull(recipes, "recipes")::index;
        this.fixed = null;
    }

    /** A book over given entries (tests). */
    public WeldBook(List<Entry> entries) {
        this.index = null;
        this.fixed = new Compiled(new WeldingRecipes(entries.stream().map(Entry::recipe).toList()), List.copyOf(entries));
    }

    /** The current welding recipes in recipe-id order. */
    public Compiled get() {
        if (fixed != null) {
            return fixed;
        }
        RecipeIndex now = index.get();
        Cache c = cache.get();
        if (c.index() == now) {
            return c.compiled();
        }
        List<Entry> list = new ArrayList<>();
        for (RecipeEntry e : now.all(RecipeTypes.WELDING)) {
            Entry entry = compile(e);
            if (entry != null) {
                list.add(entry);
            }
        }
        Compiled next = new Compiled(new WeldingRecipes(list.stream().map(Entry::recipe).toList()), List.copyOf(list));
        cache.set(new Cache(now, next));
        return next;
    }

    /** One {@code tfc:welding} entry; null when its result is not an item stack. */
    static Entry compile(RecipeEntry entry) {
        SchemaRecord r = entry.recipe();
        ItemStackProvider result = r.require("result", ItemStackProvider.class);
        ItemStackTemplate stack = result.stack().orElse(null);
        if (stack == null) {
            return null;
        }
        WeldingRecipe.Bonus bonus = r.get("bonus", String.class).map(s -> WeldingRecipe.Bonus.valueOf(s.toUpperCase(Locale.ROOT)))
            .orElse(WeldingRecipe.Bonus.IGNORE);
        return new Entry(new WeldingRecipe(entry.id(), r.require("first_input", Ingredient.class),
            r.require("second_input", Ingredient.class), r.get("tier", Integer.class).orElse(0),
            new CraftOutput(stack.id(), Math.max(1, stack.count())), bonus), result);
    }
}
