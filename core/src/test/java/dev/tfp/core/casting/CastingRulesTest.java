/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.casting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the 111 TFC casting recipes. The table is exported from TFC's {@code recipe/casting/} at c3b4d9536 by
 * the export script in the scratchpad: one row per JSON file, with the break chance taken from the file or TFC's default of 1.
 */
class CastingRulesTest {
    /** The number of JSON files in TFC's {@code recipe/casting/} at c3b4d9536. */
    private static final int RECIPE_COUNT = 111;

    private static List<CastingRecipe> recipes() throws IOException {
        List<CastingRecipe> out = new ArrayList<>();
        try (InputStream in = CastingRulesTest.class.getResourceAsStream("casting-recipes.tsv")) {
            assertThat(in).isNotNull();
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].split("\t");
                assertThat(cols).as("row %d", i).hasSize(7);
                out.add(new CastingRecipe(
                    cols[0], cols[1], cols[2], Integer.parseInt(cols[3]), cols[4], Integer.parseInt(cols[5]), Float.parseFloat(cols[6])));
            }
        }
        return out;
    }

    @Test
    void rowCountMatchesJsonFiles() throws IOException {
        assertThat(recipes()).hasSize(RECIPE_COUNT);
    }

    @Test
    void breakChancesAreAsInTfc() throws IOException {
        List<CastingRecipe> recipes = recipes();
        assertThat(recipes).filteredOn(r -> r.breakChance() == 1f).hasSize(55);
        assertThat(recipes).filteredOn(r -> r.breakChance() == 0.01f).hasSize(28);
        assertThat(recipes).filteredOn(r -> r.breakChance() == 0.1f).hasSize(28);
    }

    @Test
    void everyRecipeMatchesItsOwnMoldAndYieldsItsResult() throws IOException {
        for (CastingRecipe recipe : recipes()) {
            assertThat(CastingRules.matches(recipe, recipe.mold(), recipe.fluid(), recipe.fluidAmount())).as(recipe.id()).isTrue();
            assertThat(CastingRules.assemble(recipe)).as(recipe.id()).isEqualTo(new CastingRules.Output(recipe.result(), recipe.resultCount()));
            assertThat(CastingRules.find(recipes(), recipe.mold(), recipe.fluid(), recipe.fluidAmount()))
                .as(recipe.id()).contains(recipe);
        }
    }

    @Test
    void everyRecipeNeedsItsFullFluidAmount() throws IOException {
        for (CastingRecipe recipe : recipes()) {
            assertThat(CastingRules.matches(recipe, recipe.mold(), recipe.fluid(), recipe.fluidAmount() - 1)).as(recipe.id()).isFalse();
            assertThat(CastingRules.matches(recipe, recipe.mold(), recipe.fluid(), recipe.fluidAmount() + 500)).as(recipe.id()).isTrue();
        }
    }

    @Test
    void wrongMoldOrFluidDoesNotMatch() throws IOException {
        for (CastingRecipe recipe : recipes()) {
            assertThat(CastingRules.matches(recipe, "tfc:ceramic/unfired", recipe.fluid(), 10_000)).as(recipe.id()).isFalse();
            assertThat(CastingRules.matches(recipe, recipe.mold(), "tfc:metal/not_a_recipe_fluid", 10_000)).as(recipe.id()).isFalse();
            assertThat(CastingRules.matches(recipe, recipe.mold(), "", 10_000)).as(recipe.id()).isFalse();
        }
    }

    @Test
    void moldAndFluidPairsAreUnique() throws IOException {
        Set<String> pairs = new HashSet<>();
        for (CastingRecipe recipe : recipes()) {
            assertThat(pairs.add(recipe.mold() + "|" + recipe.fluid())).as(recipe.id()).isTrue();
        }
    }

    @Test
    void sixteenMoldsAreUsed() throws IOException {
        assertThat(recipes().stream().map(CastingRecipe::mold).distinct()).hasSize(16);
    }

    @Test
    void useBreaksWhenRollIsBelowChance() throws IOException {
        for (CastingRecipe recipe : recipes()) {
            float chance = recipe.breakChance();
            assertThat(CastingRules.breaksOnUse(recipe, 0f)).as(recipe.id()).isTrue();
            assertThat(CastingRules.breaksOnUse(recipe, Math.nextDown(chance))).as(recipe.id()).isTrue();
            assertThat(CastingRules.breaksOnUse(recipe, chance)).as(recipe.id()).isFalse();
            assertThat(CastingRules.breaksOnUse(recipe, Math.nextUp(chance))).as(recipe.id()).isFalse();
        }
    }

    @Test
    void craftKeepsWhenRollIsAboveChance() throws IOException {
        for (CastingRecipe recipe : recipes()) {
            float chance = recipe.breakChance();
            assertThat(CastingRules.keepsOnCraft(recipe, Math.nextUp(chance))).as(recipe.id()).isTrue();
            assertThat(CastingRules.keepsOnCraft(recipe, chance)).as(recipe.id()).isFalse();
            assertThat(CastingRules.keepsOnCraft(recipe, 0f)).as(recipe.id()).isFalse();
        }
    }

    @Test
    void defaultBreakChanceAlwaysBreaksOnUse() {
        CastingRecipe recipe = new CastingRecipe("t", "tfc:ceramic/x", "tfc:metal/copper", 100, "tfc:metal/ingot/copper", 1);
        assertThat(recipe.breakChance()).isEqualTo(CastingRecipe.DEFAULT_BREAK_CHANCE);
        assertThat(CastingRules.breaksOnUse(recipe, 0.999999f)).isTrue();
    }

    @Test
    void seededRollsBreakAtTheChance() {
        CastingRecipe recipe = new CastingRecipe("t", "tfc:ceramic/x", "tfc:metal/copper", 100, "tfc:metal/ingot/copper", 1, 0.1f);
        Random random = new Random(1234L);
        int breaks = 0;
        int draws = 200_000;
        for (int i = 0; i < draws; i++) {
            if (CastingRules.breaksOnUse(recipe, random.nextFloat())) {
                breaks++;
            }
        }
        assertThat((double) breaks / draws).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.005));
    }

    @Test
    void chanceOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> new CastingRecipe("t", "m", "f", 1, "r", 1, 1.5f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CastingRecipe("t", "m", "f", 1, "r", 1, -0.1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CastingRecipe("t", "m", "f", 0, "r", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
