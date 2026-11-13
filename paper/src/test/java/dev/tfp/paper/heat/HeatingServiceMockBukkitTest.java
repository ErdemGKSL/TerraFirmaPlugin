/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/item/HeatingItemTest.java @ c3b4d9536 (testCookMeat)
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.TagMembers;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PAR-HEAT-04: {@link HeatingService} on real ItemStacks: TFC's heating recipe shapes (copy_food meat, a durability
 * melt, a stacked torch recipe, a recipe with no result), and TFC's {@code HeatingItemTest.testCookMeat} on the datagen
 * output (every non-vanilla raw meat is heatable, reads back the max visible temperature and has a heating recipe).
 */
class HeatingServiceMockBukkitTest extends MockServerTest {
    private static final long ST = FoodConfig.DEFAULT.stackTicks();
    private static final long NOW = 5_000 * ST + 17;
    private static final FoodData MEAT = new FoodData(2, 0, 0, 0, 0, 0, 0, 1, 0, 1f);
    private static final Map<String, Material> BASES = Map.of(
            "tfp:food/beef", Material.BEEF, "tfp:food/cooked_beef", Material.COOKED_BEEF,
            "tfp:metal/hammer/copper", Material.IRON_PICKAXE, "tfp:torch", Material.TORCH);

    @TempDir
    Path dir;

    /** TFC's recipe files, in our namespace (heating/food/cooked_beef, metal/hammer/copper, torch_from_stick, burn_meat). */
    private HeatingTestData synthetic() {
        return new HeatingTestData(dir, BASES::get)
                .file("tfp/recipe/heating/food/cooked_beef.json", """
                        {"type": "tfp:heating",
                         "ingredient": {"type": "tfp:and", "children": [{"item": "tfp:food/beef"}, {"type": "tfp:not_rotten"}]},
                         "result_item": {"modifiers": [{"type": "tfp:copy_food"}], "stack": {"count": 1, "id": "tfp:food/cooked_beef"}},
                         "temperature": 200.0}""")
                .file("tfp/recipe/heating/metal/hammer/copper.json", """
                        {"type": "tfp:heating", "ingredient": {"item": "tfp:metal/hammer/copper"},
                         "result_fluid": {"amount": 100, "id": "tfp:metal/copper"}, "temperature": 1080.0, "use_durability": true}""")
                .file("tfp/recipe/heating/torch_from_stick.json", """
                        {"type": "tfp:heating", "ingredient": {"item": "minecraft:stick"},
                         "result_item": {"count": 2, "id": "tfp:torch"}, "temperature": 60.0}""")
                .file("tfp/recipe/heating/burn_meat.json", """
                        {"type": "tfp:heating", "ingredient": {"item": "tfp:food/cooked_beef"}, "temperature": 900.0}""")
                .load(KnownIds.permissive(Set.of("minecraft", "c")), ServerTags.NONE, () -> NOW,
                        t -> ItemHeatHooks.Capacities.UNIT,
                        () -> new FoodDefinitions(Map.of("tfp:food/beef", MEAT, "tfp:food/cooked_beef", MEAT)));
    }

    private static ItemStack hot(HeatingTestData d, String id, int count, float temperature) {
        ItemStack s = d.items.create(id, count).orElseThrow();
        HeatPdc.write(s, new HeatState(temperature, NOW));
        return s;
    }

    @Test
    void rawBeefCooksIntoCookedBeefWithTheCopiedFoodDate() {
        HeatingTestData d = synthetic();
        long date = 4_999 * ST;
        ItemStack beef = hot(d, "tfp:food/beef", 1, 210f);
        FoodPdc.write(beef, new FoodState(date, List.of(FoodTrait.SALTED)));

        assertThat(d.service.recipe(beef)).hasValueSatisfying(r -> assertThat(r.temperature()).isEqualTo(200f));
        assertThat(d.service.convertIfReady(beef, 199.9f)).as("below the recipe temperature").isEmpty();

        HeatingService.Conversion c = d.service.convertIfReady(beef, 200f).orElseThrow();
        assertThat(d.items.idOf(c.item())).contains("tfp:food/cooked_beef");
        assertThat(c.item().getAmount()).isEqualTo(1);
        assertThat(FoodPdc.read(c.item())).as("copy_food keeps the raw beef's date and traits")
                .contains(new FoodState(date, List.of(FoodTrait.SALTED)));
        assertThat(d.heat.temperature(c.item())).as("the result keeps the input's temperature").isEqualTo(210f);
        assertThat(c.fluid()).isEmpty();
        assertThat(c.destroysInput()).isFalse();
        assertThat(d.items.idOf(beef)).as("the input is not modified").contains("tfp:food/beef");

        // At its own temperature (210 >= 200)
        assertThat(d.service.convertIfReady(beef)).isPresent();
        assertThat(d.service.convertIfReady(hot(d, "tfp:food/beef", 1, 150f))).isEmpty();
    }

    @Test
    void rottenMeatHasNoHeatingRecipe() {
        HeatingTestData d = synthetic();
        ItemStack rotten = hot(d, "tfp:food/beef", 1, 500f);
        FoodPdc.write(rotten, new FoodState(FoodDecay.ROTTEN_FLAG, List.of()));
        assertThat(d.service.recipe(rotten)).isEmpty();
        assertThat(d.service.convertIfReady(rotten, 500f)).isEmpty();
    }

