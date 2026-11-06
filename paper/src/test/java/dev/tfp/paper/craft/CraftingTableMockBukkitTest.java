/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.craft;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.recipe.ModifierExtensions;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PAR-PROC-03 on MockBukkit stacks: TFC's advanced recipes (a knife is damaged, mirrored shaped input), plain recipes with
 * a fluid container, food combining, casting crafting, applying a craft to the grid and the vanilla-recipe guard.
 */
class CraftingTableMockBukkitTest extends MockServerTest {
    private static final long ST = FoodConfig.DEFAULT.stackTicks();
    private static final long NOW = 5_000 * ST + 17;
    private static final FoodData BEEF = new FoodData(2, 0, 0, 0, 0, 0, 0, 1, 0, 1f);

    @TempDir
    Path dir;

    private CraftTestData.PdcItems items;
    private CraftingTable table;
    private final AtomicReference<Double> roll = new AtomicReference<>(0.5);

    @BeforeEach
    void setUp() {
        CraftTestData data = new CraftTestData(dir)
                // A knife cuts a log into planks; the knife is damaged (TFC's damage_crafting_remainder).
                .recipe("crafting/planks_by_knife", """
                        {"type": "tfp:advanced_shapeless_crafting",
                         "ingredients": [{"item": "tfp:log"}, {"item": "tfp:knife"}],
                         "primary_ingredient": {"item": "tfp:knife"},
                         "remainder": {"modifiers": [{"type": "tfp:damage_crafting_remainder"}]},
                         "result": {"count": 2, "id": "tfp:plank"}}""")
                // Shaped: the input cell is the knife at row 0, column 1; the result copies nothing.
                .recipe("crafting/sticks_by_knife", """
                        {"type": "tfp:advanced_shaped_crafting",
                         "pattern": ["PK"], "key": {"P": {"item": "tfp:plank"}, "K": {"item": "tfp:knife"}},
                         "input_row": 0, "input_column": 1,
                         "remainder": {"modifiers": [{"type": "tfp:damage_crafting_remainder"}]},
                         "result": {"count": 3, "id": "minecraft:stick"}}""")
                // Plain shapeless with a fluid container (routed from PAR-PROC-02).
                .recipe("crafting/clay", """
                        {"type": "minecraft:crafting_shapeless",
                         "ingredients": [{"item": "tfp:hardened_clay"},
                           {"type": "tfp:fluid_content", "fluid": {"amount": 1000, "fluid": "minecraft:water"}}],
                         "result": {"count": 8, "id": "minecraft:clay_ball"}}""")
                .recipe("crafting/food_combining", "{\"type\": \"tfp:food_combining\"}")
                .recipe("crafting/casting", "{\"type\": \"tfp:casting_crafting\"}")
                .recipe("casting/copper_axe_head", """
                        {"type": "tfp:casting", "mold": {"item": "tfp:ceramic/axe_head_mold"},
                         "fluid": {"amount": 100, "fluid": "tfp:metal/copper"},
                         "result": {"count": 1, "id": "tfp:metal/axe_head/copper"}, "break_chance": 0.3}""")
                // A fluid with no fluid heat entry: TFC's FluidHeat.getOrUnknown gives the unknown metal's melt point.
                .recipe("casting/mystery_axe_head", """
                        {"type": "tfp:casting", "mold": {"item": "tfp:ceramic/axe_head_mold"},
                         "fluid": {"amount": 100, "fluid": "tfp:mystery_fluid"},
                         "result": {"count": 1, "id": "tfp:metal/axe_head/copper"}, "break_chance": 0.3}""")
                // A TFC override of a vanilla recipe, kept under its minecraft: id.
                .file("minecraft/recipe/bread.json", """
                        {"type": "minecraft:crafting_shapeless",
                         "ingredients": [{"item": "tfp:dough"}],
                         "result": {"count": 1, "id": "minecraft:bread"}}""")
                // TFC extends #minecraft:planks with its planks (tfp:plank above is not a member).
                .file("minecraft/tags/item/planks.json", "{\"values\": [\"tfp:wood/planks/oak\"]}")
                .load();
        items = new CraftTestData.PdcItems(Map.ofEntries(
                Map.entry("tfp:log", Material.OAK_LOG), Map.entry("tfp:knife", Material.IRON_SWORD),
                Map.entry("tfp:plank", Material.OAK_PLANKS), Map.entry("tfp:hardened_clay", Material.TERRACOTTA),
                Map.entry("tfp:wooden_bucket", Material.BUCKET), Map.entry("tfp:food/beef", Material.BEEF),
                Map.entry("tfp:ceramic/axe_head_mold", Material.BRICK),
                Map.entry("tfp:metal/axe_head/copper", Material.IRON_NUGGET), Map.entry("tfp:dough", Material.PAPER),
                Map.entry("tfp:wood/planks/oak", Material.OAK_PLANKS)));
        table = data.table(items, () -> new FoodDefinitions(Map.of("tfp:food/beef", BEEF)), () -> NOW, roll::get);
        table.recipes().extend(ModifierExtensions.Kind.CRAFTING_REMAINDER, CraftModule.fluidContainerRemainder(items));
    }

