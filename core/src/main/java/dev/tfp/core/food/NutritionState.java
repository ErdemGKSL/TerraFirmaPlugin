/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/NutritionData.java @ c3b4d9536
 */
package dev.tfp.core.food;

import dev.tfp.core.recipe.food.FoodData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A player's nutrition records (TFC {@code NutritionData}): the last foods eaten, newest first. Only the
 * records are stored; the nutrient values are derived from them and the player's current hunger
 * ({@link #levels}), so nutrition "decays" lazily as hunger is used up, with no ticking.
 *
 * <p><b>The rule (TFC).</b> The window holds {@code hungerWindow} (80) hunger points of eaten food. Hunger the
 * player has lost ({@code 20 - hunger}) counts as a zero-nutrient gap at the front of the window, then each
 * record adds {@code nutrient * max(hunger, 1)}; the record that crosses the window end adds only the part that
 * fits, and the records after it are dropped. The sums are divided by the window; any part of the window not
 * filled by the gap and the records is filled with the default values (0.5, dairy 0); each value is capped at 1.
 * TFC quirk kept: when a record crosses the window end the default share for the gap before it is still added.
 *
 * <p><b>Lazy equals stepped.</b> Between meals hunger only falls, so the records kept at a lower hunger are a
 * superset of those needed at any later (lower) hunger: evaluating once at the current hunger gives the same
 * values and records as TFC recomputing every tick. Callers must apply {@link #truncated} at the hunger of each
 * meal (TFC computes before and after eating) and whenever hunger rises some other way.
 *
 * <p>Float arithmetic copies TFC's {@code calculateNutrition} expression by expression (golden tests).
 */
public record NutritionState(List<FoodData> records) {
    /** No food eaten yet: all values at the defaults. */
    public static final NutritionState EMPTY = new NutritionState(List.of());

    /** TFC {@code PlayerInfo.MAX_HUNGER}. */
    public static final int MAX_HUNGER = 20;

    public NutritionState {
        records = List.copyOf(records);
    }

    /** The nutrient values at {@code hunger} (TFC {@code calculateNutrition}, the values part). */
    public NutritionLevels levels(int hunger, NutritionConfig config) {
        return new NutritionLevels(calculate(hunger, config).values);
    }

    /** The records TFC keeps after recomputing at {@code hunger} (the ones past the window are dropped). */
    public NutritionState truncated(int hunger, NutritionConfig config) {
        int kept = calculate(hunger, config).kept;
        return kept == records.size() ? this : new NutritionState(records.subList(0, kept));
    }

    /**
     * TFC {@code NutritionData.addNutrients}: records {@code food} if it has hunger, or if there are no records,
     * or if the last food eaten had hunger (so a drink of milk counts once after a meal), then recomputes at the
     * hunger the player had <i>before</i> eating ({@code hungerBefore}).
     */
    public NutritionState withEaten(FoodData food, int hungerBefore, NutritionConfig config) {
        Objects.requireNonNull(food, "food");
        if (food.hunger() > 0 || records.isEmpty() || records.get(0).hunger() > 0) {
            List<FoodData> next = new ArrayList<>(records.size() + 1);
            next.add(food);
            next.addAll(records);
            return new NutritionState(next).truncated(hungerBefore, config);
        }
        return this;
    }

    private record Calc(float[] values, int kept) {
    }

    private Calc calculate(int hunger, NutritionConfig config) {
        final float[] nutrients = new float[Nutrient.TOTAL];
        final int hungerWindow = config.hungerWindow();
        int runningHungerTotal = Math.max(MAX_HUNGER - hunger, 0);
        int kept = records.size();
        for (int i = 0; i < kept; i++) {
            FoodData record = records.get(i);
            int nextHunger = record.hunger() + runningHungerTotal;
            if (nextHunger <= hungerWindow) {
                int weight = Math.max(record.hunger(), 1);
                for (Nutrient n : Nutrient.VALUES) {
                    nutrients[n.ordinal()] = nutrients[n.ordinal()] + n.of(record) * weight;
                }
                runningHungerTotal = nextHunger;
            } else {
                float actualHunger = hungerWindow - runningHungerTotal;
                for (Nutrient n : Nutrient.VALUES) {
                    nutrients[n.ordinal()] = nutrients[n.ordinal()] + n.of(record) * actualHunger;
                }
                kept = i + 1; // TFC removes every later record, which ends the loop
            }
        }
        for (Nutrient n : Nutrient.VALUES) {
            nutrients[n.ordinal()] = nutrients[n.ordinal()] / hungerWindow;
        }
        if (runningHungerTotal < hungerWindow) {
            float defaultModifier = 1 - (float) runningHungerTotal / hungerWindow;
            for (Nutrient n : Nutrient.VALUES) {
                nutrients[n.ordinal()] += (n == Nutrient.DAIRY ? config.defaultDairy() : config.defaultNutrition())
                        * defaultModifier;
            }
        }
        for (Nutrient n : Nutrient.VALUES) {
            nutrients[n.ordinal()] = Math.min(1, nutrients[n.ordinal()]);
        }
        return new Calc(nutrients, kept);
    }
}
