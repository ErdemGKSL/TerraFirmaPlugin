/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (work) and
 * src/main/java/net/dries007/tfc/common/recipes/AnvilRecipe.java (constructor validation, bonus)
 */
package dev.tfp.core.anvil;

import static dev.tfp.core.forge.ForgeStep.DRAW;
import static dev.tfp.core.forge.ForgeStep.HIT_LIGHT;
import static dev.tfp.core.forge.ForgeStep.PUNCH;
import static dev.tfp.core.forge.ForgeStep.SHRINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgeSteps;
import dev.tfp.core.forge.ForgingBonus;
import dev.tfp.core.recipe.ingredient.Ingredient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests of the anvil and welding decisions, with the TFC behaviour each one is taken from. */
class AnvilLogicTest {
    /** TFC's default thresholds, indexed by ordinal. NONE's entry is never read. */
    static final double[] TFC_THRESHOLDS = {Double.POSITIVE_INFINITY, 10.0, 5.0, 2.0, 1.5};
    static final CraftOutput OUT = new CraftOutput("tfc:thing", 1);

    @Test
    void untouchedWorkRefusesANegativeStep() {
        assertThat(ForgingProgress.EMPTY.canApply(HIT_LIGHT)).isFalse();
        assertThat(ForgingProgress.EMPTY.canApply(DRAW)).isFalse();
        assertThat(ForgingProgress.EMPTY.canApply(PUNCH)).isTrue();
        assertThat(ForgingProgress.EMPTY.canApply(SHRINK)).isTrue();

        final ForgingProgress worked = ForgingProgress.EMPTY.apply(PUNCH);
        assertThat(worked.canApply(HIT_LIGHT)).isTrue();
    }

    @Test
    void workOutsideZeroToLimitDestroysTheItem() {
        assertThat(new ForgingProgress(ForgeSteps.EMPTY, -1).isDestroyed()).isTrue();
        assertThat(new ForgingProgress(ForgeSteps.EMPTY, 0).isDestroyed()).isFalse();
        assertThat(new ForgingProgress(ForgeSteps.EMPTY, ForgeStep.LIMIT).isDestroyed()).isFalse();
        assertThat(new ForgingProgress(ForgeSteps.EMPTY, ForgeStep.LIMIT + 1).isDestroyed()).isTrue();

        ForgingProgress progress = ForgingProgress.EMPTY;
        for (int i = 0; i < 4; i++) {
            progress = progress.apply(SHRINK);
        }
        assertThat(progress.work()).isEqualTo(64);
        assertThat(progress.isDestroyed()).isFalse();
    }

