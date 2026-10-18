/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/WeldingRecipe.java @ c3b4d9536. The golden values are TFC's recipe JSON
 * (src/generated/resources/data/tfc/recipe/welding) as transcribed in tfc-welding-recipes.tsv.
 */
package dev.tfp.core.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilFixtures.WeldingRow;
import dev.tfp.core.forge.ForgingBonus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * M4-ANV-02 golden values: all 87 TFC welding recipes. Checks the row count against the file count, that each recipe welds its TFC
 * inputs in either order at its TFC tier, that each pair lists exactly the recipes TFC lists for it, and the bonus rule.
 */
class WeldingGoldenTest {
    static final int TFC_WELDING_FILES = 87;

    static final List<WeldingRow> ROWS = AnvilFixtures.weldingRows();
    static final Map<String, Set<String>> TAGS = AnvilFixtures.tagMembers();
    static final WeldingRecipes RECIPES = new WeldingRecipes(ROWS.stream().map(WeldingRow::recipe).toList());

    @Test
    void rowCountMatchesTfcFileCount() {
        assertThat(AnvilFixtures.fileCount(AnvilFixtures.WELDING_TSV)).isEqualTo(TFC_WELDING_FILES);
        assertThat(ROWS).hasSize(AnvilFixtures.fileCount(AnvilFixtures.WELDING_TSV));
    }

    @Test
    void everyRecipeWeldsItsTfcInputsInEitherOrderAtItsTfcTier() {
        final var ctx = AnvilFixtures.context(TAGS);
        for (WeldingRow row : ROWS) {
            final WeldingRecipe recipe = row.recipe();
            final Set<String> firsts = AnvilFixtures.members(row.first(), TAGS);
            final Set<String> seconds = AnvilFixtures.members(row.second(), TAGS);
            assertThat(firsts).as(row.file()).isNotEmpty();
            assertThat(seconds).as(row.file()).isNotEmpty();
            for (String a : firsts) {
                for (String b : seconds) {
                    assertThat(recipe.matches(a, b, ctx)).as(row.file() + " " + a + "+" + b).isTrue();
                    assertThat(recipe.matches(b, a, ctx)).as(row.file() + " " + b + "+" + a).isTrue();
                }
            }
            assertThat(recipe.isCorrectTier(row.tier())).as(row.file()).isTrue();
            if (row.tier() > 0) {
                assertThat(recipe.isCorrectTier(row.tier() - 1)).as(row.file() + " below tier").isFalse();
            }
        }
    }

    @Test
    void eachPairListsExactlyTheRecipesTfcListsForIt() {
        final var ctx = AnvilFixtures.context(TAGS);
        final Set<String> items = new LinkedHashSet<>();
        for (WeldingRow row : ROWS) {
            items.addAll(AnvilFixtures.members(row.first(), TAGS));
            items.addAll(AnvilFixtures.members(row.second(), TAGS));
        }
        for (String a : items) {
            for (String b : items) {
                final List<String> expected = new ArrayList<>();
                for (WeldingRow row : ROWS) {
                    final Set<String> firsts = AnvilFixtures.members(row.first(), TAGS);
                    final Set<String> seconds = AnvilFixtures.members(row.second(), TAGS);
                    if ((firsts.contains(a) && seconds.contains(b)) || (firsts.contains(b) && seconds.contains(a))) {
                        expected.add(row.id());
                    }
                }
                final List<String> actual = RECIPES.candidates(a, b, ctx).stream().map(WeldingRecipe::id).toList();
                assertThat(actual).as(a + " + " + b).isEqualTo(expected);
            }
        }
    }

    @Test
    void everyRecipeGivesItsTfcResultAndBonusRule() {
        int copyBest = 0;
        int copyWorst = 0;
        int ignore = 0;
        for (WeldingRow row : ROWS) {
            final WeldingRecipe recipe = row.recipe();
            assertThat(recipe.output()).as(row.file()).isEqualTo(row.output());
            assertThat(recipe.bonus()).as(row.file()).isEqualTo(row.bonus());
            switch (recipe.bonus()) {
                case COPY_BEST -> copyBest++;
                case COPY_WORST -> copyWorst++;
                case IGNORE -> ignore++;
            }
        }
        assertThat(copyBest).isEqualTo(36);
        assertThat(copyWorst).isEqualTo(9);
        assertThat(ignore).isEqualTo(42);
    }

    @Test
    void firstMatchingRecipeIsTheFirstCandidate() {
        final var ctx = AnvilFixtures.context(TAGS);
        for (WeldingRow row : ROWS) {
            for (String a : AnvilFixtures.members(row.first(), TAGS)) {
                for (String b : AnvilFixtures.members(row.second(), TAGS)) {
                    final List<WeldingRecipe> candidates = RECIPES.candidates(a, b, ctx);
                    assertThat(RECIPES.find(a, b, ctx)).contains(candidates.get(0));
                }
            }
        }
    }

    @Test
    void bonusTableMatchesTfcCopyRules() {
        final ForgingBonus[] all = ForgingBonus.values();
        for (ForgingBonus main : all) {
            for (ForgingBonus secondary : all) {
                final ForgingBonus best = main.ordinal() >= secondary.ordinal() ? main : secondary;
                final ForgingBonus worst = main.ordinal() <= secondary.ordinal() ? main : secondary;
                assertThat(WeldingRecipe.Bonus.COPY_BEST.select(main, secondary)).isEqualTo(best);
                assertThat(WeldingRecipe.Bonus.COPY_WORST.select(main, secondary)).isEqualTo(worst);
                assertThat(WeldingRecipe.Bonus.IGNORE.select(main, secondary)).isEqualTo(ForgingBonus.NONE);
            }
        }
    }
}
