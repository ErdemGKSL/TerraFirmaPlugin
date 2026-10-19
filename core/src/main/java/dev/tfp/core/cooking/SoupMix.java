/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/SoupPotRecipe.java @ c3b4d9536 (getOutput: the ingredient loop,
 *       the rotten break, the per-serving multiplier, the strict maximum nutrient that picks the soup item)
 */
package dev.tfp.core.cooking;

import dev.tfp.core.food.Nutrient;
import dev.tfp.core.recipe.food.FoodData;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The nutrition mix of the pot soup ({@code tfc:pot_soup}): how the soup's food values and its output item are chosen
 * from the food ingredients that were put in the pot. Pure: the caller decides which slots hold food and whether each
 * food is rotten (TFC's {@code FoodCapability.get} and {@code IFood.isRotten} at the current time), and passes only the
 * food ingredients, in slot order.
 *
 * <p>The mix, as TFC's {@code SoupPotRecipe.getOutput}:
 * <ul>
 *     <li>The soup starts with {@link PotVariantRules#SOUP_BASE_WATER} water and {@link PotVariantRules#SOUP_BASE_SATURATION}
 *         saturation. Each food adds its water and saturation to a running total, and its five nutrients to five running
 *         sums, in slot order.</li>
 *     <li>A rotten ingredient makes the whole pot empty (TFC resets the count to zero and stops the loop).</li>
 *     <li>No food, no soup: the count is zero and the pot makes nothing.</li>
 *     <li>The servings are {@link PotVariantRules#soupServings}. The totals are scaled by {@code 1.2f / servings}: the
 *         water and saturation are added to the base values, and each nutrient sum is multiplied by the same factor.</li>
 *     <li>The soup item is the nutrient with the largest scaled value. The scan is in {@link Nutrient} order and replaces
 *         the best only on a strictly larger value, so a tie goes to the earlier nutrient; with no nutrient above zero the
 *         default is {@link Nutrient#GRAIN}.</li>
 *     <li>The soup food is hunger {@link PotVariantRules#SOUP_HUNGER_VALUE}, the mixed water and saturation, no
 *         intoxication, the scaled nutrients and decay modifier {@link PotVariantRules#SOUP_DECAY_MODIFIER}.</li>
 * </ul>
 *
 * <p>The float arithmetic follows TFC's expressions one by one, so the results are bit-exact with TFC's.
 */
public final class SoupMix {
    private SoupMix() {
    }

    /**
     * One food ingredient as the mix sees it.
     *
     * @param food   the food values of the ingredient (TFC {@code FoodData} of the stack)
     * @param rotten whether the ingredient is rotten at the time of cooking
     */
    public record Ingredient(FoodData food, boolean rotten) {
        public Ingredient {
            Objects.requireNonNull(food, "food");
        }
    }

    /**
     * The soup a pot makes.
     *
     * @param servings the stack count of the soup item, {@code >= 1}
     * @param item     the soup item, chosen by the largest nutrient
     * @param food     the soup's food values
     */
    public record Soup(int servings, Nutrient item, FoodData food) {
    }

    /**
     * Mixes the food ingredients of a pot into a soup.
     *
     * @param ingredients the food ingredients in slot order; non-food stacks are not passed
     * @return the soup, or empty when there is no food or a rotten food (TFC leaves the output empty)
     */
    public static Optional<Soup> mix(List<Ingredient> ingredients) {
        Objects.requireNonNull(ingredients, "ingredients");
        int ingredientCount = 0;
        float water = PotVariantRules.SOUP_BASE_WATER;
        float saturation = PotVariantRules.SOUP_BASE_SATURATION;
        float extraWater = 0;
        float extraSaturation = 0;
        float[] nutrition = new float[Nutrient.TOTAL];
        for (Ingredient ingredient : ingredients) {
            if (ingredient.rotten()) {
                ingredientCount = 0;
                break;
            }
            final FoodData data = ingredient.food();
            extraWater += data.water();
            extraSaturation += data.saturation();
            for (Nutrient nutrient : Nutrient.VALUES) {
                nutrition[nutrient.ordinal()] += nutrient.of(data);
            }
            ingredientCount++;
        }
        if (ingredientCount == 0) {
            return Optional.empty();
        }

        final int servings = PotVariantRules.soupServings(ingredientCount);
        final float multiplier = PotVariantRules.SOUP_NUTRITION_MULTIPLIER / servings; // per-serving multiplier
        water += extraWater * multiplier;
        saturation += extraSaturation * multiplier;

        Nutrient maxNutrient = Nutrient.GRAIN; // the default when no nutrient is above zero
        float maxNutrientValue = 0;
        for (Nutrient nutrient : Nutrient.VALUES) {
            final int idx = nutrient.ordinal();
            nutrition[idx] *= multiplier;
            if (nutrition[idx] > maxNutrientValue) {
                maxNutrientValue = nutrition[idx];
                maxNutrient = nutrient;
            }
        }

        final FoodData food = new FoodData(
                PotVariantRules.SOUP_HUNGER_VALUE, water, saturation, 0,
                nutrition[Nutrient.GRAIN.ordinal()], nutrition[Nutrient.FRUIT.ordinal()],
                nutrition[Nutrient.VEGETABLES.ordinal()], nutrition[Nutrient.PROTEIN.ordinal()],
                nutrition[Nutrient.DAIRY.ordinal()], PotVariantRules.SOUP_DECAY_MODIFIER);
        return Optional.of(new Soup(servings, maxNutrient, food));
    }
}
