/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.cooking.SoupMix.Ingredient;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.recipe.food.FoodData;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Hand-traced soup mixes, each checked against TFC's {@code SoupPotRecipe.getOutput} at c3b4d9536. The expected values
 * are worked out by hand from the TFC expressions (not from the Java code): the base water 20 and saturation 2
 * ({@code float water = 20, saturation = 2}, L68), the servings {@code (int) (n / 2f) + 1} (L96, via
 * {@code ingredientsToServings}), the multiplier {@code 1.2f / outputCount} (L97), and the strict maximum (L100-110).
 */
class SoupMixGoldenTest {
    private static final float EPS = 1e-4f;

    /** A food with the given water, saturation and nutrients (grain, fruit, vegetables, protein, dairy). */
    private static Ingredient food(float water, float saturation, float grain, float fruit, float vegetables,
                                   float protein, float dairy) {
        return new Ingredient(new FoodData(0, water, saturation, 0, grain, fruit, vegetables, protein, dairy, 0f), false);
    }

    private static Ingredient rotten() {
        return new Ingredient(new FoodData(0, 10f, 10f, 0, 1f, 1f, 1f, 1f, 1f, 0f), true);
    }

    private static Ingredient zeroFood() {
        return food(0, 0, 0, 0, 0, 0, 0);
    }

    private static SoupMix.Soup soup(List<Ingredient> ingredients) {
        Optional<SoupMix.Soup> result = SoupMix.mix(ingredients);
        assertThat(result).as("a soup is made").isPresent();
        return result.get();
    }

    /** L67-68 and L94: no food, the count stays zero, the pot makes no soup. */
    @Test
    void noFoodMakesNoSoup() {
        assertThat(SoupMix.mix(List.of())).isEmpty();
    }

    /** L79-83: a rotten food resets the count to zero and stops the loop, whatever comes before or after it. */
    @Test
    void rottenIngredientEmptiesThePot() {
        assertThat(SoupMix.mix(List.of(food(10, 4, 1, 0, 0, 0, 0), rotten()))).isEmpty();
        assertThat(SoupMix.mix(List.of(rotten(), food(10, 4, 1, 0, 0, 0, 0)))).isEmpty();
        assertThat(SoupMix.mix(List.of(rotten()))).isEmpty();
    }

    /** L96: servings {@code (int) (n / 2f) + 1} for n = 1..6 gives 1, 2, 2, 3, 3, 4. */
    @Test
    void servingsFollowTfcIngredientsToServings() {
        int[] expected = {1, 2, 2, 3, 3, 4};
        for (int n = 1; n <= expected.length; n++) {
            List<Ingredient> ingredients = Collections.nCopies(n, zeroFood());
            assertThat(soup(ingredients).servings()).as("servings for %d ingredients", n).isEqualTo(expected[n - 1]);
        }
    }

    /**
     * L68-70 and L97-99, one ingredient: multiplier 1.2 (servings 1). Water 20 + 10 * 1.2 = 32, saturation 2 + 4 * 1.2
     * = 6.8, grain 1.0 * 1.2 = 1.2, the soup is grain. Hunger 4 and decay 3.5 (L50-51).
     */
    @Test
    void singleIngredientScalesByOnePointTwo() {
        SoupMix.Soup s = soup(List.of(food(10, 4, 1f, 0, 0, 0, 0)));
        assertThat(s.servings()).isEqualTo(1);
        assertThat(s.item()).isEqualTo(Nutrient.GRAIN);
        assertThat(s.food().hunger()).isEqualTo(4);
        assertThat(s.food().decayModifier()).isCloseTo(3.5f, within(EPS));
        assertThat(s.food().intoxication()).isZero();
        assertThat(s.food().water()).isCloseTo(32f, within(EPS));
        assertThat(s.food().saturation()).isCloseTo(6.8f, within(EPS));
        assertThat(s.food().grain()).isCloseTo(1.2f, within(EPS));
        assertThat(s.food().fruit()).isZero();
    }

    /**
     * Two ingredients: servings 2, multiplier 0.6. Water 20 + 16 * 0.6 = 29.6, saturation 2 + 6 * 0.6 = 5.6. Grain 1.0,
     * fruit 1.0 and vegetables 0.5 + 0.5 = 1.0 all scale to 0.6, a three-way tie, so the soup is grain (L106 is a
     * strict {@code >}, and GRAIN is the first nutrient scanned).
     */
    @Test
    void tieGoesToTheFirstNutrientInOrder() {
        SoupMix.Soup s = soup(List.of(food(10, 4, 1f, 0, 0.5f, 0, 0), food(6, 2, 0, 1f, 0.5f, 0, 0)));
        assertThat(s.servings()).isEqualTo(2);
        assertThat(s.item()).isEqualTo(Nutrient.GRAIN);
        assertThat(s.food().water()).isCloseTo(29.6f, within(EPS));
        assertThat(s.food().saturation()).isCloseTo(5.6f, within(EPS));
        assertThat(s.food().grain()).isCloseTo(0.6f, within(EPS));
        assertThat(s.food().fruit()).isCloseTo(0.6f, within(EPS));
        assertThat(s.food().vegetables()).isCloseTo(0.6f, within(EPS));
    }

