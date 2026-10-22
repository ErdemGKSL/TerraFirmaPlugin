/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.tags.Tags;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-06: the recipe index on a small synthetic data set (keys, wildcards, order, key spaces, rebuilds). */
class RecipeIndexTest {
    @TempDir
    Path dir;

    private RecipeTestData data;

    @BeforeEach
    void data() {
        data = new RecipeTestData(dir)
                .itemTag("metal_items", "tfp:ingot/copper", "minecraft:iron_ingot")
                .recipe("heating/b_copper", """
                        {"type": "tfp:heating", "ingredient": {"item": "tfp:ingot/copper"}, "temperature": 1080}""")
                .recipe("heating/a_metal", """
                        {"type": "tfp:heating", "ingredient": {"tag": "tfp:metal_items"}, "temperature": 900}""")
                .recipe("heating/c_hot", """
                        {"type": "tfp:heating", "ingredient": {"type": "tfp:heat", "min": 100}, "temperature": 50}""")
                .recipe("heating/d_fresh_food", """
                        {"type": "tfp:heating", "ingredient": {"type": "tfp:and", "children": [
                          {"item": "tfp:food/beef"}, {"type": "tfp:not_rotten"}]}, "temperature": 200}""")
                .recipe("heating/e_not_metal", """
                        {"type": "tfp:heating", "ingredient": {"type": "neoforge:difference",
                          "base": [{"item": "minecraft:stick"}, {"item": "minecraft:iron_ingot"}],
                          "subtracted": {"tag": "tfp:metal_items"}}, "temperature": 300}""")
                .recipe("collapse/granite", """
                        {"type": "tfp:collapse", "ingredient": ["tfp:rock/raw/granite"], "result": "tfp:rock/cobble/granite"}""")
                .recipe("knapping/rock_axe", """
                        {"type": "tfp:knapping", "knapping_type": "tfp:rock", "pattern": ["X"],
                         "result": {"id": "tfp:axe_head"}}""")
                .recipe("alloy/bronze", """
                        {"type": "tfp:alloy", "contents": [
                          {"fluid": "tfp:metal/copper", "min": 0.88, "max": 0.92},
                          {"fluid": "tfp:metal/tin", "min": 0.08, "max": 0.12}], "result": "tfp:metal/bronze"}""")
                .recipe("crafting/food_combining", """
                        {"type": "tfp:food_combining", "category": "misc"}""")
                .file("tfp/tfp/knapping_type/rock.json", """
                        {"amount_to_consume": 1, "click_sound": "tfp:item.knapping.stone",
                         "consume_after_complete": false, "has_off_texture": false,
                         "icon": {"count": 1, "id": "tfp:rock/loose/granite"},
                         "input": {"count": 2, "item": "tfp:rock/loose/granite"}, "spawns_particles": true}""")
                .load();
    }

    private RecipeIndex build(Tags tags) {
        return RecipeIndex.build(data.registries.get(),
                new TagMembers(tags.snapshot(), RecipeTestData.vanilla("minecraft:iron_ingot", "minecraft:stick")));
    }

    @Test
    void overlappingInputsResolveToTheFirstRecipeById() {
        Tags tags = data.tags(new FakeCustomItems());
        RecipeIndex idx = build(tags);
        TagLookup lookup = ItemIngredientContext.lookup(tags.snapshot());
        IngredientContext<String> cold = RecipeTestData.strings(lookup, s -> false, s -> 0);
        IngredientContext<String> hot = RecipeTestData.strings(lookup, s -> false, s -> 500);

        // Candidates in id order: the tag recipe, the item recipe, then the precise wildcard.
        assertThat(idx.byItem(RecipeTypes.HEATING, "tfp:ingot/copper")).extracting(RecipeEntry::id)
                .containsExactly("tfp:heating/a_metal", "tfp:heating/b_copper", "tfp:heating/c_hot");
        assertThat(idx.first(RecipeTypes.HEATING, "tfp:ingot/copper", cold)).map(RecipeEntry::id)
                .contains("tfp:heating/a_metal");
        assertThat(idx.first(RecipeTypes.HEATING, "minecraft:iron_ingot", cold)).map(RecipeEntry::id)
                .contains("tfp:heating/a_metal");
        // tfc:heat matches any item that is hot enough
        assertThat(idx.first(RecipeTypes.HEATING, "minecraft:dirt", cold)).isEmpty();
        assertThat(idx.first(RecipeTypes.HEATING, "minecraft:dirt", hot)).map(RecipeEntry::id)
                .contains("tfp:heating/c_hot");
        // A difference: stick yes, iron (in the subtracted tag) no.
        assertThat(idx.first(RecipeTypes.HEATING, "minecraft:stick", cold)).map(RecipeEntry::id)
                .contains("tfp:heating/e_not_metal");
        assertThat(idx.byItem(RecipeTypes.HEATING, "minecraft:stick")).extracting(RecipeEntry::id)
                .containsExactly("tfp:heating/c_hot", "tfp:heating/e_not_metal");
    }

