/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.recipe.ItemModifierHooks;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-PROC-08 on the real datagen output: every one of TFC's 331 barrel recipes decodes into the barrel's recipe book. */
class BarrelRecipesContentDataMockBukkitTest extends MockServerTest {

    @Test
    void all331BarrelRecipesLoad() {
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
        FakeCustomItems items = new FakeCustomItems();
        Tags tags = new Tags(registries, items, new FakeCustomBlocks(), ServerTags.BUKKIT, rules, () -> rules,
            Runnable::run, err -> {
                throw new AssertionError(err);
            });
        ItemFoodHooks food = new ItemFoodHooks(() -> 0L, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        ItemHeatHooks heat = new ItemHeatHooks(() -> 0L, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        Recipes recipes = new Recipes(registries, tags, RecipeModule::vanillaIds, Runnable::run, err -> {
            throw new AssertionError(err);
        }, new ItemModifierHooks.Services(items, tags::itemId, food, () -> FoodDefinitions.EMPTY, heat,
            ItemHeatHooks.Capacities.UNIT, () -> 0L));
        recipes.rebuild().join();
        BarrelRecipeBook book = new BarrelRecipeBook(recipes, new BarrelItemTanks(new BarrelItemTanks.Services(
            tags::itemId, (k, f) -> true, id -> items.create(id, 1), items::exists)), BarrelRules.DEFAULT_CAPACITY);

        List<BarrelRecipeView> sealed = book.all(BarrelRecipeView.Kind.SEALED);
        List<BarrelRecipeView> instant = book.all(BarrelRecipeView.Kind.INSTANT);
        List<BarrelRecipeView> fluid = book.all(BarrelRecipeView.Kind.INSTANT_FLUID);
        assertThat(sealed).hasSize(309);
        assertThat(instant).hasSize(20);
        assertThat(fluid).hasSize(2);
        assertThat(sealed.size() + instant.size() + fluid.size()).isEqualTo(331);

        for (List<BarrelRecipeView> list : List.of(sealed, instant, fluid)) {
            for (BarrelRecipeView r : list) {
                assertThat(r.inputFluid().amount()).as(r.id()).isPositive();
                if (r.outputFluid() != null) {
                    assertThat(TfcFluids.get(BarrelRecipeBook.fluidId(r.outputFluid().id()))).as(r.id() + " output fluid")
                        .isPresent();
                }
            }
        }
        assertThat(fluid).allSatisfy(r -> assertThat(r.addedFluid()).isNotNull());
        BarrelRecipeView brined = sealed.stream().filter(r -> r.id().endsWith("barrel/brined")).findFirst().orElseThrow();
        assertThat(brined.duration()).isEqualTo(3600);
        assertThat(brined.inputFluid().amount()).isEqualTo(125);
        assertThat(brined.outputItem()).isNotNull();
        BarrelRecipeView vinegar = sealed.stream().filter(r -> r.id().endsWith("barrel/preserved_in_vinegar")).findFirst()
            .orElseThrow();
        assertThat(vinegar.infinite()).isTrue();
        assertThat(vinegar.onSeal()).isNotNull();
        assertThat(vinegar.onUnseal()).isNotNull();
        assertThat(sealed.stream().filter(BarrelRecipeView::infinite).count()).as("infinite sealed recipes").isPositive();
    }
}