    /**
     * Vegetables 1.5 + 0.5 = 2.0 scale to 2.0 * 0.6 = 1.2, grain 1.0 * 0.6 = 0.6: the largest is vegetables.
     */
    @Test
    void largestNutrientPicksTheItem() {
        SoupMix.Soup s = soup(List.of(food(0, 0, 0, 0, 1.5f, 0, 0), food(0, 0, 1f, 0, 0.5f, 0, 0)));
        assertThat(s.item()).isEqualTo(Nutrient.VEGETABLES);
        assertThat(s.food().vegetables()).isCloseTo(1.2f, within(EPS));
    }

    /** Protein 1.0 and dairy 1.0 both scale to 1.2: the earlier nutrient (protein, L104-109) wins the tie. */
    @Test
    void proteinBeatsDairyOnATie() {
        SoupMix.Soup s = soup(List.of(food(0, 0, 0, 0, 0, 1f, 1f)));
        assertThat(s.item()).isEqualTo(Nutrient.PROTEIN);
    }

    /** Dairy 2.0 scales to 2.4 against protein 1.2: dairy is the strict maximum. */
    @Test
    void dairyWinsWhenStrictlyLargest() {
        SoupMix.Soup s = soup(List.of(food(0, 0, 0, 0, 0, 1f, 2f)));
        assertThat(s.item()).isEqualTo(Nutrient.DAIRY);
        assertThat(s.food().dairy()).isCloseTo(2.4f, within(EPS));
        assertThat(s.food().protein()).isCloseTo(1.2f, within(EPS));
    }

    /**
     * No nutrient above zero (a food with only water): every nutrient stays zero, the maximum keeps its default GRAIN
     * (L100-101), and the soup is still made with water 20 + 5 * 1.2 = 26.
     */
    @Test
    void waterOnlyFoodDefaultsToGrain() {
        SoupMix.Soup s = soup(List.of(food(5, 0, 0, 0, 0, 0, 0)));
        assertThat(s.item()).isEqualTo(Nutrient.GRAIN);
        assertThat(s.food().water()).isCloseTo(26f, within(EPS));
        assertThat(s.food().grain()).isZero();
        assertThat(s.food().dairy()).isZero();
    }

    /**
     * Three ingredients with water 3 each: servings (int) (1.5) + 1 = 2, multiplier 0.6, water 20 + 9 * 0.6 = 25.4.
     * Saturation has no food input, so it stays at the base 2.
     */
    @Test
    void oddCountUsesTheRoundedDownServings() {
        SoupMix.Soup s = soup(List.of(food(3, 0, 0, 0, 0, 0, 0), food(3, 0, 0, 0, 0, 0, 0), food(3, 0, 0, 0, 0, 0, 0)));
        assertThat(s.servings()).isEqualTo(2);
        assertThat(s.food().water()).isCloseTo(25.4f, within(EPS));
        assertThat(s.food().saturation()).isCloseTo(2f, within(EPS));
    }

    /** The soup's food values are TFC's constants (L50-51, L68, L68 for saturation). */
    @Test
    void soupFoodUsesTfcConstants() {
        assertThat(PotVariantRules.SOUP_HUNGER_VALUE).isEqualTo(4);
        assertThat(PotVariantRules.SOUP_DECAY_MODIFIER).isEqualTo(3.5f);
        assertThat(PotVariantRules.SOUP_BASE_WATER).isEqualTo(20f);
        assertThat(PotVariantRules.SOUP_BASE_SATURATION).isEqualTo(2f);
        assertThat(PotVariantRules.SOUP_NUTRITION_MULTIPLIER).isEqualTo(1.2f);
        SoupMix.Soup s = soup(List.of(zeroFood()));
        assertThat(s.food().hunger()).isEqualTo(PotVariantRules.SOUP_HUNGER_VALUE);
        assertThat(s.food().decayModifier()).isEqualTo(PotVariantRules.SOUP_DECAY_MODIFIER);
        assertThat(s.food().water()).isEqualTo(PotVariantRules.SOUP_BASE_WATER);
        assertThat(s.food().saturation()).isEqualTo(PotVariantRules.SOUP_BASE_SATURATION);
    }
}