    @Test
    void anAndWithAPreciseChildIsKeyedByItsItem() {
        Tags tags = data.tags(new FakeCustomItems());
        RecipeIndex idx = build(tags);
        TagLookup lookup = ItemIngredientContext.lookup(tags.snapshot());
        assertThat(idx.byItem(RecipeTypes.HEATING, "tfp:food/beef")).extracting(RecipeEntry::id)
                .containsExactly("tfp:heating/c_hot", "tfp:heating/d_fresh_food");
        assertThat(idx.first(RecipeTypes.HEATING, "tfp:food/beef", RecipeTestData.strings(lookup, s -> false, s -> 0)))
                .map(RecipeEntry::id).contains("tfp:heating/d_fresh_food");
        assertThat(idx.first(RecipeTypes.HEATING, "tfp:food/beef", RecipeTestData.strings(lookup, s -> true, s -> 0)))
                .isEmpty();
    }

    @Test
    void blockFluidAndStringKeySpaces() {
        Tags tags = data.tags(new FakeCustomItems());
        RecipeIndex idx = build(tags);
        TagLookup lookup = ItemIngredientContext.lookup(tags.snapshot());
        assertThat(idx.byBlock(RecipeTypes.COLLAPSE, "tfp:rock/raw/granite")).hasSize(1);
        assertThat(idx.byBlock(RecipeTypes.COLLAPSE, "tfp:rock/raw/basalt")).isEmpty();
        assertThat(idx.firstForBlock(RecipeTypes.COLLAPSE, "tfp:rock/raw/granite", lookup, r -> true))
                .map(RecipeEntry::id).contains("tfp:collapse/granite");
        assertThat(idx.byKey(RecipeTypes.KNAPPING, "tfp:rock")).extracting(RecipeEntry::id)
                .containsExactly("tfp:knapping/rock_axe");
        assertThat(idx.byKey(RecipeTypes.KNAPPING, "tfp:clay")).isEmpty();
        // Knapping without an ingredient matches any item; an alloy is found from each of its metals.
        assertThat(idx.byItem(RecipeTypes.KNAPPING, "tfp:rock/loose/granite")).hasSize(1);
        assertThat(idx.byFluid(RecipeTypes.ALLOY, "tfp:metal/tin")).hasSize(1);
        assertThat(idx.byFluid(RecipeTypes.ALLOY, "tfp:metal/gold")).isEmpty();
        // A type without item fields is a wildcard in the item space.
        assertThat(idx.byItem(RecipeTypes.FOOD_COMBINING, "minecraft:dirt")).hasSize(1);
        assertThat(idx.byItem("tfc:no_such_type", "minecraft:dirt")).isEmpty();
        assertThat(idx.size(RecipeTypes.HEATING)).isEqualTo(5);
        assertThat(idx.byId("tfp:heating/a_metal")).map(RecipeEntry::type).contains(RecipeTypes.HEATING);
    }

    @Test
    void theServiceRebuildsOffThreadWhenTheDataChangesAndServesThePreviousIndexMeanwhile() {
        Tags tags = data.tags(new FakeCustomItems());
        Queue<Runnable> pool = new ArrayDeque<>();
        Executor executor = pool::add;
        Recipes recipes = new Recipes(data.registries, tags,
                () -> RecipeTestData.vanilla("minecraft:iron_ingot", "minecraft:stick"), executor,
                err -> {
                    throw new AssertionError(err);
                }, RecipeTestData.services(new FakeCustomItems(), tags, () -> 0L,
                        () -> dev.tfp.core.food.FoodDefinitions.EMPTY));
        // First call: nothing built yet, one build queued (not run on this thread).
        assertThat(recipes.index()).isSameAs(RecipeIndex.EMPTY);
        assertThat(recipes.index()).isSameAs(RecipeIndex.EMPTY);
        assertThat(pool).hasSize(1);
        pool.poll().run();
        RecipeIndex first = recipes.index();
        assertThat(first.size(RecipeTypes.HEATING)).isEqualTo(5);
        assertThat(pool).isEmpty();

        // A data reload: the old index is served until the new one is built.
        data.recipe("heating/f_new", """
                {"type": "tfp:heating", "ingredient": {"item": "minecraft:stick"}, "temperature": 10}""").load();
        assertThat(recipes.index()).isSameAs(first);
        assertThat(pool).hasSize(1);
        pool.poll().run();
        assertThat(recipes.index().size(RecipeTypes.HEATING)).isEqualTo(6);
        assertThat(recipes.index().all(RecipeTypes.HEATING)).extracting(RecipeEntry::id)
                .isSorted().contains("tfp:heating/f_new");
        assertThat(recipes.index().first(RecipeTypes.HEATING, "minecraft:stick",
                RecipeTestData.strings(ItemIngredientContext.lookup(tags.snapshot()), s -> false, s -> 0)))
                .map(RecipeEntry::id).isEqualTo(Optional.of("tfp:heating/e_not_metal"));
    }
}
