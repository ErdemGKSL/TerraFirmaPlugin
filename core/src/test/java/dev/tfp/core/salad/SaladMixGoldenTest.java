/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.salad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.food.Nutrient;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.salad.SaladMix.Ingredient;
import dev.tfp.core.salad.SaladMix.Salad;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden salads against TFC's {@code SaladContainer.setAndUpdateSlots} at c3b4d9536. The expected floats come from a
 * scratch transcription of TFC's loop (kept outside the repo) run on TFC's own food data
 * ({@code data/tfc/tfc/food/<food>.json}); every value is compared bit-exact.
 */
class SaladMixGoldenTest {
    // TFC food data: water, saturation, grain, fruit, vegetables, protein, dairy
    private static final FoodData CABBAGE = food(0, 1.0f, 0, 0, 2.0f, 0, 0, 1.2f);
    private static final FoodData CARROT = food(0, 2.0f, 0, 0, 1.5f, 0, 0, 0.5f);
    private static final FoodData RED_APPLE = food(0, 0.2f, 0, 0.9f, 0, 0, 0, 1.7f);
    private static final FoodData COOKED_BEEF = food(0, 2.0f, 0, 0, 0, 2.5f, 0, 1.5f);
    private static final FoodData GARLIC = food(0, 0.5f, 0, 0, 2.5f, 0, 0, 0.4f);
    private static final FoodData TOMATO = food(5.0f, 1.0f, 0, 0, 2.0f, 0, 0, 3.5f);
    private static final FoodData BANANA = food(0, 0.2f, 0, 1.0f, 0, 0, 0, 2.0f);
    private static final FoodData CHEESE = food(0, 2.0f, 0, 0, 0, 0, 3.0f, 0.3f);

    private static FoodData food(float water, float saturation, float grain, float fruit, float vegetables,
                                 float protein, float dairy, float decay) {
        return new FoodData(4, water, saturation, 0, grain, fruit, vegetables, protein, dairy, decay);
    }

    private static Ingredient at(int slot, FoodData food, int count) {
        return new Ingredient(slot, food, false, count);
    }

    private static void assertSalad(Optional<Salad> salad, int count, Nutrient item, float water, float saturation,
                                    float grain, float fruit, float vegetables, float protein, float dairy) {
        assertThat(salad).isPresent();
        Salad s = salad.get();
        assertThat(s.count()).isEqualTo(count);
        assertThat(s.item()).isEqualTo(item);
        FoodData f = s.food();
        assertThat(f.hunger()).isEqualTo(4);
        assertThat(f.intoxication()).isZero();
        assertThat(f.decayModifier()).isEqualTo(4.0f);
        assertThat(f.water()).isEqualTo(water);
        assertThat(f.saturation()).isEqualTo(saturation);
        assertThat(f.grain()).isEqualTo(grain);
        assertThat(f.fruit()).isEqualTo(fruit);
        assertThat(f.vegetables()).isEqualTo(vegetables);
        assertThat(f.protein()).isEqualTo(protein);
        assertThat(f.dairy()).isEqualTo(dairy);
    }

    @Test
    void cabbageAndCarrotMakeAVegetableSaladOfTheSmallestStack() {
        Optional<Salad> s = SaladMix.mix(List.of(at(0, CABBAGE, 5), at(1, CARROT, 3)), 10);
        assertSalad(s, 3, Nutrient.VEGETABLES, 0.0f, 2.25f, 0.0f, 0.0f, 2.625f, 0.0f, 0.0f);
        assertThat(s.get().itemId()).isEqualTo("tfp:food/vegetables_salad");
        assertThat(s.get().ingredients()).containsExactly(0, 1);
    }

    @Test
    void oneBowlMakesOneFruitSalad() {
        assertSalad(SaladMix.mix(List.of(at(0, RED_APPLE, 2), at(3, BANANA, 2)), 1),
            1, Nutrient.FRUIT, 0.0f, 0.3f, 0.0f, 1.425f, 0.0f, 0.0f, 0.0f);
    }

