/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.craft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.craft.AdvancedCraftGrid;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.recipe.IngredientKeys;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.TagMembers;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-03 on the real datagen output: every crafting recipe of TFC's data (2912 under {@code tfp:crafting/} plus the 66
 * overrides of vanilla recipes) compiles for the grid, and each shaped or shapeless one matches a grid built from its own
 * ingredients and is a candidate of the index for that grid's first item.
 */
class CraftingContentDataMockBukkitTest extends MockServerTest {
    /** TFC's {@code data/tfc/recipe/crafting} files at c3b4d9536 (1686 + 569 + 372 + 283 + food combining + casting). */
    static final int TFC_CRAFTING = 2912;
    /** TFC's crafting overrides of vanilla recipes ({@code data/minecraft/recipe}, 48 shaped + 18 shapeless). */
    static final int TFC_VANILLA_OVERRIDES = 66;

    @Test
    void everyCraftingRecipeResolves() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        Registries registries = new Registries();
        registries.reload(Runnable::run, () -> {
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).join();
        TagRules rules = TagRules.load(root);
        Tags tags = new Tags(registries, new FakeCustomItems(), new FakeCustomBlocks(), ServerTags.BUKKIT, rules,
                () -> rules, Runnable::run, err -> {
                    throw new AssertionError(err);
                });
        TagMembers members = new TagMembers(tags.snapshot(), RecipeModule.vanillaIds());
        RecipeIndex index = RecipeIndex.build(registries.get(), members);

        Map<String, CraftRecipe> compiled = new TreeMap<>();
        for (String type : CraftRecipe.TYPES) {
            for (RecipeEntry e : index.all(type)) {
                compiled.put(e.id(), CraftRecipe.compile(e));
            }
        }
        long tfc = compiled.keySet().stream().filter(id -> id.startsWith("tfp:crafting/")).count();
        long vanilla = compiled.keySet().stream().filter(id -> id.startsWith("minecraft:")).count();
        assertThat(tfc).as("TFC's crafting recipes").isEqualTo(TFC_CRAFTING);
        assertThat(vanilla).as("TFC's overrides of vanilla recipes").isEqualTo(TFC_VANILLA_OVERRIDES);
        assertThat(compiled).hasSize(TFC_CRAFTING + TFC_VANILLA_OVERRIDES);
        Map<CraftRecipe.Kind, Integer> byKind = new TreeMap<>();
        compiled.values().forEach(r -> byKind.merge(r.kind(), 1, Integer::sum));
        assertThat(byKind).containsEntry(CraftRecipe.Kind.ADVANCED_SHAPED, 372)
                .containsEntry(CraftRecipe.Kind.ADVANCED_SHAPELESS, 283)
                .containsEntry(CraftRecipe.Kind.FOOD_COMBINING, 1).containsEntry(CraftRecipe.Kind.CASTING, 1)
                .containsEntry(CraftRecipe.Kind.SHAPED, 1734).containsEntry(CraftRecipe.Kind.SHAPELESS, 587);

        Map<Ingredient, IngredientKeys.Keys> keys = new IdentityHashMap<>();
        List<String> unresolved = new ArrayList<>();
        int checked = 0;
        for (CraftRecipe r : compiled.values()) {
            if (!r.kind().shaped() && !r.kind().shapeless()) {
                continue;
            }
            // One representative item per ingredient (its smallest id; "*" for an ingredient that accepts any item)
            String[] grid = new String[9];
            List<Ingredient> ingredients = r.ingredients();
            boolean ok = true;
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ing = ingredients.get(i);
                if (ing == null) {
                    continue;
                }
                IngredientKeys.Keys k = keys.computeIfAbsent(ing, x -> IngredientKeys.item(x, members));
                String pick = k.any() ? "*" : k.ids().stream().sorted().findFirst().orElse(null);
                if (pick == null) {
                    ok = false;
                    break;
                }
                int slot = r.kind().shaped() ? (i % r.width()) + (i / r.width()) * 3 : i;
                grid[slot] = pick;
            }
            if (!ok) {
                unresolved.add(r.id() + " (an ingredient with no item)");
                continue;
            }
            List<String> g = Arrays.asList(grid);
            java.util.function.BiPredicate<Ingredient, String> test = (ing, s) -> {
                IngredientKeys.Keys k = keys.computeIfAbsent(ing, x -> IngredientKeys.item(x, members));
                return k.any() || s.equals("*") || k.ids().contains(s);
            };
            boolean matches = r.kind().shaped()
                    ? AdvancedCraftGrid.shapedMatch(r.ingredients(), r.width(), r.height(),
                            AdvancedCraftGrid.positioned(3, g, Objects::isNull), test, Objects::isNull, r.inputSlot()) >= 0
                    : AdvancedCraftGrid.shapelessMatches(r.ingredients(), g, test, Objects::isNull);
            String first = g.stream().filter(Objects::nonNull).findFirst().orElseThrow();
            boolean candidate = first.equals("*")
                    || index.byItem(r.kind().type(), first).stream().anyMatch(e -> e.id().equals(r.id()));
            if (!matches || !candidate) {
                unresolved.add(r.id() + (matches ? " (not a candidate of " + first + ")" : " (own grid does not match)"));
            }
            checked++;
        }
        assertThat(unresolved).as("recipes that cannot be crafted from their own ingredients").isEmpty();
        assertThat(checked).isEqualTo(TFC_CRAFTING + TFC_VANILLA_OVERRIDES - 2);
    }
}
