/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java @ c3b4d9536 (ingredientsToServings,
 *       SOUP_HUNGER_VALUE, SOUP_DECAY_MODIFIER, the base water and saturation, the per-serving multiplier),
 *       src/main/java/net/dries007/tfc/common/recipes/JamPotRecipe.java @ c3b4d9536 (sealed and unsealed results)
 */
package dev.tfp.core.cooking;

/**
 * The timing and output rules of the pot soup ({@code tfc:pot_soup}) and pot jam ({@code tfc:pot_jam}) recipes.
 *
 * <p>Timing is not a separate rule: a soup or jam recipe boils with {@link PotRules} and its own duration and temperature
 * from the recipe data, the same live counter as {@code tfc:pot}. This class holds what differs: the soup serving count
 * from the number of ingredients, the soup food constants, and the jam jar count, which the sealed and unsealed results
 * must share.
 */
public final class PotVariantRules {
    private PotVariantRules() {
    }

    /** {@code SoupPotRecipe.SOUP_HUNGER_VALUE}. */
    public static final int SOUP_HUNGER_VALUE = 4;

    /** {@code SoupPotRecipe.SOUP_DECAY_MODIFIER}. */
    public static final float SOUP_DECAY_MODIFIER = 3.5F;

    /** The water of a soup before its ingredients add theirs, from {@code SoupPotRecipe.getOutput}. */
    public static final float SOUP_BASE_WATER = 20F;

    /** The saturation of a soup before its ingredients add theirs, from {@code SoupPotRecipe.getOutput}. */
    public static final float SOUP_BASE_SATURATION = 2F;

    /** The numerator of the per-serving multiplier of the ingredients' food data, {@code 1.2f / outputCount}. */
    public static final float SOUP_NUTRITION_MULTIPLIER = 1.2F;

    /**
     * The servings a pot soup yields for a count of usable ingredients, {@code SoupPotRecipe.ingredientsToServings}:
     * {@code (int) (ingredientCount / 2f) + 1}. Zero ingredients give one serving here, but the pot makes no soup then
     * (the caller checks that the count is positive before it calls this, as TFC does).
     *
     * @param ingredientCount the number of ingredients that are food, {@code >= 0}
     * @return the stack count of the soup
     */
    public static int soupServings(int ingredientCount) {
        if (ingredientCount < 0) {
            throw new IllegalArgumentException("ingredientCount must be >= 0: " + ingredientCount);
        }
        return (int) (ingredientCount / 2f) + 1;
    }

    /**
     * The jar count of a pot jam. TFC's jam recipes give the same count for the sealed and the unsealed result; a recipe
     * where they differ is malformed, so this rule refuses it.
     *
     * @param sealedCount   the count of the sealed result, {@code > 0}
     * @param unsealedCount the count of the unsealed result, {@code > 0}
     * @return the jar count
     */
    public static int jamJarCount(int sealedCount, int unsealedCount) {
        if (sealedCount <= 0 || unsealedCount <= 0) {
            throw new IllegalArgumentException("jar counts must be > 0: " + sealedCount + ", " + unsealedCount);
        }
        if (sealedCount != unsealedCount) {
            throw new IllegalArgumentException("sealed and unsealed counts differ: " + sealedCount + " != " + unsealedCount);
        }
        return sealedCount;
    }
}
