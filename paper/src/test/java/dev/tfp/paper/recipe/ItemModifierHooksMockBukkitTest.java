/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-06: TFC's output modifiers applied to real ItemStacks (food and heat PDC records, vanilla components). */
class ItemModifierHooksMockBukkitTest extends MockServerTest {
    private static final long ST = FoodConfig.DEFAULT.stackTicks();
    private static final long NOW = 5_000 * ST + 17;
    private static final FoodData BEEF = new FoodData(2, 0, 0, 0, 0, 0, 0, 1, 0, 1f);

    @TempDir
    Path dir;

    private RecipeTestData.PdcItems items;
    private Tags tags;
    private final AtomicReference<Double> roll = new AtomicReference<>(0.0);
    private ItemModifierHooks hooks;
    private ItemModifierHooks.Services services;

    @BeforeEach
    void setUp() {
        RecipeTestData data = new RecipeTestData(dir)
                .file("minecraft/tags/item/dyeable.json", "{\"values\": [\"minecraft:leather_chestplate\"]}")
                .load();
        items = new RecipeTestData.PdcItems(Map.of("tfp:food/beef", Material.BEEF, "tfp:food/cooked_beef",
                Material.COOKED_BEEF, "tfp:ingot/copper", Material.BRICK, "tfp:rod", Material.STICK));
        tags = data.tags(items);
        services = RecipeTestData.services(items, tags, () -> NOW,
                () -> new FoodDefinitions(Map.of("tfp:food/beef", BEEF, "tfp:food/cooked_beef", BEEF)));
        hooks = new ItemModifierHooks(services, ItemIngredientContext.lookup(tags.snapshot()), ModifierExtensions.NONE,
                roll::get);
    }

    private ItemStack food(String id, long date, FoodTrait... traits) {
        ItemStack s = items.create(id, 1).orElseThrow();
        FoodPdc.write(s, new FoodState(date, List.of(traits)));
        return s;
    }

    private static ItemStackProvider provider(String id, int count, ItemStackModifier... modifiers) {
        return new ItemStackProvider(id == null ? Optional.empty()
                : Optional.of(new ItemStackTemplate(id, count, Optional.empty())), List.of(modifiers));
    }

    @Test
    void createMakesCraftEngineOrVanillaItemsAndDatesNewFood() {
        ItemStack beef = hooks.create(new ItemStackTemplate("tfp:food/beef", 2, Optional.empty()));
        assertThat(items.idOf(beef)).contains("tfp:food/beef");
        assertThat(beef.getAmount()).isEqualTo(2);
        assertThat(FoodPdc.read(beef)).contains(FoodState.createdAt(NOW, FoodConfig.DEFAULT));
        ItemStack sticks = hooks.create(new ItemStackTemplate("minecraft:stick", 3, Optional.empty()));
        assertThat(sticks.getType()).isEqualTo(Material.STICK);
        assertThat(FoodPdc.has(sticks)).isFalse();
        assertThat(hooks.create(new ItemStackTemplate("tfp:unknown", 1, Optional.empty())).isEmpty()).isTrue();
    }

    @Test
    void theProviderMultipliesByTheInputCountThenAppliesModifiersInOrder() {
        ItemStack input = new ItemStack(Material.OAK_LOG, 3);
        ItemStack out = provider("minecraft:stick", 2).getStack(input, ItemStackModifier.Context.DEFAULT, hooks);
        assertThat(out.getType()).isEqualTo(Material.STICK);
        assertThat(out.getAmount()).isEqualTo(6);
        assertThat(provider("minecraft:stick", 2).getSingleStack(input, hooks).getAmount()).isEqualTo(2);

        // TFC's oil cooling: copy_input then add_heat -40
        ItemStack hot = items.create("tfp:ingot/copper", 2).orElseThrow();
        HeatPdc.write(hot, new HeatState(500f, NOW));
        ItemStack cooled = provider(null, 1, ItemStackModifier.CopyInput.INSTANCE, new ItemStackModifier.AddHeat(-40f))
                .getStack(hot, ItemStackModifier.Context.DEFAULT, hooks);
        assertThat(cooled.getAmount()).isEqualTo(2);
        assertThat(items.idOf(cooled)).contains("tfp:ingot/copper");
        assertThat(services.heat().temperature(cooled)).isEqualTo(460f);
        assertThat(services.heat().temperature(hot)).as("the input is not modified").isEqualTo(500f);
    }

    @Test
    void copyHeatAndAddHeat() {
        ItemStack hot = items.create("tfp:ingot/copper", 1).orElseThrow();
        HeatPdc.write(hot, new HeatState(800f, NOW));
        ItemStack out = provider("tfp:ingot/copper", 1, ItemStackModifier.CopyHeat.INSTANCE)
                .getSingleStack(hot, hooks);
        assertThat(HeatPdc.read(out)).contains(new HeatState(800f, NOW));
        ItemStack cold = hooks.addHeat(items.create("tfp:ingot/copper", 1).orElseThrow(), -50f);
        assertThat(services.heat().temperature(cold)).as("not below 0").isZero();

        ItemModifierHooks unheatable = new ItemModifierHooks(new ItemModifierHooks.Services(items, tags::itemId,
                services.food(), services.foodDefinitions(), services.heat(), stack -> 0f, () -> NOW),
                ItemIngredientContext.lookup(tags.snapshot()), ModifierExtensions.NONE, roll::get);
        assertThat(HeatPdc.has(unheatable.copyHeat(new ItemStack(Material.STICK), hot))).isFalse();
    }

