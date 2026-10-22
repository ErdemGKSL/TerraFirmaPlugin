/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CastingRecipe.java @ c3b4d9536, CastingCraftingRecipe.java,
 * src/main/java/net/dries007/tfc/common/items/MoldItem.java (use, overrideOtherStackedOnMe)
 */
package dev.tfp.core.casting;

import java.util.List;
import java.util.Optional;

/**
 * The casting decisions: which recipe a mold matches, what it yields, and whether the mold breaks. Pure and thread-safe.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>A recipe matches when the mold item is the recipe's mold and the tank holds the recipe's fluid in at least the recipe's
 *       amount. Temperature is not part of the match: a molten mold is refused by the caller, as TFC does in its
 *       {@code isMolten} checks.</li>
 *   <li>The first matching recipe in lookup order is used. No two shipped recipes share a mold and fluid.</li>
 *   <li>The output is the recipe's result item in the recipe's count.</li>
 *   <li>Taking the result out of a mold (use or shift-use, or a crafting-grid craft) breaks it with the recipe's break chance.
 *       Use breaks when the roll is <em>below</em> the chance. A crafting craft keeps the mold when the roll is <em>above</em>
 *       the chance, so at a roll exactly equal to the chance the two paths disagree, as in TFC.</li>
 * </ul>
 *
 * <p>The random draw is a parameter, a value in {@code [0, 1)} such as {@code Random.nextFloat()}, so the decision is
 * deterministic and testable.
 */
public final class CastingRules {
    private CastingRules() {}

    /**
     * The item a casting yields.
     *
     * @param item  the result item id
     * @param count the number of items
     */
    public record Output(String item, int count) {}

    /**
     * TFC {@code CastingRecipe.matches}: whether the mold and its tank fit the recipe. The temperature is not checked here.
     *
     * @param recipe     the recipe to test
     * @param moldItem   the item id of the mold
     * @param tankFluid  the fluid id in the mold's tank, or empty when the tank is empty
     * @param tankAmount the amount in the mold's tank
     */
    public static boolean matches(CastingRecipe recipe, String moldItem, String tankFluid, int tankAmount) {
        return recipe.mold().equals(moldItem)
            && recipe.fluid().equals(tankFluid)
            && tankAmount >= recipe.fluidAmount();
    }

    /**
     * The first recipe, in the given order, that matches the mold and its tank.
     *
     * @param recipes    the casting recipes, in lookup order
     * @param moldItem   the item id of the mold
     * @param tankFluid  the fluid id in the mold's tank
     * @param tankAmount the amount in the mold's tank
     */
    public static Optional<CastingRecipe> find(List<CastingRecipe> recipes, String moldItem, String tankFluid, int tankAmount) {
        for (CastingRecipe recipe : recipes) {
            if (matches(recipe, moldItem, tankFluid, tankAmount)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /**
     * TFC {@code CastingRecipe.assemble}: the output of a matched recipe. Heat transfer from the mold is platform work and is
     * not part of this method.
     */
    public static Output assemble(CastingRecipe recipe) {
        return new Output(recipe.result(), recipe.resultCount());
    }

    /**
     * TFC {@code MoldItem.use} and {@code overrideOtherStackedOnMe}: whether the mold breaks when its result is taken out.
     *
     * @param recipe the recipe the mold matched
     * @param roll   a uniform draw in {@code [0, 1)}
     * @return {@code true} if the mold breaks ({@code roll < breakChance})
     */
    public static boolean breaksOnUse(CastingRecipe recipe, float roll) {
        return roll < recipe.breakChance();
    }

    /**
     * TFC {@code CastingCraftingRecipe.getRemainingItems}: whether a crafting-grid craft keeps the mold. A kept mold is drained;
     * a mold that is not kept is consumed. The caller handles a missing player, which TFC treats as not kept.
     *
     * @param recipe the recipe the mold matched
     * @param roll   a uniform draw in {@code [0, 1)}
     * @return {@code true} if the mold is kept ({@code roll > breakChance})
     */
    public static boolean keepsOnCraft(CastingRecipe recipe, float roll) {
        return roll > recipe.breakChance();
    }
}