    private ItemStack item(String id, int count) {
        return items.create(id, count).orElseThrow();
    }

    private static int damage(ItemStack s) {
        return ((Damageable) s.getItemMeta()).getDamage();
    }

    @Test
    void craftingWithAKnifeDamagesTheKnife() {
        List<ItemStack> grid = Arrays.asList(item("tfp:log", 3), null, null, null, item("tfp:knife", 1), null, null, null,
                null);
        CraftingTable.Match m = table.find(grid, 3).orElseThrow();
        assertThat(m.recipe().id()).isEqualTo("tfp:crafting/planks_by_knife");
        assertThat(m.primarySlot()).isEqualTo(4);
        assertThat(items.idOf(table.preview(m))).contains("tfp:plank");
        assertThat(table.preview(m).getAmount()).isEqualTo(2);

        CraftingTable.Craft craft = table.craft(m);
        assertThat(craft.output().getAmount()).isEqualTo(2);
        assertThat(craft.remainders().get(0)).as("the log has no remainder").isNull();
        ItemStack knife = craft.remainders().get(4);
        assertThat(items.idOf(knife)).contains("tfp:knife");
        assertThat(damage(knife)).isEqualTo(1);

        List<ItemStack> after = CraftListener.consume(grid, craft.remainders(), s -> {
            throw new AssertionError("nothing overflows");
        });
        assertThat(after.get(0).getAmount()).isEqualTo(2);
        assertThat(damage(after.get(4))).as("the damaged knife stays in its slot").isEqualTo(1);
        assertThat(grid.get(4).getItemMeta()).as("the grid stack itself is untouched").matches(meta ->
                ((Damageable) meta).getDamage() == 0);

        // The craft again with the damaged knife: damage accumulates; a knife at its last point breaks.
        CraftingTable.Craft second = table.craft(table.find(after, 3).orElseThrow());
        assertThat(damage(second.remainders().get(4))).isEqualTo(2);
        ItemStack worn = item("tfp:knife", 1);
        Damageable meta = (Damageable) worn.getItemMeta();
        meta.setDamage(Material.IRON_SWORD.getMaxDurability() - 1);
        worn.setItemMeta(meta);
        CraftingTable.Craft breaks = table.craft(table.find(Arrays.asList(item("tfp:log", 1), worn, null, null), 2)
                .orElseThrow());
        assertThat(breaks.remainders().get(1)).as("the knife breaks").isNull();
    }

    @Test
    void shapedInputCellIsFoundMirrored() {
        // Written order P K, so the knife (input cell) is in slot 1.
        CraftingTable.Match plain = table.find(Arrays.asList(item("tfp:plank", 1), item("tfp:knife", 1), null, null), 2)
                .orElseThrow();
        assertThat(plain.recipe().id()).isEqualTo("tfp:crafting/sticks_by_knife");
        assertThat(plain.primarySlot()).isEqualTo(1);
        // Mirrored K P in the lower row of a 3x3 grid.
        List<ItemStack> grid = Arrays.asList(null, null, null, null, null, null, item("tfp:knife", 1),
                item("tfp:plank", 1), null);
        CraftingTable.Match mirrored = table.find(grid, 3).orElseThrow();
        assertThat(mirrored.primarySlot()).isEqualTo(6);
        CraftingTable.Craft craft = table.craft(mirrored);
        assertThat(craft.output().getType()).isEqualTo(Material.STICK);
        assertThat(craft.output().getAmount()).isEqualTo(3);
        assertThat(damage(craft.remainders().get(6))).isEqualTo(1);
        assertThat(craft.remainders().get(7)).isNull();
        // An extra item breaks the shape.
        assertThat(table.find(Arrays.asList(item("tfp:plank", 1), item("tfp:knife", 1), item("tfp:plank", 1), null), 2)
                .map(m -> m.recipe().id())).isEmpty();
    }