    @Test
    void fiveIngredientsSumInSlotOrder() {
        Optional<Salad> s = SaladMix.mix(List.of(at(0, COOKED_BEEF, 4), at(1, CABBAGE, 7), at(2, TOMATO, 9),
            at(3, GARLIC, 12), at(4, RED_APPLE, 6)), 32);
        assertSalad(s, 4, Nutrient.VEGETABLES, 3.75f, 3.5249999f, 0.0f, 0.67499995f, 4.875f, 1.875f, 0.0f);
        assertThat(s.get().ingredients()).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void cheeseOutweighsCarrot() {
        Optional<Salad> s = SaladMix.mix(List.of(at(1, CHEESE, 1), at(4, CARROT, 8)), 16);
        assertSalad(s, 1, Nutrient.DAIRY, 0.0f, 3.0f, 0.0f, 0.0f, 1.125f, 0.0f, 2.25f);
        assertThat(s.get().itemId()).isEqualTo("tfp:food/dairy_salad");
    }

    @Test
    void aTieGoesToTheEarlierNutrient() {
        FoodData even = food(0, 0, 0, 1f, 1f, 0, 0, 1f);
        assertSalad(SaladMix.mix(List.of(at(2, even, 3)), 3), 3, Nutrient.FRUIT,
            0.0f, 0.0f, 0.0f, 0.75f, 0.75f, 0.0f, 0.0f);
    }

    @Test
    void aRottenIngredientMakesNothing() {
        assertThat(SaladMix.mix(List.of(at(0, CABBAGE, 5), new Ingredient(1, CARROT, true, 3)), 10)).isEmpty();
        assertThat(SaladMix.mix(List.of(new Ingredient(0, CARROT, true, 3), at(1, CABBAGE, 5)), 10)).isEmpty();
    }

    @Test
    void noNutrientsMakesNothing() {
        assertThat(SaladMix.mix(List.of(at(0, food(1f, 1f, 0, 0, 0, 0, 0, 1f), 3)), 3)).isEmpty();
    }

    @Test
    void noBowlOrNoFoodMakesNothing() {
        assertThat(SaladMix.mix(List.of(at(0, CABBAGE, 5)), 0)).isEmpty();
        assertThat(SaladMix.mix(List.of(), 5)).isEmpty();
    }

    @Test
    void theCountIsCappedAtSixtyFour() {
        assertThat(SaladMix.mix(List.of(at(0, CABBAGE, 64)), 64).orElseThrow().count()).isEqualTo(64);
    }

    @Test
    void takingShrinksEveryStackByTheAmountTaken() {
        assertThat(SaladMix.afterTake(5, 3)).isEqualTo(2);
        assertThat(SaladMix.afterTake(3, 3)).isZero();
        assertThat(SaladMix.afterTake(2, 3)).isZero();
        assertThat(SaladMix.afterTake(4, -1)).isEqualTo(4);
    }

    @Test
    void ingredientCountMustBePositive() {
        assertThatThrownBy(() -> new Ingredient(0, CABBAGE, false, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void screenSlotsMatchTfcColumnsOnChestRows() {
        for (int i = SaladScreen.SLOT_INPUT_START; i <= SaladScreen.SLOT_INPUT_END; i++) {
            int chest = SaladScreen.chestSlot(i);
            assertThat(SaladScreen.CHEST.slotX(chest)).isEqualTo(44 + 18 * i); // TFC x
            assertThat(SaladScreen.CHEST.slotY(chest)).isEqualTo(24 - 6);
            assertThat(SaladScreen.tfcSlot(chest)).isEqualTo(i);
        }
        assertThat(SaladScreen.CHEST.slotX(SaladScreen.chestSlot(SaladScreen.SLOT_BOWLS))).isEqualTo(44);
        assertThat(SaladScreen.CHEST.slotX(SaladScreen.chestSlot(SaladScreen.SLOT_OUTPUT))).isEqualTo(116);
        assertThat(SaladScreen.CHEST.slotY(SaladScreen.OUTPUT_CHEST_SLOT)).isEqualTo(54);
        assertThat(SaladScreen.tfcSlot(0)).isEqualTo(-1);
        assertThatThrownBy(() -> SaladScreen.chestSlot(7)).isInstanceOf(IllegalArgumentException.class);
    }
}
