/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AnvilRecipe.java @ c3b4d9536 (matches, checkComplete, isCorrectTier,
 * isWorkMatched, shouldApplyForgingBonus and the bonus ratio of AnvilBlockEntity.work)
 */
package dev.tfp.core.anvil;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.forge.ForgingBonus;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import java.util.List;
import java.util.Objects;

/**
 * An anvil recipe: the input ingredient, the minimum anvil tier, the forging rules (the plan) the step history must satisfy, whether
 * the forging bonus applies, and the fixed result. Pure, immutable and thread-safe.
 *
 * <p>The work target is not computed here. TFC derives it from the world seed and the result item, and the caller supplies it, as
 * an {@code int}, to {@link #checkComplete(ForgingProgress, int, int)} and {@link #bonusFor(ForgingProgress, int, double[])}.
 *
 * @param id                 the recipe id, unique in its registry
 * @param input              the input ingredient
 * @param minTier            the minimum anvil tier, {@code >= 0}
 * @param rules              the forging rules; must be consistent per {@link ForgeRule#isConsistent(List)}
 * @param applyForgingBonus  whether finishing this recipe awards a forging bonus
 * @param output             the fixed result
 */
public record AnvilRecipe(String id, Ingredient input, int minTier, List<ForgeRule> rules, boolean applyForgingBonus,
                          CraftOutput output) {
    public AnvilRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        if (minTier < 0) {
            throw new IllegalArgumentException("minTier must be >= 0: " + minTier);
        }
        rules = List.copyOf(rules);
        if (!ForgeRule.isConsistent(rules)) {
            throw new IllegalArgumentException("The rules " + rules + " cannot be satisfied by any combination of steps");
        }
    }

    /**
     * @return {@code true} if {@code stack} can be started on an anvil of {@code anvilTier}. This is the decision used to list the
     *         recipes for an item: it depends on the input and the tier only, not on the work done.
     */
    public <S> boolean matches(S stack, int anvilTier, IngredientContext<S> ctx) {
        return input.test(stack, ctx) && isCorrectTier(anvilTier);
    }

    /**
     * @return {@code true} if an anvil of {@code anvilTier} can perform this recipe.
     */
    public boolean isCorrectTier(int anvilTier) {
        return anvilTier >= minTier;
    }

    /**
     * @return {@code true} if the step history satisfies the rules and the work offset is within {@code leeway} of {@code target}.
     */
    public boolean checkComplete(ForgingProgress progress, int target, int leeway) {
        return progress.steps().matches(rules) && isWorkMatched(progress.work(), target, leeway);
    }

    /**
     * The forging bonus for finishing with {@code progress}. The ratio is the steps taken divided by the fewest steps that reach
     * {@code target} under the rules, which is at least one for a finished item. TFC's thresholds are upper bounds on that ratio: a tier
     * is awarded when its threshold is strictly above the ratio, see {@link ForgingBonus#byRatio(float, double[])}.
     *
     * @param thresholds the threshold of each tier, indexed by {@link ForgingBonus#ordinal()}, as TFC's server config gives them
     * @return {@link ForgingBonus#NONE} when this recipe does not apply a bonus
     */
    public ForgingBonus bonusFor(ForgingProgress progress, int target, double[] thresholds) {
        if (!applyForgingBonus) {
            return ForgingBonus.NONE;
        }
        final float ratio = (float) progress.steps().total() / ForgeRule.calculateOptimalStepsToTarget(target, rules);
        return ForgingBonus.byRatio(ratio, thresholds);
    }

    private static boolean isWorkMatched(int work, int target, int leeway) {
        return work >= target - leeway && work <= target + leeway;
    }
}