    @Test
    void copyFoodKeepsTheInputsDecayAndTraits() {
        long date = 4_000 * ST;
        ItemStack raw = food("tfp:food/beef", date, FoodTrait.SALTED);
        ItemStack cooked = provider("tfp:food/cooked_beef", 1, ItemStackModifier.CopyFood.INSTANCE)
                .getSingleStack(raw, hooks);
        // Same decay modifiers on both sides: delta 1, the date rounds to itself; the salted trait is copied.
        assertThat(FoodPdc.read(cooked)).contains(new FoodState(date, List.of(FoodTrait.SALTED)));

        // An output that already has a trait: the decay delta counts both.
        ItemStack brined = food("tfp:food/cooked_beef", NOW, FoodTrait.BRINED);
        hooks.copyFood(brined, raw);
        FoodState in = FoodPdc.read(raw).orElseThrow();
        FoodState temp = new FoodState(NOW, List.of(FoodTrait.BRINED, FoodTrait.SALTED));
        float delta = temp.decayDateModifier(1f, FoodConfig.DEFAULT) / in.decayDateModifier(1f, FoodConfig.DEFAULT);
        assertThat(FoodPdc.read(brined)).contains(new FoodState(
                FoodDecay.newRoundedCreationDate(date, delta, NOW, ST), List.of(FoodTrait.BRINED, FoodTrait.SALTED)));
    }

    @Test
    void copyOldestResetRoundAndTraits() {
        ItemStack a = food("tfp:food/beef", 2_000 * ST);
        ItemStack b = food("tfp:food/beef", 1_000 * ST);
        ItemModifierHooks crafting = hooks.forCrafting(List.of(a, new ItemStack(Material.STICK), b), s -> {
        });
        ItemStack out = crafting.copyOldestFood(food("tfp:food/cooked_beef", NOW));
        assertThat(FoodPdc.read(out).orElseThrow().creationDate()).isEqualTo(1_000 * ST);

        assertThat(FoodPdc.read(hooks.resetFood(food("tfp:food/beef", 7L))).orElseThrow().creationDate())
                .isEqualTo(FoodDecay.roundedCreationDate(NOW, ST));
        assertThat(FoodPdc.read(hooks.roundCreationDate(food("tfp:food/beef", ST + 1))).orElseThrow().creationDate())
                .isEqualTo(2 * ST);
        assertThat(FoodPdc.read(hooks.roundCreationDate(food("tfp:food/beef", FoodDecay.ROTTEN_FLAG)))
                .orElseThrow().creationDate()).isEqualTo(FoodDecay.ROTTEN_FLAG);

        ItemStack fresh = food("tfp:food/beef", 4_999 * ST);
        FoodState before = FoodPdc.read(fresh).orElseThrow();
        hooks.addTrait(fresh, "tfc:salted");
        assertThat(FoodPdc.read(fresh)).contains(before.withTraitApplied(FoodTrait.SALTED, 1f, NOW, FoodConfig.DEFAULT));
        hooks.removeTrait(fresh, "tfp:salted");
        assertThat(FoodPdc.read(fresh).orElseThrow().traits()).isEmpty();
        assertThat(FoodPdc.has(hooks.addTrait(new ItemStack(Material.STICK), "tfp:salted"))).isFalse();
    }

    /** Vanilla remainders come from the server's item types (MockBukkit does not implement them: skipped there). */
    @Test
    void vanillaCraftingRemainders() {
        assertThat(hooks.craftingRemainder(new ItemStack(Material.WATER_BUCKET)).getType()).isEqualTo(Material.BUCKET);
        assertThat(hooks.craftingRemainder(new ItemStack(Material.STICK)).isEmpty()).isTrue();
    }

    @Test
    void customItemsTakeTheirRemainderFromTheExtension() {
        ItemStack rod = items.create("tfp:rod", 1).orElseThrow();
        assertThat(hooks.craftingRemainder(rod).isEmpty()).isTrue();
        ItemModifierHooks extended = new ItemModifierHooks(services, ItemIngredientContext.lookup(tags.snapshot()),
                ModifierExtensions.NONE.with(ModifierExtensions.Kind.CRAFTING_REMAINDER,
                        (stack, input, inputs, modifier) -> Optional.of(new ItemStack(Material.STRING))),
                roll::get);
        assertThat(extended.craftingRemainder(rod).getType()).isEqualTo(Material.STRING);
    }