    @Test
    void aFilledFluidContainerMatchesAndLeavesAnEmptyOne() {
        ItemStack bucket = item("tfp:wooden_bucket", 1);
        FluidPdc.write(bucket, new FluidAmount("minecraft:water", 1000));
        CraftingTable.Match m = table.find(Arrays.asList(item("tfp:hardened_clay", 1), bucket, null, null), 2)
                .orElseThrow();
        assertThat(m.recipe().id()).isEqualTo("tfp:crafting/clay");
        CraftingTable.Craft craft = table.craft(m);
        assertThat(craft.output().getType()).isEqualTo(Material.CLAY_BALL);
        assertThat(craft.output().getAmount()).isEqualTo(8);
        ItemStack left = craft.remainders().get(1);
        assertThat(items.idOf(left)).contains("tfp:wooden_bucket");
        assertThat(ItemFluids.content(left).isEmpty()).isTrue();

        ItemStack half = item("tfp:wooden_bucket", 1);
        FluidPdc.write(half, new FluidAmount("minecraft:water", 500));
        assertThat(table.find(Arrays.asList(item("tfp:hardened_clay", 1), half, null, null), 2)).isEmpty();
    }

    @Test
    void foodCombiningMergesStacksToTheOldestDate() {
        ItemStack young = item("tfp:food/beef", 1);
        FoodPdc.write(young, new FoodState(4_000 * ST, List.of()));
        ItemStack old = item("tfp:food/beef", 1);
        FoodPdc.write(old, new FoodState(3_000 * ST, List.of()));
        List<ItemStack> grid = Arrays.asList(young, null, old, item("tfp:food/beef", 1));
        CraftingTable.Match m = table.find(grid, 2).orElseThrow();
        assertThat(m.recipe().id()).isEqualTo("tfp:crafting/food_combining");
        CraftingTable.Craft craft = table.craft(m);
        assertThat(craft.output().getAmount()).isEqualTo(3);
        assertThat(FoodPdc.read(craft.output()).orElseThrow().creationDate()).isEqualTo(3_000 * ST);
        assertThat(craft.remainders()).allMatch(r -> r == null);

        ItemStack salted = item("tfp:food/beef", 1);
        FoodPdc.write(salted, new FoodState(3_000 * ST, List.of(FoodTrait.SALTED)));
        assertThat(table.find(Arrays.asList(young, salted, null, null), 2)).as("traits differ").isEmpty();
        assertThat(table.find(Arrays.asList(young, null, null, null), 2)).as("one food is not a combination").isEmpty();
        assertThat(table.find(Arrays.asList(young, item("tfp:log", 1), null, null), 2)).isEmpty();
    }

    @Test
    void castingCraftingTakesTheCastAndMayKeepTheMold() {
        ItemStack mold = item("tfp:ceramic/axe_head_mold", 1);
        FluidPdc.write(mold, new FluidAmount("tfp:metal/copper", 100));
        HeatPdc.write(mold, new HeatState(500f, NOW));
        List<ItemStack> grid = Arrays.asList(null, mold, null, null);
        CraftingTable.Match m = table.find(grid, 2).orElseThrow();
        assertThat(m.recipe().id()).isEqualTo("tfp:crafting/casting");
        roll.set(0.5);
        CraftingTable.Craft kept = table.craft(m);
        assertThat(items.idOf(kept.output())).contains("tfp:metal/axe_head/copper");
        assertThat(HeatPdc.read(kept.output()).orElseThrow().temperature()).as("the cast keeps the mold's heat")
                .isEqualTo(500f);
        assertThat(items.idOf(kept.remainders().get(1))).contains("tfp:ceramic/axe_head_mold");
        assertThat(ItemFluids.content(kept.remainders().get(1)).isEmpty()).as("the kept mold is drained").isTrue();
        roll.set(0.3);
        assertThat(table.craft(m).remainders().get(1)).as("a roll at the break chance breaks it").isNull();

        ItemStack molten = mold.clone();
        HeatPdc.write(molten, new HeatState(1200f, NOW));
        assertThat(table.find(Arrays.asList(molten, null, null, null), 2)).as("molten metal").isEmpty();
        ItemStack emptyMold = item("tfp:ceramic/axe_head_mold", 1);
        assertThat(table.find(Arrays.asList(emptyMold, null, null, null), 2)).isEmpty();
        assertThat(table.find(Arrays.asList(mold, item("tfp:log", 1), null, null), 2)).as("one stack only").isEmpty();
    }

