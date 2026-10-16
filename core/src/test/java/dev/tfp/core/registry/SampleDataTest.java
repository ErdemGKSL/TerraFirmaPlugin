/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.RecipeSchemas;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.tag.TagType;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Loads the fixed TFC sample in {@code src/test/resources/tfc-sample} (TFC files copied unmodified
 * from {@code src/generated/resources/data/} @ c3b4d9536, listed in its {@code SOURCES.txt}) and
 * checks that it exercises every recipe type, definition kind, ingredient type and output modifier
 * that occurs in TFC's data. The full upstream tree is covered by {@link TfcDataCoverageTest}.
 */
class SampleDataTest {
    static RegistrySnapshot snapshot;

    @BeforeAll
    static void load() throws URISyntaxException {
        Path root = Path.of(Objects.requireNonNull(SampleDataTest.class.getResource("/tfc-sample/data")).toURI());
        snapshot = DataLoader.standard(KnownIds.permissive(Set.of("minecraft", "c"))).load(List.of(root));
    }

    @Test
    void everyRecipeTypeAndDefinitionKindIsPresent() {
        Set<String> types = new TreeSet<>();
        snapshot.get(DefinitionKinds.RECIPE).values().forEach(r -> types.add(r.schema()));
        assertThat(types).isEqualTo(new TreeSet<>(RecipeSchemas.TYPES.types()));
        for (DataKind<?> kind : DefinitionKinds.standard()) {
            assertThat(snapshot.get(kind.key()).size()).as(kind.key().name()).isPositive();
        }
        assertThat(snapshot.disabledRecipes()).containsExactly("minecraft:anvil");
    }

    @Test
    void everyIngredientAndModifierTypeFoundInTfcDataParses() {
        Set<String> ingredients = new TreeSet<>();
        Set<String> modifiers = new TreeSet<>();
        for (Registry<?> registry : snapshot.registries()) {
            for (Object value : registry.values()) {
                walk(value, ingredients, modifiers);
            }
        }
        // Types that occur in TFC's generated data @ c3b4d9536 (tfc:rotten and neoforge:intersection
        // are registered but unused; they are covered by IngredientTest).
        assertThat(ingredients).containsExactlyInAnyOrder("item", "tag", "list", "tfc:and", "tfc:not_rotten",
                "tfc:has_trait", "tfc:lacks_trait", "tfc:heat", "tfc:fluid_content", "neoforge:compound", "neoforge:difference");
        // tfc:copy_heat, tfc:reset_food and tfc:crafting_remainder are registered but unused in TFC data.
        assertThat(modifiers).containsExactlyInAnyOrder("tfc:copy_input", "tfc:copy_food", "tfc:copy_oldest_food",
                "tfc:copy_forging_bonus", "tfc:empty_bowl", "tfc:flower_cutting", "tfc:add_bait_to_rod", "tfc:add_glass",
                "tfc:add_powder", "tfc:damage_crafting_remainder", "tfc:add_trait", "tfc:remove_trait",
                "tfc:round_creation_date", "tfc:add_heat", "tfc:dye_leather", "tfc:remove_dye", "tfc:meal",
                "tfc:extra_products", "tfc:chance");
    }

    @Test
    void decodedValuesMatchTheJson() {
        SchemaRecord pickled = snapshot.get(DefinitionKinds.RECIPE).getOrThrow("tfc:barrel/pickled");
        assertThat(pickled.schema()).isEqualTo("tfc:barrel_sealed");
        assertThat(pickled.require("duration", Integer.class)).isEqualTo(3600);
        SizedIngredient input = pickled.require("input_item", SizedIngredient.class);
        assertThat(input.count()).isEqualTo(1);
        assertThat(((Ingredient.And) input.ingredient()).children()).hasSize(4)
                .contains(new Ingredient.HasTrait("tfc:brined"), new Ingredient.LacksTrait("tfc:pickled"));
        ItemStackProvider out = pickled.require("output_item", ItemStackProvider.class);
        assertThat(out.modifiers()).containsExactly(ItemStackModifier.CopyInput.INSTANCE, new ItemStackModifier.AddTrait("tfc:pickled"));

        SchemaRecord ingots = snapshot.get(DefinitionKinds.key("item_size")).getOrThrow("tfc:ingots");
        assertThat(ingots.require("size", String.class)).isEqualTo("large");
        assertThat(ingots.require("ingredient", Ingredient.class)).isInstanceOf(Ingredient.Difference.class);
        assertThat(snapshot.tags().members(TagType.ITEM, "tfc:foods/preserves")).doesNotContain("tfc:jar/peanut_unsealed");
    }

    private static void walk(Object value, Set<String> ingredients, Set<String> modifiers) {
        switch (value) {
            case SchemaRecord r -> r.values().values().forEach(v -> walk(v, ingredients, modifiers));
            case List<?> l -> l.forEach(v -> walk(v, ingredients, modifiers));
            case Map<?, ?> m -> m.values().forEach(v -> walk(v, ingredients, modifiers));
            case Optional<?> o -> o.ifPresent(v -> walk(v, ingredients, modifiers));
            case SizedIngredient s -> walk(s.ingredient(), ingredients, modifiers);
            case ItemStackProvider p -> p.modifiers().forEach(m -> walk(m, ingredients, modifiers));
            case ItemStackModifier.Meal meal -> {
                modifiers.add("tfc:meal");
                meal.portions().forEach(portion -> portion.ingredient().ifPresent(i -> walk(i, ingredients, modifiers)));
            }
            case ItemStackModifier m -> modifiers.add(modifierType(m));
            case Ingredient i -> walkIngredient(i, ingredients, modifiers);
            default -> {
            }
        }
    }

    private static void walkIngredient(Ingredient i, Set<String> ingredients, Set<String> modifiers) {
        switch (i) {
            case Ingredient.Item x -> ingredients.add("item");
            case Ingredient.Tag x -> ingredients.add("tag");
            case Ingredient.AnyOf x -> {
                ingredients.add("list");
                x.children().forEach(c -> walkIngredient(c, ingredients, modifiers));
            }
            case Ingredient.Compound x -> {
                ingredients.add("neoforge:compound");
                x.children().forEach(c -> walkIngredient(c, ingredients, modifiers));
            }
            case Ingredient.And x -> {
                ingredients.add("tfc:and");
                x.children().forEach(c -> walkIngredient(c, ingredients, modifiers));
            }
            case Ingredient.Intersection x -> {
                ingredients.add("neoforge:intersection");
                x.children().forEach(c -> walkIngredient(c, ingredients, modifiers));
            }
            case Ingredient.Difference x -> {
                ingredients.add("neoforge:difference");
                walkIngredient(x.base(), ingredients, modifiers);
                walkIngredient(x.subtracted(), ingredients, modifiers);
            }
            case Ingredient.NotRotten x -> ingredients.add("tfc:not_rotten");
            case Ingredient.Rotten x -> ingredients.add("tfc:rotten");
            case Ingredient.HasTrait x -> ingredients.add("tfc:has_trait");
            case Ingredient.LacksTrait x -> ingredients.add("tfc:lacks_trait");
            case Ingredient.Heat x -> ingredients.add("tfc:heat");
            case Ingredient.FluidContent x -> ingredients.add("tfc:fluid_content");
        }
    }

    /** Type id from the class name ({@code CopyInput} → {@code tfc:copy_input}). */
    private static String modifierType(ItemStackModifier m) {
        String simple = m.getClass().getSimpleName();
        String snake = simple.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return "tfc:" + switch (snake) {
            case "extra_product" -> "extra_products";
            default -> snake;
        };
    }
}
