/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/WeldingRecipe.java @ c3b4d9536 (matches, isCorrectTier, the bonus copy rule of
 * assemble, and the Behavior enum)
 */
package dev.tfp.core.anvil;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.forge.ForgingBonus;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import java.util.Objects;

/**
 * A welding recipe: two input ingredients (either order), the minimum anvil tier, a fixed result and the forging bonus rule.
 * Pure, immutable and thread-safe.
 *
 * @param id     the recipe id, unique in its registry
 * @param first  the first input ingredient
 * @param second the second input ingredient
 * @param tier   the minimum anvil tier, {@code >= 0}
 * @param output the fixed result
 * @param bonus  how the forging bonus of the result is chosen from the two inputs
 */
public record WeldingRecipe(String id, Ingredient first, Ingredient second, int tier, CraftOutput output, Bonus bonus) {
    public WeldingRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(bonus, "bonus");
        if (tier < 0) {
            throw new IllegalArgumentException("tier must be >= 0: " + tier);
        }
    }

    /**
     * @return {@code true} if the two stacks fill the two inputs in either order. Like TFC, the anvil tier is not checked here, see
     *         {@link #isCorrectTier(int)}.
     */
    public <S> boolean matches(S main, S secondary, IngredientContext<S> ctx) {
        return (first.test(main, ctx) && second.test(secondary, ctx))
            || (first.test(secondary, ctx) && second.test(main, ctx));
    }

    /**
     * @return {@code true} if an anvil of {@code anvilTier} can perform this recipe.
     */
    public boolean isCorrectTier(int anvilTier) {
        return anvilTier >= tier;
    }

    /**
     * The forging bonus of the welded result, from the bonuses of the two inputs.
     *
     * @param mainBonus      the bonus of the main input (the stack in the anvil's first slot)
     * @param secondaryBonus the bonus of the secondary input
     */
    public ForgingBonus resultBonus(ForgingBonus mainBonus, ForgingBonus secondaryBonus) {
        return bonus.select(mainBonus, secondaryBonus);
    }

    /**
     * How the forging bonus of a welded result is chosen. TFC's {@code WeldingRecipe.Behavior}.
     */
    public enum Bonus {
        /** Copy the best of both inputs. If one input has no bonus, the other is copied. */
        COPY_BEST,
        /** Copy the worst of both inputs. If one input has no bonus, no bonus is present. */
        COPY_WORST,
        /** No bonus on the output. */
        IGNORE;

        /**
         * @return the bonus of the input this rule copies. When both inputs have the same bonus, the result is that bonus, so the
         *         choice of input does not matter.
         */
        public ForgingBonus select(ForgingBonus main, ForgingBonus secondary) {
            if (this == IGNORE) {
                return ForgingBonus.NONE;
            }
            final boolean mainIsHigher = main.ordinal() > secondary.ordinal();
            final boolean copyHigher = this == COPY_BEST;
            return mainIsHigher == copyHigher ? main : secondary;
        }
    }
}
