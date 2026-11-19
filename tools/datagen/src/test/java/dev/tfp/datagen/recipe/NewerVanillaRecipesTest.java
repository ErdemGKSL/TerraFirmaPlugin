/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-PROC-43. */
class NewerVanillaRecipesTest {
    @Test
    void pinsTheCopperToolsAndArmourAndEverySpear() {
        assertThat(NewerVanillaRecipes.ids()).contains("copper_pickaxe", "copper_axe", "copper_shovel", "copper_hoe",
            "copper_sword", "copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots", "wooden_spear",
            "stone_spear", "copper_spear", "iron_spear", "golden_spear", "diamond_spear").doesNotHaveDuplicates();
    }

    @Test
    void keptRecipesAreNotSelected() {
        assertThat(NewerVanillaRecipes.ids()).doesNotContain("stick", "ladder", "book", "crafting_table", "copper_block");
    }

    @Test
    void writesTfcsFalseConditionAndLeavesExistingFilesAlone() {
        OutputTree out = new OutputTree();
        out.put(NewerVanillaRecipes.DIR + "copper_axe.json", "{\"type\": \"minecraft:crafting_shaped\"}");
        int written = NewerVanillaRecipes.run(out);
        assertThat(written).isEqualTo(NewerVanillaRecipes.ids().size() - 1);
        assertThat(new String(out.files().get(NewerVanillaRecipes.DIR + "copper_pickaxe.json"), StandardCharsets.UTF_8))
            .contains("\"neoforge:conditions\"", "\"neoforge:false\"");
        assertThat(new String(out.files().get(NewerVanillaRecipes.DIR + "copper_axe.json"), StandardCharsets.UTF_8))
            .contains("crafting_shaped");
        assertThat(List.copyOf(out.files().keySet())).allMatch(p -> p.startsWith("data/minecraft/recipe/"));
    }
}
