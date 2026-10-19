/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.json.SchemaRecord;
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

/** M3-HEAT-01: the item heat lookup (TFC {@code item_heat} definitions). */
class ItemHeatDefinitionsTest {
    @Test
    void itemDefinitionsAndTheDefaultForUnknownItems() {
        Registry<SchemaRecord> heat = Registry.builder(DefinitionKinds.key("item_heat"))
                .register("tfp:bell", new SchemaRecord("item_heat", Map.of(
                        "ingredient", new Ingredient.Item("minecraft:bell"), "heat_capacity", 1.6666666f,
                        "forging_temperature", 636.0f, "welding_temperature", 848.0f)))
                .register("tfp:clay", new SchemaRecord("item_heat", Map.of(
                        "ingredient", new Ingredient.Item("tfp:clay_ball"), "heat_capacity", 1.5f)))
                .build();
        RegistrySnapshot snapshot = new RegistrySnapshot(Map.of("item_heat", heat), TagRegistry.EMPTY, Set.of());
        ItemHeatDefinitions defs = ItemHeatDefinitions.fromRegistry(snapshot);

        assertThat(defs.size()).isEqualTo(2);
        assertThat(defs.heatCapacity("minecraft:bell")).isEqualTo(1.6666666f);
        assertThat(defs.get("minecraft:bell").orElseThrow().canForge()).isTrue();
        assertThat(defs.get("minecraft:bell").orElseThrow().canWeld()).isTrue();
        assertThat(defs.get("tfp:clay_ball").orElseThrow().canForge()).as("no forging temperature").isFalse();
        assertThat(defs.heatCapacity("tfp:not_heatable")).as("cannot be heated").isZero();
        assertThat(defs.heatCapacity(null)).isZero();
        assertThat(ItemHeatDefinitions.EMPTY.heatCapacity("minecraft:bell")).isZero();
    }

    @Test
    void generatedContentHasTfcMetalHeatData() throws java.io.IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        ItemHeatDefinitions defs = ItemHeatDefinitions.fromRegistry(snapshot);
        // TFC data/tfc/tfc/item_heat/copper/ingot.json @ c3b4d9536: tag c:ingots/copper
        ItemHeatDefinitions.HeatData copper = defs.get("tfp:metal/ingot/copper").orElseThrow();
        assertThat(copper.heatCapacity()).isEqualTo(2.857143f);
        assertThat(copper.forgingTemperature()).isEqualTo(648.0f);
        assertThat(copper.weldingTemperature()).isEqualTo(864.0f);
        assertThat(defs.size()).isGreaterThan(100);
    }
}
