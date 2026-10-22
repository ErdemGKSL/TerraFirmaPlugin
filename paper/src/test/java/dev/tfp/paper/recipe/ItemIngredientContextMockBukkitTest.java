/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-06: every TFC ingredient type tested against real ItemStacks (CE ids, vanilla ids, PDC food/heat/fluid). */
class ItemIngredientContextMockBukkitTest extends MockServerTest {
    private static final long NOW = 10_000_000L;

    @TempDir
    Path dir;

    private RecipeTestData data;
    private RecipeTestData.PdcItems items;
    private Tags tags;
    private ItemIngredientContext ctx;

    @BeforeEach
    void setUp() {
        data = new RecipeTestData(dir)
                .itemTag("metal_items", "tfp:ingot/copper", "minecraft:iron_ingot")
                .recipe("heating/copper", """
                        {"type": "tfp:heating", "ingredient": {"type": "tfp:and", "children": [
                          {"item": "tfp:ingot/copper"}, {"type": "tfp:heat", "min": 400}]}, "temperature": 1080}""")
                .load();
        items = new RecipeTestData.PdcItems(Map.of("tfp:ingot/copper", Material.BRICK, "tfp:food/beef", Material.BEEF,
                "tfp:jug", Material.PAPER));
        tags = data.tags(items);
        ItemFoodHooks food = new ItemFoodHooks(() -> NOW, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        ItemHeatHooks heat = new ItemHeatHooks(() -> NOW, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        ctx = new ItemIngredientContext(tags::itemId, tags.snapshot(), food, heat::temperature);
    }

    private ItemStack custom(String id) {
        return items.create(id, 1).orElseThrow();
    }

    @Test
    void itemAndTagIngredientsUseTheCraftEngineIdOrTheVanillaKey() {
        ItemStack copper = custom("tfp:ingot/copper");
        ItemStack brick = new ItemStack(Material.BRICK);
        assertThat(ctx.itemId(copper)).isEqualTo("tfp:ingot/copper");
        assertThat(ctx.itemId(brick)).isEqualTo("minecraft:brick");
        assertThat(ctx.itemId(ItemStack.empty())).isEqualTo(ItemIngredientContext.AIR);

        assertThat(new Ingredient.Item("tfp:ingot/copper").test(copper, ctx)).isTrue();
        assertThat(new Ingredient.Item("tfp:ingot/copper").test(brick, ctx)).as("same base material, not the CE item")
                .isFalse();
        assertThat(new Ingredient.Tag("tfp:metal_items").test(copper, ctx)).isTrue();
        assertThat(new Ingredient.Tag("tfc:metal_items").test(new ItemStack(Material.IRON_INGOT), ctx))
                .as("TFC's tag name accepted").isTrue();
        assertThat(new Ingredient.Tag("tfp:metal_items").test(brick, ctx)).isFalse();
        assertThat(new Ingredient.Difference(new Ingredient.AnyOf(List.of(new Ingredient.Item("minecraft:brick"),
                new Ingredient.Item("tfp:ingot/copper"))), new Ingredient.Tag("tfp:metal_items")).test(brick, ctx))
                .isTrue();
    }

    @Test
    void notRottenRottenAndTraitsReadTheFoodRecord() {
        ItemStack fresh = custom("tfp:food/beef");
        FoodPdc.write(fresh, FoodState.createdAt(NOW, FoodConfig.DEFAULT));
        ItemStack rotten = custom("tfp:food/beef");
        FoodPdc.write(rotten, new FoodState(1L, List.of()));
        ItemStack salted = custom("tfp:food/beef");
        FoodPdc.write(salted, new FoodState(FoodState.createdAt(NOW, FoodConfig.DEFAULT).creationDate(),
                List.of(FoodTrait.SALTED)));

        assertThat(Ingredient.NotRotten.INSTANCE.test(fresh, ctx)).isTrue();
        assertThat(Ingredient.NotRotten.INSTANCE.test(rotten, ctx)).isFalse();
        assertThat(Ingredient.Rotten.INSTANCE.test(rotten, ctx)).isTrue();
        assertThat(Ingredient.NotRotten.INSTANCE.test(new ItemStack(Material.STICK), ctx))
                .as("a non-food is never rotten").isTrue();
        assertThat(new Ingredient.HasTrait("tfc:salted").test(salted, ctx)).isTrue();
        assertThat(new Ingredient.HasTrait("tfp:salted").test(fresh, ctx)).isFalse();
        assertThat(new Ingredient.LacksTrait("tfp:salted").test(fresh, ctx)).isTrue();
        assertThat(new Ingredient.And(List.of(new Ingredient.Item("tfp:food/beef"), Ingredient.NotRotten.INSTANCE))
                .test(rotten, ctx)).isFalse();
    }

    @Test
    void heatReadsTheLazyTemperature() {
        ItemStack hot = custom("tfp:ingot/copper");
        HeatPdc.write(hot, new HeatState(500f, NOW));
        ItemStack cold = custom("tfp:ingot/copper");
        assertThat(new Ingredient.Heat(400f, Float.POSITIVE_INFINITY).test(hot, ctx)).isTrue();
        assertThat(new Ingredient.Heat(600f, Float.POSITIVE_INFINITY).test(hot, ctx)).isFalse();
        assertThat(new Ingredient.Heat(Float.NEGATIVE_INFINITY, 100f).test(cold, ctx)).isTrue();

        RecipeIndex idx = RecipeIndex.build(data.registries.get(),
                new TagMembers(tags.snapshot(), RecipeTestData.vanilla()));
        assertThat(idx.first(RecipeTypes.HEATING, hot, ctx)).map(RecipeEntry::id).contains("tfp:heating/copper");
        assertThat(idx.first(RecipeTypes.HEATING, cold, ctx)).isEmpty();
    }

    @Test
    void fluidContentReadsTheItemsFluid() {
        ItemStack jug = custom("tfp:jug");
        assertThat(ItemFluids.fill(jug, new FluidAmount("minecraft:water", 500), 1000, true)).isEqualTo(500);
        ItemStack empty = custom("tfp:jug");
        Ingredient water100 = new Ingredient.FluidContent(
                new SizedFluidIngredient(new FluidIngredient.Single("minecraft:water"), 100));
        Ingredient water1000 = new Ingredient.FluidContent(
                new SizedFluidIngredient(new FluidIngredient.Single("minecraft:water"), 1000));
        assertThat(water100.test(jug, ctx)).isTrue();
        assertThat(water1000.test(jug, ctx)).isFalse();
        assertThat(water100.test(empty, ctx)).isFalse();
        assertThat(ItemIngredientContext.containedFluid(jug))
                .contains(new dev.tfp.core.recipe.fluid.FluidAmount("minecraft:water", 500));
    }
}
