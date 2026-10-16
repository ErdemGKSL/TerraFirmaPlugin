/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.json.StrictJson;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackModifier.Context;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Codecs of every output modifier type, and the pure parts of {@link ItemStackProvider}. */
class OutputModifierTest {
    static ItemStackProvider provider(String json) {
        return ItemStackProvider.CODEC.decode(StrictJson.parse(json, "t"), "t.json");
    }

    static ItemStackModifier modifier(String json) {
        return ItemStackModifier.CODEC.decode(StrictJson.parse(json, "t"), "t.json");
    }

    @Test
    void everyTfcModifierTypeIsRegistered() {
        assertThat(ItemStackModifier.TYPES.types()).containsExactlyInAnyOrder(
                "tfc:copy_input", "tfc:copy_food", "tfc:copy_oldest_food", "tfc:copy_heat", "tfc:copy_forging_bonus",
                "tfc:reset_food", "tfc:empty_bowl", "tfc:flower_cutting", "tfc:add_bait_to_rod", "tfc:add_glass",
                "tfc:add_powder", "tfc:crafting_remainder", "tfc:damage_crafting_remainder", "tfc:add_trait",
                "tfc:remove_trait", "tfc:round_creation_date", "tfc:add_heat", "tfc:dye_leather", "tfc:remove_dye",
                "tfc:meal", "tfc:extra_products", "tfc:chance");
    }

    @Test
    void plainStackOrModifierForm() {
        assertThat(provider("{\"count\": 8, \"id\": \"minecraft:clay_ball\"}"))
                .isEqualTo(new ItemStackProvider(Optional.of(new ItemStackTemplate("minecraft:clay_ball", 8, Optional.empty())), List.of()));
        ItemStackProvider p = provider("{\"modifiers\": [{\"type\": \"tfc:copy_input\"}, {\"type\": \"tfc:add_heat\", \"temperature\": -40.0}]}");
        assertThat(p.stack()).isEmpty();
        assertThat(p.modifiers()).containsExactly(ItemStackModifier.CopyInput.INSTANCE, new ItemStackModifier.AddHeat(-40f));
        assertThat(p.dependsOnInput()).isTrue();
        assertThatThrownBy(() -> provider("{\"id\": \"tfc:a\", \"modifiers\": []}")).hasMessageContaining("unknown key(s) [id]");
        assertThatThrownBy(() -> provider("{\"id\": \"tfc:a\", \"count\": 100}")).hasMessageContaining("1..99");
    }

    @Test
    void getStackScalesByInputCountThenAppliesModifiersInOrder() {
        // recipe/barrel/pickled.json output_item
        ItemStackProvider pickled = provider("{\"modifiers\": [{\"type\": \"tfc:copy_input\"}, {\"type\": \"tfc:add_trait\", \"trait\": \"tfc:pickled\"}]}");
        TestStack input = TestStack.of("tfc:food/banana").count(4);
        TestStack out = pickled.getStack(input, Context.DEFAULT, new TestStack.Hooks());
        assertThat(out.item()).isEqualTo("tfc:food/banana");
        assertThat(out.count()).isEqualTo(4);
        assertThat(out.log()).containsExactly("addTrait tfc:pickled");

        ItemStackProvider fixed = provider("{\"stack\": {\"id\": \"tfc:food/jam\", \"count\": 2}, \"modifiers\": [{\"type\": \"tfc:copy_food\"}]}");
        TestStack jam = fixed.getStack(input, Context.DEFAULT, new TestStack.Hooks());
        assertThat(jam.count()).as("2 per input item").isEqualTo(8);
        assertThat(jam.log()).containsExactly("copyFood<tfc:food/banana");
        assertThat(fixed.getSingleStack(input, new TestStack.Hooks()).count()).isEqualTo(2);
        assertThat(fixed.getStack(TestStack.EMPTY, Context.DEFAULT, new TestStack.Hooks()).count()).isEqualTo(2);
    }

    @Test
    void chanceRollsPerItemAndIsDeterministicForDisplay() {
        ItemStackProvider p = provider("{\"stack\": {\"id\": \"tfc:x\", \"count\": 4}, \"modifiers\": [{\"type\": \"tfc:chance\", \"chance\": 0.5}]}");
        double[] rolls = {0.1, 0.9, 0.4, 0.6};
        AtomicInteger i = new AtomicInteger();
        TestStack out = p.getStack(TestStack.EMPTY, Context.DEFAULT, new TestStack.Hooks(() -> rolls[i.getAndIncrement()], List.of()));
        assertThat(out.count()).isEqualTo(2);
        TestStack none = p.getStack(TestStack.EMPTY, Context.DEFAULT, new TestStack.Hooks(() -> 0.99, List.of()));
        assertThat(none.isEmpty()).isTrue();
        TestStack display = p.getStack(TestStack.EMPTY, Context.NO_RANDOM_CHANCE, new TestStack.Hooks(() -> {
            throw new AssertionError("no randomness in display context");
        }, List.of()));
        assertThat(display.count()).isEqualTo(4);
        assertThat(display.log()).containsExactly("chanceLore 0.5");
    }

