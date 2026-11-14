/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SaladContainer.java @ c3b4d9536 (setAndUpdateSlots: the
 *       ingredient loop, the rotten break, the minimum stack count, the 0.75 salad multiplier, the strict maximum
 *       nutrient that picks the salad item, the salad food data; onSlotTake: every input and the bowls shrink by the
 *       amount taken)
 */
package dev.tfp.core.salad;

import dev.tfp.core.food.Nutrient;
import dev.tfp.core.recipe.food.FoodData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The salad a bowl and up to five ingredients make in TFC's salad screen. Pure: the caller decides which input slots
 * hold food (TFC {@code FoodCapability.get} not null) and whether each food is rotten at the current time, and passes
 * only those, in slot order, with their stack counts.
 *
 * <p>The mix, as TFC's {@code SaladContainer.setAndUpdateSlots}:
 * <ul>
 *     <li>No bowl, no salad.</li>
 *     <li>Each food adds its water and saturation to a running total and its five nutrients to five running sums, in
 *         slot order. A rotten food stops the loop and makes nothing (TFC resets the count to zero).</li>
 *     <li>The salad count is the smallest stack count among the foods and the bowls, starting from 64.</li>
 *     <li>Water, saturation and every nutrient are multiplied by {@link #MULTIPLIER}.</li>
 *     <li>The salad item is the nutrient with the largest value, scanned in {@link Nutrient} order and replaced only on a
 *         strictly larger value (a tie goes to the earlier nutrient). With no nutrient above zero there is no salad
 *         (unlike the soup, which falls back to grain).</li>
 *     <li>The salad's food is hunger {@link #HUNGER}, the mixed water and saturation, no intoxication, the mixed
 *         nutrients and decay modifier {@link #DECAY_MODIFIER}.</li>
 * </ul>
 *
 * <p>The float arithmetic follows TFC's expressions one by one, so the results are bit-exact with TFC's.
 */
public final class SaladMix {
    /** TFC {@code final float multiplier = 0.75f; // salad multiplier}. */
    public static final float MULTIPLIER = 0.75f;
    /** The salad's hunger ({@code new FoodData(4, ...)}). */
    public static final int HUNGER = 4;
    /** The salad's decay modifier ({@code new FoodData(..., 4.0f)}). */
    public static final float DECAY_MODIFIER = 4.0f;
    /** TFC's starting value of {@code minIngredientCount}. */
    public static final int MAX_COUNT = 64;

    private SaladMix() {
    }

    /**
     * One food stack in an input slot.
     *
     * @param slot   the TFC input slot index, {@link SaladScreen#SLOT_INPUT_START}..{@link SaladScreen#SLOT_INPUT_END}
     * @param food   the food values of the stack
     * @param rotten whether the stack is rotten now
     * @param count  the stack count, {@code >= 1}
     */
    public record Ingredient(int slot, FoodData food, boolean rotten, int count) {
        public Ingredient {
            Objects.requireNonNull(food, "food");
            if (count < 1) {
                throw new IllegalArgumentException("count " + count);
            }
        }
    }

    /**
     * The salad the screen offers.
     *
     * @param count       the stack count of the salad
     * @param item        the salad item, chosen by the largest nutrient ({@code tfp:food/<nutrient>_salad})
     * @param food        the salad's food values
     * @param ingredients the input slots whose stacks went in, in slot order (TFC's {@code INGREDIENTS} list)
     */
    public record Salad(int count, Nutrient item, FoodData food, List<Integer> ingredients) {
        public Salad {
            ingredients = List.copyOf(ingredients);
        }

        /** The CraftEngine id of the salad item. */
        public String itemId() {
            return SaladMix.itemId(item);
        }
    }

    /** {@code tfp:food/<nutrient>_salad} (TFC {@code TFCItems.SALADS}). */
    public static String itemId(Nutrient nutrient) {
        return "tfp:food/" + nutrient.serializedName() + "_salad";
    }

    /**
     * Mixes a salad.
     *
     * @param ingredients the food stacks of the input slots, in slot order; empty and non-food slots are not passed
     * @param bowls       the stack count of the bowl slot, 0 when it is empty
     * @return the salad, or empty when TFC's output slot would be empty
     */
    public static Optional<Salad> mix(List<Ingredient> ingredients, int bowls) {
        Objects.requireNonNull(ingredients, "ingredients");
        if (bowls <= 0) {
            return Optional.empty();
        }
        float water = 0;
        float saturation = 0;
        float[] nutrition = new float[Nutrient.TOTAL];
        int ingredientCount = 0;
        int minIngredientCount = MAX_COUNT;
        List<Integer> used = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            used.add(ingredient.slot());
            if (ingredient.rotten()) {
                ingredientCount = 0; // rotten food is not allowed
                break;
            }
            final FoodData data = ingredient.food();
            water += data.water();
            saturation += data.saturation();
            for (Nutrient nutrient : Nutrient.VALUES) {
                nutrition[nutrient.ordinal()] += nutrient.of(data);
            }
            ingredientCount++;
            if (ingredient.count() < minIngredientCount) {
                minIngredientCount = ingredient.count();
            }
        }
        if (bowls < minIngredientCount) {
            minIngredientCount = bowls;
        }
        if (ingredientCount <= 0) {
            return Optional.empty();
        }

        water *= MULTIPLIER;
        saturation *= MULTIPLIER;
        Nutrient maxNutrient = null;
        float maxNutrientValue = 0;
        for (Nutrient nutrient : Nutrient.VALUES) {
            final int idx = nutrient.ordinal();
            nutrition[idx] *= MULTIPLIER;
            if (nutrition[idx] > maxNutrientValue) {
                maxNutrientValue = nutrition[idx];
                maxNutrient = nutrient;
            }
        }
        if (maxNutrient == null) {
            return Optional.empty();
        }
        final FoodData food = new FoodData(HUNGER, water, saturation, 0,
                nutrition[Nutrient.GRAIN.ordinal()], nutrition[Nutrient.FRUIT.ordinal()],
                nutrition[Nutrient.VEGETABLES.ordinal()], nutrition[Nutrient.PROTEIN.ordinal()],
                nutrition[Nutrient.DAIRY.ordinal()], DECAY_MODIFIER);
        return Optional.of(new Salad(minIngredientCount, maxNutrient, food, used));
    }

    /**
     * TFC {@code onSlotTake}: what is left of a stack of {@code count} in an input or bowl slot after {@code taken}
     * salads left the output slot. Never below zero.
     */
    public static int afterTake(int count, int taken) {
        return Math.max(0, count - Math.max(0, taken));
    }
}