    @Test
    void damageCraftingRemainderDamagesACopyOfTheTool() {
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack damaged = hooks.damageCraftingRemainder(ItemStack.empty(), pick);
        assertThat(((Damageable) damaged.getItemMeta()).getDamage()).isEqualTo(1);
        assertThat(((Damageable) pick.getItemMeta()).getDamage()).as("input untouched").isZero();

        ItemStack worn = new ItemStack(Material.IRON_PICKAXE);
        Damageable d = (Damageable) worn.getItemMeta();
        d.setDamage(Material.IRON_PICKAXE.getMaxDurability() - 1);
        worn.setItemMeta(d);
        assertThat(hooks.damageCraftingRemainder(ItemStack.empty(), worn).isEmpty()).as("breaks").isTrue();
        assertThat(hooks.damageCraftingRemainder(ItemStack.empty(), new ItemStack(Material.DIRT)).isEmpty()).isTrue();

        ItemStack unbreaking = new ItemStack(Material.IRON_PICKAXE);
        unbreaking.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        roll.set(0.5); // < 3/4: spared
        assertThat(((Damageable) hooks.damageCraftingRemainder(ItemStack.empty(), unbreaking).getItemMeta())
                .getDamage()).isZero();
        roll.set(0.8);
        assertThat(((Damageable) hooks.damageCraftingRemainder(ItemStack.empty(), unbreaking).getItemMeta())
                .getDamage()).isEqualTo(1);
    }

    @Test
    void dyeLeatherAndRemoveDye() {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE, 2);
        ItemStack red = hooks.dyeLeather(chest, "red");
        assertThat(red.getAmount()).isEqualTo(1);
        LeatherArmorMeta meta = (LeatherArmorMeta) red.getItemMeta();
        assertThat(meta.getColor().asRGB())
                .isEqualTo(DyeMixing.mix(OptionalInt.empty(), List.of(DyeColor.RED.getColor().asRGB())));
        ItemStack mixed = hooks.dyeLeather(red, "blue");
        assertThat(((LeatherArmorMeta) mixed.getItemMeta()).getColor().asRGB()).isEqualTo(DyeMixing.mix(
                OptionalInt.of(DyeColor.RED.getColor().asRGB()), List.of(DyeColor.BLUE.getColor().asRGB())));
        assertThat(hooks.dyeLeather(new ItemStack(Material.STICK), "red").isEmpty()).as("not dyeable").isTrue();

        ItemStack clean = hooks.removeDye(ItemStack.empty(), red);
        assertThat(clean.getType()).isEqualTo(Material.LEATHER_CHESTPLATE);
    }

    @Test
    void chanceExtraProductsBowlAndExtensions() {
        ItemStackProvider half = provider("minecraft:stick", 4, new ItemStackModifier.Chance(0.5f));
        roll.set(0.1);
        assertThat(half.getStack(ItemStack.empty(), ItemStackModifier.Context.DEFAULT, hooks).getAmount()).isEqualTo(4);
        roll.set(0.9);
        assertThat(half.getStack(ItemStack.empty(), ItemStackModifier.Context.DEFAULT, hooks).isEmpty()).isTrue();
        ItemStack display = half.getStack(ItemStack.empty(), ItemStackModifier.Context.NO_RANDOM_CHANCE, hooks);
        assertThat(display.getAmount()).isEqualTo(4);
        assertThat(display.lore()).hasSize(1);

        List<ItemStack> given = new ArrayList<>();
        ItemModifierHooks crafting = hooks.forCrafting(List.of(), given::add);
        provider("minecraft:stick", 1, new ItemStackModifier.ExtraProduct(
                new ItemStackTemplate("minecraft:bone_meal", 1, Optional.empty())))
                .getStack(ItemStack.empty(), ItemStackModifier.Context.DEFAULT, crafting);
        assertThat(given).extracting(ItemStack::getType).containsExactly(Material.BONE_MEAL);

        assertThat(hooks.emptyBowl(ItemStack.empty(), new ItemStack(Material.MUSHROOM_STEW)).getType())
                .as("TFC's Bowl.DISPLAY").isEqualTo(Material.BOWL);
        assertThat(hooks.flowerCutting(ItemStack.empty(), new ItemStack(Material.STICK)).isEmpty()).isTrue();
        ItemStack rod = items.create("tfp:rod", 1).orElseThrow();
        assertThat(hooks.addBaitToRod(rod)).isSameAs(rod);

        ModifierExtensions ext = ModifierExtensions.NONE.with(ModifierExtensions.Kind.EMPTY_BOWL,
                (stack, input, inputs, modifier) -> Optional.of(new ItemStack(Material.FLOWER_POT)));
        ItemModifierHooks extended = new ItemModifierHooks(services, ItemIngredientContext.lookup(tags.snapshot()), ext,
                roll::get);
        assertThat(extended.emptyBowl(ItemStack.empty(), new ItemStack(Material.MUSHROOM_STEW)).getType())
                .isEqualTo(Material.FLOWER_POT);
    }
}