    @Test
    void recipeRejectsRulesNoStepSequenceSatisfies() {
        assertThatThrownBy(() -> new AnvilRecipe("x", new Ingredient.Item("a"), 0, List.of(ForgeRule.HIT_LAST, ForgeRule.DRAW_LAST),
            false, OUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnvilRecipe("x", new Ingredient.Item("a"), -1, List.of(ForgeRule.HIT_ANY), false, OUT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workTargetLeewayIsInclusiveOnBothSides() {
        final AnvilRecipe recipe = new AnvilRecipe("x", new Ingredient.Item("a"), 0, List.of(ForgeRule.PUNCH_LAST), false, OUT);
        final ForgingProgress at20 = new ForgingProgress(new ForgeSteps(List.of(ForgeStep.PUNCH), 1), 20);

        assertThat(recipe.checkComplete(at20, 20, 0)).isTrue();
        assertThat(recipe.checkComplete(at20, 22, 2)).isTrue();
        assertThat(recipe.checkComplete(at20, 18, 2)).isTrue();
        assertThat(recipe.checkComplete(at20, 23, 2)).isFalse();
        assertThat(recipe.checkComplete(at20, 17, 2)).isFalse();
    }

    @Test
    void bonusIsNoneWhenTheRecipeDoesNotApplyIt() {
        final AnvilRecipe recipe = new AnvilRecipe("x", new Ingredient.Item("a"), 0, List.of(ForgeRule.PUNCH_LAST), false, OUT);
        final ForgingProgress perfect = new ForgingProgress(new ForgeSteps(List.of(ForgeStep.PUNCH), 1), 20);
        assertThat(recipe.bonusFor(perfect, 20, TFC_THRESHOLDS)).isEqualTo(ForgingBonus.NONE);
    }

    @Test
    void bonusFollowsTfcRatioThresholds() {
        final AnvilRecipe recipe = new AnvilRecipe("x", new Ingredient.Item("a"), 0, List.of(ForgeRule.PUNCH_LAST), true, OUT);
        final int target = 20;
        final int optimal = ForgeRule.calculateOptimalStepsToTarget(target, recipe.rules());
        assertThat(optimal).isPositive();

        // Ratio 1.0 is under the PERFECT threshold of 1.5.
        final ForgingProgress exact = new ForgingProgress(new ForgeSteps(List.of(), optimal), target);
        assertThat(recipe.bonusFor(exact, target, TFC_THRESHOLDS)).isEqualTo(ForgingBonus.PERFECT);

        // Ratio 2.0 is not under PERFECT (1.5) or EXPERT (2.0, strictly), but is under WELL (5.0).
        final ForgingProgress twice = new ForgingProgress(new ForgeSteps(List.of(), 2 * optimal), target);
        assertThat(recipe.bonusFor(twice, target, TFC_THRESHOLDS)).isEqualTo(ForgingBonus.WELL);
    }

    @Test
    void defaultPlanIsTheOnlyCandidateOrTheRememberedOne() {
        final AnvilRecipe a = new AnvilRecipe("tfc:anvil/a", new Ingredient.Item("i"), 0, List.of(ForgeRule.HIT_ANY), false, OUT);
        final AnvilRecipe b = new AnvilRecipe("tfc:anvil/b", new Ingredient.Item("i"), 0, List.of(ForgeRule.HIT_ANY), false, OUT);

        assertThat(AnvilRecipes.defaultFor(List.of(a), null)).contains(a);
        assertThat(AnvilRecipes.defaultFor(List.of(a, b), null)).isEmpty();
        assertThat(AnvilRecipes.defaultFor(List.of(a, b), "tfc:anvil/b")).contains(b);
        assertThat(AnvilRecipes.defaultFor(List.of(a, b), "tfc:anvil/c")).isEmpty();
        assertThat(AnvilRecipes.defaultFor(List.of(), "tfc:anvil/a")).isEmpty();
    }

    @Test
    void registryRejectsDuplicateIds() {
        final AnvilRecipe a = new AnvilRecipe("tfc:anvil/a", new Ingredient.Item("i"), 0, List.of(ForgeRule.HIT_ANY), false, OUT);
        assertThatThrownBy(() -> new AnvilRecipes(List.of(a, a))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weldingRecipeHonoursItsTier() {
        final WeldingRecipe welding = new WeldingRecipe("tfc:welding/x", new Ingredient.Item("a"), new Ingredient.Item("b"), 2, OUT,
            WeldingRecipe.Bonus.IGNORE);
        assertThat(welding.isCorrectTier(1)).isFalse();
        assertThat(welding.isCorrectTier(2)).isTrue();
        assertThat(welding.resultBonus(ForgingBonus.PERFECT, ForgingBonus.NONE)).isEqualTo(ForgingBonus.NONE);
    }

    @Test
    void weldingFirstMatchIgnoresTierLikeTfc() {
        final WeldingRecipe welding = new WeldingRecipe("tfc:welding/x", new Ingredient.Item("a"), new Ingredient.Item("b"), 5, OUT,
            WeldingRecipe.Bonus.IGNORE);
        final var ctx = AnvilFixtures.context(Map.of());
        final Optional<WeldingRecipe> found = new WeldingRecipes(List.of(welding)).find("b", "a", ctx);
        assertThat(found).contains(welding);
        assertThat(found.orElseThrow().isCorrectTier(0)).isFalse();
    }

    @Test
    void forgingProgressAppliesTheStepToTheOffset() {
        final ForgingProgress progress = ForgingProgress.EMPTY.apply(SHRINK).apply(DRAW);
        assertThat(progress.work()).isEqualTo(16 - 15);
        assertThat(progress.steps().total()).isEqualTo(2);
    }
}