    @Test
    void aFluidWithoutHeatDataIsMoltenAtTheUnknownMetalsMeltTemperature() {
        assertThat(CrucibleRules.meltTemperature("tfp:mystery_fluid")).isEmpty();
        float melt = CrucibleRules.UNKNOWN_MELT_TEMPERATURE;
        ItemStack below = item("tfp:ceramic/axe_head_mold", 1);
        FluidPdc.write(below, new FluidAmount("tfp:mystery_fluid", 100));
        HeatPdc.write(below, new HeatState(melt - 1f, NOW));
        CraftingTable.Match m = table.find(Arrays.asList(below, null, null, null), 2).orElseThrow();
        assertThat(m.recipe().id()).isEqualTo("tfp:crafting/casting");
        ItemStack at = below.clone();
        HeatPdc.write(at, new HeatState(melt, NOW));
        assertThat(table.find(Arrays.asList(at, null, null, null), 2)).as("molten at the melt temperature (>=)")
                .isEmpty();
        ItemStack above = below.clone();
        HeatPdc.write(above, new HeatState(melt + 50f, NOW));
        assertThat(table.find(Arrays.asList(above, null, null, null), 2)).as("molten above it").isEmpty();
    }

    @Test
    void theGuardReplacesVanillaRecipesThatTfcOverridesRemovesOrThatHoldOurItems() {
        CraftListener listener = new CraftListener(table, items);
        Recipe bread = new ShapelessRecipe(NamespacedKey.minecraft("bread"), new ItemStack(Material.BREAD))
                .addIngredient(Material.PAPER);
        List<ItemStack> dough = Arrays.asList(item("tfp:dough", 1), null, null, null);
        CraftListener.Decision d = listener.decide(bread, dough, 2);
        assertThat(d.keepServer()).isFalse();
        assertThat(d.match().orElseThrow().recipe().id()).as("TFC's override under the vanilla id")
                .isEqualTo("minecraft:bread");

        // Our item on a vanilla base in a vanilla recipe: blocked (no TFC recipe for it).
        Recipe paperRecipe = new ShapelessRecipe(NamespacedKey.minecraft("book"), new ItemStack(Material.BOOK))
                .addIngredient(Material.OAK_PLANKS);
        CraftListener.Decision leak = listener.decide(paperRecipe, Arrays.asList(item("tfp:plank", 1), null, null, null),
                2);
        assertThat(leak.keepServer()).isFalse();
        assertThat(leak.match()).isEmpty();
        assertThat(listener.blocked()).isEqualTo(2);

        // A vanilla recipe TFC keeps, on vanilla items: the server's match stands; so does any other plugin's recipe.
        assertThat(listener.decide(paperRecipe, Arrays.asList(new ItemStack(Material.OAK_PLANKS), null, null, null), 2)
                .keepServer()).isTrue();
        Recipe ce = new ShapelessRecipe(new NamespacedKey("tfp", "crafting/thing"), new ItemStack(Material.STICK))
                .addIngredient(Material.OAK_LOG);
        assertThat(listener.decide(ce, Arrays.asList(item("tfp:log", 1), null, null, null), 2).keepServer()).isTrue();
        // No server match: our runtime decides.
        assertThat(listener.decide(null, Arrays.asList(item("tfp:log", 1), item("tfp:knife", 1), null, null), 2)
                .match().map(m -> m.recipe().id())).contains("tfp:crafting/planks_by_knife");
    }

