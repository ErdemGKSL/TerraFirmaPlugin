/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AnvilRecipe.java @ c3b4d9536. The golden values are TFC's recipe JSON
 * (src/generated/resources/data/tfc/recipe/anvil) as transcribed in tfc-anvil-recipes.tsv.
 */
package dev.tfp.core.anvil;

import static dev.tfp.core.anvil.AnvilFixtures.NOT_AN_INGREDIENT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilFixtures.AnvilRow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * M4-ANV-02 golden values: all 281 TFC anvil recipes. Checks the row count against the file count, that every recipe is consistent and
 * builds, that each recipe accepts exactly its TFC inputs at its TFC tier, that each item lists exactly the recipes TFC lists for it,
 * and that the default plan follows TFC's rule.
 */
class AnvilGoldenTest {
    static final int TFC_ANVIL_FILES = 281;

    static final List<AnvilRow> ROWS = AnvilFixtures.anvilRows();
    static final Map<String, Set<String>> TAGS = AnvilFixtures.tagMembers();
    static final AnvilRecipes RECIPES = new AnvilRecipes(ROWS.stream().map(AnvilRow::recipe).toList());

    @Test
    void rowCountMatchesTfcFileCount() {
        assertThat(AnvilFixtures.fileCount(AnvilFixtures.ANVIL_TSV)).isEqualTo(TFC_ANVIL_FILES);
        assertThat(ROWS).hasSize(AnvilFixtures.fileCount(AnvilFixtures.ANVIL_TSV));
        assertThat(RECIPES.all()).hasSize(TFC_ANVIL_FILES);
    }

    @Test
    void everyRecipeIsConsistentAndHasAUniqueId() {
        // AnvilRecipe's constructor rejects inconsistent rules, so building the registry checks every row.
        assertThat(RECIPES.all()).extracting(AnvilRecipe::id).doesNotHaveDuplicates();
        for (AnvilRow row : ROWS) {
            assertThat(row.rules()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        }
    }

    @Test
    void everyRecipeAcceptsItsTfcInputsAtItsTfcTierOnly() {
        final var ctx = AnvilFixtures.context(TAGS);
        for (AnvilRow row : ROWS) {
            final AnvilRecipe recipe = row.recipe();
            final Set<String> members = AnvilFixtures.members(row.input(), TAGS);
            assertThat(members).as(row.file()).isNotEmpty();
            for (String item : members) {
                assertThat(recipe.matches(item, row.tier(), ctx)).as(row.file() + " " + item).isTrue();
                if (row.tier() > 0) {
                    assertThat(recipe.matches(item, row.tier() - 1, ctx)).as(row.file() + " below tier " + item).isFalse();
                }
            }
            assertThat(recipe.matches(NOT_AN_INGREDIENT, 6, ctx)).as(row.file()).isFalse();
        }
    }

    @Test
    void eachItemListsExactlyTheRecipesTfcListsForIt() {
        final var ctx = AnvilFixtures.context(TAGS);
        final Set<String> items = new LinkedHashSet<>();
        for (AnvilRow row : ROWS) {
            items.addAll(AnvilFixtures.members(row.input(), TAGS));
        }
        for (String item : items) {
            for (int tier = 0; tier <= 6; tier++) {
                final List<String> expected = new ArrayList<>();
                for (AnvilRow row : ROWS) {
                    if (AnvilFixtures.members(row.input(), TAGS).contains(item) && tier >= row.tier()) {
                        expected.add(row.id());
                    }
                }
                final List<String> actual = RECIPES.candidates(item, tier, ctx).stream().map(AnvilRecipe::id).toList();
                assertThat(actual).as(item + " at tier " + tier).isEqualTo(expected);
            }
        }
    }

    @Test
    void everyRecipeGivesItsTfcResult() {
        for (AnvilRow row : ROWS) {
            final AnvilRecipe recipe = RECIPES.byId(row.id()).orElseThrow();
            assertThat(recipe.output()).as(row.file()).isEqualTo(row.output());
            assertThat(recipe.applyForgingBonus()).as(row.file()).isEqualTo(row.applyBonus());
            assertThat(recipe.rules()).as(row.file()).isEqualTo(row.rules());
        }
    }

    @Test
    void defaultPlanFollowsTfcChoice() {
        final var ctx = AnvilFixtures.context(TAGS);
        int single = 0;
        int multiple = 0;
        for (String item : TAGS.values().stream().flatMap(Set::stream).toList()) {
            final List<AnvilRecipe> candidates = RECIPES.candidates(item, 6, ctx);
            if (candidates.size() == 1) {
                single++;
                assertThat(AnvilRecipes.defaultFor(candidates, null)).contains(candidates.get(0));
            } else if (candidates.size() > 1) {
                multiple++;
                assertThat(AnvilRecipes.defaultFor(candidates, null)).isEmpty();
                for (AnvilRecipe remembered : candidates) {
                    assertThat(AnvilRecipes.defaultFor(candidates, remembered.id())).contains(remembered);
                }
                assertThat(AnvilRecipes.defaultFor(candidates, "tfc:anvil/not_listed")).isEmpty();
            }
        }
        assertThat(single).isPositive();
        assertThat(multiple).isPositive();
    }

    /** The golden recipe in a file under {@code recipe/anvil}, such as {@code blowpipe} or {@code metal/axe_head/bronze}. */
    static Optional<AnvilRecipe> byFile(String file) {
        return RECIPES.byId("tfc:anvil/" + file);
    }
}
