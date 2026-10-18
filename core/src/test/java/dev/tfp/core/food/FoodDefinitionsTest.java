/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** M3-SURV-02: the food-definition lookup that gives each food its {@code decay_modifier}. */
class FoodDefinitionsTest {
    @Test
    void itemDefinitionsAndTheDefaultForUnknownItems() {
        Registry<SchemaRecord> foods = Registry.builder(DefinitionKinds.key("food"))
                .register("tfp:beef", new SchemaRecord("food", Map.of(
                        "ingredient", new Ingredient.Item("tfp:food/beef"), "hunger", 4, "protein", 2.0f,
                        "decay_modifier", 2.0f)))
                .register("tfp:rotten_flesh", new SchemaRecord("food", Map.of(
                        "ingredient", new Ingredient.Item("minecraft:rotten_flesh"),
                        "decay_modifier", Float.POSITIVE_INFINITY)))
                .register("tfp:no_decay", new SchemaRecord("food", Map.of(
                        "ingredient", new Ingredient.Item("tfp:food/no_decay"), "hunger", 4)))
                .build();
        RegistrySnapshot snapshot = new RegistrySnapshot(Map.of("food", foods), TagRegistry.EMPTY, Set.of());
        FoodDefinitions defs = FoodDefinitions.fromRegistry(snapshot);
        assertThat(defs.size()).isEqualTo(3);
        assertThat(defs.get("tfp:food/beef")).contains(new FoodData(4, 0, 0, 0, 0, 0, 0, 2.0f, 0, 2.0f));
        assertThat(defs.decayModifier("tfp:food/beef")).isEqualTo(2.0f);
        assertThat(defs.decayModifier("minecraft:rotten_flesh")).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(defs.decayModifier("tfp:food/no_decay")).as("a definition without decay_modifier: TFC's 0").isZero();
        assertThat(defs.decayModifier("tfp:not_food")).isEqualTo(FoodDefinitions.UNKNOWN_DECAY_MODIFIER);
        assertThat(defs.decayModifier(null)).isEqualTo(1f);
        assertThat(FoodDefinitions.EMPTY.decayModifier("tfp:food/beef")).isEqualTo(1f);
    }

    @Test
    void generatedContentHasTfcDecayModifiers() throws java.io.IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        FoodDefinitions defs = FoodDefinitions.fromRegistry(snapshot);
        // TFC data/tfc/tfc/food/*.json @ c3b4d9536
        assertThat(defs.decayModifier("tfp:food/blueberry")).isEqualTo(4.9f);
        assertThat(defs.decayModifier("tfp:food/beef")).isEqualTo(2.0f);
        assertThat(defs.decayModifier("tfp:food/cooked_beef")).isEqualTo(1.5f);
        assertThat(defs.decayModifier("tfp:food/cheese")).isEqualTo(0.3f);
        assertThat(defs.decayModifier("tfp:food/barley_bread")).isEqualTo(1.0f);
        assertThat(defs.decayModifier("tfp:food/wheat_grain")).isEqualTo(0.25f);
        assertThat(defs.get("tfp:food/blueberry").orElseThrow())
                .isEqualTo(new FoodData(4, 5.0f, 0.5f, 0, 0, 0.9f, 0, 0, 0, 4.9f));
        assertThat(defs.size()).isGreaterThanOrEqualTo(185);
    }
}
