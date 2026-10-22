/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.knap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The runtime knapping loader against the generated content (5 types, 101 recipes). */
class KnappingDefinitionsTest {
    private static RegistrySnapshot snapshot() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        return DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
    }

    @Test
    void loadsAllTypesFromGeneratedData() throws IOException {
        KnappingDefinitions defs = KnappingDefinitions.fromRegistry(snapshot());
        assertThat(defs.types().keySet()).containsExactlyInAnyOrder(
                "tfp:clay", "tfp:fire_clay", "tfp:goat_horn", "tfp:leather", "tfp:rock");
        KnappingType clay = defs.type("tfp:clay").orElseThrow();
        assertThat(clay.input()).isEqualTo(new KnappingIngredient("tfp:clay_knapping", true));
        assertThat(clay.inputCount()).isEqualTo(5);
        assertThat(clay.amountToConsume()).isEqualTo(5);
        assertThat(clay.icon()).isEqualTo("minecraft:clay_ball");
        assertThat(defs.type("tfp:missing")).isEmpty();
        assertThat(defs.recipes()).hasSize(101);
    }

    @Test
    void resolvesEveryRecipe() throws IOException {
        RegistrySnapshot snapshot = snapshot();
        KnappingDefinitions defs = KnappingDefinitions.fromRegistry(snapshot);
        for (KnappingRecipe recipe : defs.recipes()) {
            KnappingType type = defs.type(recipe.typeId()).orElse(null);
            assertThat(type).as("type of %s", recipe.id()).isNotNull();
            assertThat(type.input().tag()).as("input of %s is a tag", recipe.id()).isTrue();
            assertThat(snapshot.tags().exists(TagType.ITEM, type.input().id()))
                    .as("input tag %s of %s", type.input().id(), recipe.id()).isTrue();
            if (recipe.ingredient() != null && recipe.ingredient().tag()) {
                assertThat(snapshot.tags().exists(TagType.ITEM, recipe.ingredient().id()))
                        .as("ingredient tag of %s", recipe.id()).isTrue();
            }
            assertThat(recipe.result().item()).as("result of %s", recipe.id()).contains(":");
        }
        assertThat(defs.recipesOf("tfp:goat_horn")).isNotEmpty();
    }
}