    @Test
    void tfcPlanksStillMakeTheVanillaRecipesTfcKeepsThroughTheTag() {
        CraftListener listener = new CraftListener(table, items);
        Recipe stick = new ShapedRecipe(NamespacedKey.minecraft("stick"), new ItemStack(Material.STICK, 4))
                .shape("#", "#").setIngredient('#', Material.OAK_PLANKS);
        List<ItemStack> tfc = Arrays.asList(item("tfp:wood/planks/oak", 1), null, item("tfp:wood/planks/oak", 1), null);
        assertThat(listener.decide(stick, tfc, 2).keepServer()).as("members of #minecraft:planks: the server's stick")
                .isTrue();
        // No server match (a base material outside vanilla #planks): our runtime makes the same vanilla recipe.
        CraftingTable.Match m = listener.decide(null, tfc, 2).match().orElseThrow();
        assertThat(m.recipe().id()).isEqualTo("minecraft:stick");
        ItemStack sticks = table.craft(m).output();
        assertThat(sticks.getType()).isEqualTo(Material.STICK);
        assertThat(sticks.getAmount()).isEqualTo(4);
        // A vanilla oak plank (a server member of #minecraft:planks) mixed with a TFC plank: still sticks, as in TFC.
        List<ItemStack> mixed = Arrays.asList(new ItemStack(Material.OAK_PLANKS), null, item("tfp:wood/planks/oak", 1),
                null);
        assertThat(listener.decide(stick, mixed, 2).keepServer()).isTrue();
        assertThat(listener.decide(null, mixed, 2).match().map(x -> x.recipe().id())).contains("minecraft:stick");
        // Our item on an oak_planks base that is not in #minecraft:planks: leakage, blocked.
        List<ItemStack> leak = Arrays.asList(item("tfp:plank", 1), null, item("tfp:plank", 1), null);
        CraftListener.Decision blocked = listener.decide(stick, leak, 2);
        assertThat(blocked.keepServer()).isFalse();
        assertThat(blocked.match()).isEmpty();
        assertThat(listener.decide(null, leak, 2).match()).isEmpty();
        assertThat(listener.blocked()).isEqualTo(1);
    }

    @Test
    void theCrafterBlockGetsTheSameGuard() {
        CraftListener listener = new CraftListener(table, items);
        Recipe stick = new ShapedRecipe(NamespacedKey.minecraft("stick"), new ItemStack(Material.STICK, 4))
                .shape("#", "#").setIngredient('#', Material.OAK_PLANKS);
        assertThat(listener.blocksCrafter(stick, Arrays.asList(item("tfp:wood/planks/oak", 1), null, null,
                item("tfp:wood/planks/oak", 1), null, null, null, null, null))).isFalse();
        assertThat(listener.blocksCrafter(stick, Arrays.asList(new ItemStack(Material.OAK_PLANKS), null, null,
                new ItemStack(Material.OAK_PLANKS), null, null, null, null, null))).isFalse();
        assertThat(listener.blocksCrafter(stick, Arrays.asList(item("tfp:plank", 1), null, null, item("tfp:plank", 1),
                null, null, null, null, null))).as("leakage").isTrue();
        Recipe bread = new ShapelessRecipe(NamespacedKey.minecraft("bread"), new ItemStack(Material.BREAD))
                .addIngredient(Material.WHEAT);
        assertThat(listener.blocksCrafter(bread, Arrays.asList(new ItemStack(Material.WHEAT), null, null, null, null,
                null, null, null, null))).as("a vanilla recipe TFC overrides").isTrue();
        Recipe ce = new ShapelessRecipe(new NamespacedKey("tfp", "crafting/thing"), new ItemStack(Material.STICK))
                .addIngredient(Material.OAK_LOG);
        assertThat(listener.blocksCrafter(ce, Arrays.asList(item("tfp:log", 1), null, null, null, null, null, null, null,
                null))).isFalse();
    }

    @Test
    void consumingPutsRemaindersBackOrGivesThemToThePlayer() {
        List<ItemStack> overflow = new ArrayList<>();
        List<ItemStack> grid = Arrays.asList(new ItemStack(Material.STICK, 2), new ItemStack(Material.OAK_LOG, 1),
                null);
        List<ItemStack> rem = Arrays.asList(new ItemStack(Material.BOWL), new ItemStack(Material.STICK), null);
        List<ItemStack> after = CraftListener.consume(grid, rem, overflow::add);
        assertThat(after.get(0).getAmount()).as("one stick taken").isEqualTo(1);
        assertThat(overflow).extracting(ItemStack::getType).containsExactly(Material.BOWL);
        assertThat(after.get(1).getType()).as("the emptied slot takes the remainder").isEqualTo(Material.STICK);
        assertThat(after.get(2)).isNull();
        assertThat(Bukkit.getServer()).isNotNull();
    }
}
