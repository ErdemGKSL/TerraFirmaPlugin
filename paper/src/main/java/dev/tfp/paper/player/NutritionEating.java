/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.core.food.HungerState;
import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.food.PlayerNutrition;
import dev.tfp.core.recipe.food.FoodData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pure part of eating (M3-SURV-02): the stored nutrition records after a meal, from the core
 * {@link PlayerNutrition#eat} rule. Pure: the caller passes the stored data, the food (empty for a food the plugin
 * does not know) and the vanilla hunger and saturation read on the player's thread, and writes the result with
 * {@link PlayerDataStore#update}.
 *
 * <p>Only the nutrition records change. The hunger and saturation the core state computes are not written back:
 * vanilla's food level stays authoritative until the food sync lands, and the passive exhaustion and thirst clock
 * are left to {@link ThirstDecay} (writing them here would drop the thirst loss of the points already spent).
 */
public final class NutritionEating {
    private static final NutritionConfig CONFIG = NutritionConfig.DEFAULT;

    private NutritionEating() {
    }

    /**
     * The data after eating {@code food} at player tick {@code now}. Returns {@code stored} unchanged when the food is
     * unknown or rotten (TFC eats nothing from rotten food; the rotten effects are not built yet).
     *
     * @param vanillaFood       the vanilla food level before eating (0..20)
     * @param vanillaSaturation the vanilla saturation before eating; a negative or NaN value counts as 0
     */
    public static PlayerData eat(PlayerData stored, Optional<FoodData> food, boolean rotten, int vanillaFood,
            float vanillaSaturation, long now) {
        if (food.isEmpty() || rotten) {
            return stored;
        }
        // The hunger clock is the thirst clock (ThirstDecay keeps both at the same tick); a player never evaluated
        // starts it now, as ThirstDecay does on its first evaluation.
        long clock = stored.thirstTick() == Long.MIN_VALUE ? now : stored.thirstTick();
        float saturation = Float.isNaN(vanillaSaturation) || vanillaSaturation < 0 ? 0f : vanillaSaturation;
        HungerState hunger = new HungerState(Math.clamp(vanillaFood, 0, NutritionState.MAX_HUNGER), saturation,
                stored.passiveExhaustion(), clock);
        PlayerNutrition state = new PlayerNutrition(hunger, new NutritionState(toFoods(stored.nutrition())));
        PlayerNutrition eaten = state.eat(food.get(), now, CONFIG);
        List<PlayerData.FoodRecord> records = new ArrayList<>(eaten.nutrition().records().size());
        for (FoodData f : eaten.nutrition().records()) {
            records.add(toRecord(f));
        }
        return stored.withNutrition(records);
    }

    static List<FoodData> toFoods(List<PlayerData.FoodRecord> records) {
        List<FoodData> foods = new ArrayList<>(records.size());
        for (PlayerData.FoodRecord r : records) {
            PlayerData.Nutrients n = r.nutrients();
            foods.add(new FoodData(r.hunger(), r.water(), r.saturation(), r.intoxication(), n.grain(), n.fruit(),
                    n.vegetables(), n.protein(), n.dairy(), r.decayModifier()));
        }
        return foods;
    }

    static PlayerData.FoodRecord toRecord(FoodData f) {
        return new PlayerData.FoodRecord(f.hunger(), f.water(), f.saturation(), f.intoxication(),
                new PlayerData.Nutrients(f.grain(), f.fruit(), f.vegetables(), f.protein(), f.dairy()), f.decayModifier());
    }
}