    @Test
    void toolsMeltIntoLessMetalWhenWorn() {
        HeatingTestData d = synthetic();
        ItemStack hammer = hot(d, "tfp:metal/hammer/copper", 1, 1100f);
        assertThat(d.service.convertIfReady(hammer, 1100f).orElseThrow().fluid())
                .contains(new FluidAmount("tfp:metal/copper", 100));

        Damageable meta = (Damageable) hammer.getItemMeta();
        int max = Material.IRON_PICKAXE.getMaxDurability();
        meta.setDamage(100);
        hammer.setItemMeta(meta);
        HeatingService.Conversion c = d.service.convertIfReady(hammer, 1100f).orElseThrow();
        assertThat(c.fluid()).contains(new FluidAmount("tfp:metal/copper",
                (int) Math.floor(100 * (1 - 100f / max))));
        assertThat(c.item().isEmpty()).isTrue();
        assertThat(c.destroysInput()).isFalse();
    }

    @Test
    void stackedConversionCapsTheCountAndRecipesWithoutResultDestroyTheInput() {
        HeatingTestData d = synthetic();
        ItemStack sticks = new ItemStack(Material.STICK, 40);
        HeatingService.Conversion single = d.service.convertIfReady(sticks, 60f).orElseThrow();
        assertThat(single.item().getAmount()).as("assembleItem: one input item").isEqualTo(2);
        assertThat(d.service.convertStackedIfReady(sticks, 60f, 64).orElseThrow().item().getAmount())
                .as("80 torches capped at the max stack size").isEqualTo(64);
        assertThat(d.service.convertStackedIfReady(sticks, 60f, 16).orElseThrow().item().getAmount()).isEqualTo(16);
        assertThat(d.service.convertStackedIfReady(new ItemStack(Material.STICK, 3), 60f, 64).orElseThrow().item()
                .getAmount()).isEqualTo(6);
        assertThat(d.service.convertStackedIfReady(sticks, 59f, 64)).isEmpty();

        ItemStack cooked = hot(d, "tfp:food/cooked_beef", 1, 950f);
        assertThat(d.service.convertIfReady(cooked, 950f).orElseThrow().destroysInput()).isTrue();
        assertThat(d.service.recipe(new ItemStack(Material.DIRT))).isEmpty();
        assertThat(d.service.convertIfReady(ItemStack.empty(), 2000f)).isEmpty();
    }

    @Test
    void moduleIsListedInTheServiceFile() throws Exception {
        String services = new String(HeatingModule.class.getClassLoader()
                .getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(services.lines()).contains(HeatingModule.class.getName());
        assertThat(new HeatingModule().id()).isEqualTo("heating_recipes");
    }

    /**
     * TFC {@code HeatingItemTest.testCookMeat}, in full on the datagen output: every item of {@code #c:foods/raw_meat}
     * (TFC {@code RAW_MEATS}) outside the minecraft namespace has a heat capability, is heated to
     * {@code Heat.maxVisibleTemperature()} and reads it back, and has a heating recipe.
     */
    @Test
    void testCookMeatOnTheContentData() throws java.io.IOException {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        HeatingTestData d = new HeatingTestData(root, id -> id.startsWith("tfp:") ? Material.PAPER : null)
                .load(KnownIds.fromDataRoot(root), ServerTags.BUKKIT, () -> NOW,
                        HeatingTestData::definitionCapacities, () -> FoodDefinitions.EMPTY);
        Set<String> meats = new TreeSet<>(new TagMembers(d.tags.snapshot(), RecipeModule.vanillaIds())
                .members(TagType.ITEM, "c:foods/raw_meat"));
        meats.removeIf(id -> id.startsWith("minecraft:"));
        assertThat(meats).as("TFC's non-vanilla raw meats").hasSizeGreaterThan(30).contains("tfp:food/beef");
        ItemHeatDefinitions definitions = ItemHeatDefinitions.fromRegistry(d.registries.get());
        for (String id : meats) {
            ItemStack stack = d.items.create(id, 1).orElseThrow();
            assertThat(definitions.heatCapacity(id)).as("Meat not heatable: " + id).isPositive();
            HeatPdc.write(stack, HeatState.cold(NOW).set(HeatLevel.MAX_VISIBLE, NOW));
            assertThat(d.heat.temperature(stack)).as("Heat not achieved for stack: " + id)
                    .isEqualTo(HeatLevel.MAX_VISIBLE);
            assertThat(d.service.recipe(stack)).as("No heating recipe found for meat: " + id).isPresent();
            assertThat(d.service.convertIfReady(stack)).as("cooks (or burns) at max visible: " + id).isPresent();
        }

        // The same check as the in-server self-test runs it.
        HeatingSelfCheck.Result result = HeatingSelfCheck.run(new HeatingSelfCheck.Services(d.service, d.heat,
                definitions, d.items, d.tags.snapshot(), RecipeModule.vanillaIds(), () -> NOW));
        assertThat(result.problems()).isEmpty();
        assertThat(result.passed()).isTrue();
        assertThat(result.meats()).containsExactlyElementsOf(meats);

        // A missing recipe is reported with TFC's message.
        HeatingTestData empty = new HeatingTestData(dir, id -> id.startsWith("tfp:") ? Material.PAPER : null)
                .file("tfp/tags/item/x.json", "{\"values\": []}")
                .load(KnownIds.permissive(Set.of("minecraft", "c")), ServerTags.BUKKIT, () -> NOW,
                        t -> ItemHeatHooks.Capacities.UNIT, () -> FoodDefinitions.EMPTY);
        HeatingSelfCheck.Result failed = HeatingSelfCheck.run(new HeatingSelfCheck.Services(empty.service, empty.heat,
                definitions, empty.items, d.tags.snapshot(), RecipeModule.vanillaIds(), () -> NOW));
        assertThat(failed.passed()).isFalse();
        assertThat(failed.problems()).contains("No heating recipe found for meat: tfp:food/beef");
    }
}