    @Test
    void extraProductsGoToTheCrafterOnlyInGameplay() {
        // recipe/crafting/medium_raw_hide.json remainder
        ItemStackProvider remainder = provider("""
                {"modifiers": [{"type": "tfc:damage_crafting_remainder"},
                               {"type": "tfc:extra_products", "stack": {"count": 2, "id": "tfc:wool"}}]}""");
        TestStack.Hooks hooks = new TestStack.Hooks();
        TestStack knife = TestStack.of("tfc:metal/knife/copper");
        TestStack out = remainder.getStack(knife, Context.DEFAULT, hooks);
        assertThat(out.item()).isEqualTo("tfc:metal/knife/copper");
        assertThat(out.log()).containsExactly("damaged");
        assertThat(hooks.given).containsExactly(TestStack.of("tfc:wool").count(2));
        TestStack.Hooks display = new TestStack.Hooks();
        remainder.getStack(knife, Context.NO_RANDOM_CHANCE, display);
        assertThat(display.given).isEmpty();
    }

    @Test
    void mealCodec() {
        // recipe/crafting/food/rye_bread_jam_sandwich_xsj_jar.json result.modifiers[0]
        ItemStackModifier.Meal meal = (ItemStackModifier.Meal) modifier("""
                {"type": "tfc:meal",
                 "food": {"decay_modifier": 4.5, "hunger": 4, "saturation": 1.0, "water": 0.5},
                 "portions": [
                   {"ingredient": {"item": "tfc:food/rye_bread"}, "nutrient_modifier": 0.675, "saturation_modifier": 0.5, "water_modifier": 0.5},
                   {"nutrient_modifier": 0.8, "saturation_modifier": 0.8, "water_modifier": 0.8}]}""");
        assertThat(meal.food()).isEqualTo(new FoodData(4, 0.5f, 1f, 0, 0, 0, 0, 0, 0, 4.5f));
        assertThat(meal.portions()).containsExactly(
                new ItemStackModifier.MealPortion(Optional.of(new Ingredient.Item("tfc:food/rye_bread")), 0.675f, 0.5f, 0.5f),
                new ItemStackModifier.MealPortion(Optional.empty(), 0.8f, 0.8f, 0.8f));
        assertThat(modifier("{\"type\": \"tfc:meal\", \"food\": {\"decay_modifier\": \"infinity\"}, \"portions\": []}"))
                .extracting(m -> ((ItemStackModifier.Meal) m).food().decayModifier()).isEqualTo(Float.POSITIVE_INFINITY);
        assertThatThrownBy(() -> modifier("{\"type\": \"tfc:meal\", \"food\": {\"decay_modifier\": \"forever\"}, \"portions\": []}"))
                .hasMessageContaining("'infinity'");
        assertThatThrownBy(() -> modifier("{\"type\": \"tfc:meal\", \"food\": {\"calories\": 1}, \"portions\": []}"))
                .hasMessageContaining("$.food: unknown key(s) [calories]");
    }

    @Test
    void parameterisedModifiersAndHooks() {
        TestStack.Hooks hooks = new TestStack.Hooks();
        TestStack in = TestStack.of("tfc:in");
        TestStack out = TestStack.of("tfc:out");
        assertThat(modifier("{\"type\": \"tfc:dye_leather\", \"color\": \"light_blue\"}").apply(out, in, Context.DEFAULT, hooks).log())
                .containsExactly("dye light_blue");
        assertThatThrownBy(() -> modifier("{\"type\": \"tfc:dye_leather\", \"color\": \"teal\"}")).hasMessageContaining("is not one of");
        assertThat(modifier("{\"type\": \"tfc:remove_trait\", \"trait\": \"tfc:salted\"}").apply(out, in, Context.DEFAULT, hooks).log())
                .containsExactly("removeTrait tfc:salted");
        assertThat(modifier("{\"type\": \"tfc:empty_bowl\"}").apply(out, in, Context.DEFAULT, hooks).item()).isEqualTo("tfc:ceramic/bowl");
        assertThat(modifier("{\"type\": \"tfc:copy_heat\"}").dependsOnInput()).isTrue();
        assertThat(modifier("{\"type\": \"tfc:copy_food\"}").dependsOnInput()).isFalse();
        assertThat(modifier("{\"type\": \"tfc:add_powder\"}").apply(out, in, Context.DEFAULT, hooks).log()).containsExactly("addPowder");
        assertThat(modifier("{\"type\": \"tfc:crafting_remainder\"}").apply(out, in, Context.DEFAULT, hooks).isEmpty()).isTrue();
        assertThatThrownBy(() -> modifier("{\"type\": \"tfc:add_heat\"}")).hasMessageContaining("missing required key 'temperature'");
        assertThatThrownBy(() -> modifier("{\"type\": \"tfc:copy_input\", \"temperature\": 1}")).hasMessageContaining("unknown key(s)");
        assertThatThrownBy(() -> modifier("{\"type\": \"tfc:sparkle\"}")).hasMessageContaining("unknown item stack modifier type 'tfc:sparkle'");
    }
}
