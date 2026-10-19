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
import java.util.function.DoubleSupplier;

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
    /** TFC {@code PlayerInfo#eat(IFood)}: chance of Hunger from rotten food ({@code random.nextFloat() < 0.6}). */
    static final double ROTTEN_HUNGER_CHANCE = 0.6;
    /** TFC {@code PlayerInfo#eat(IFood)}: chance of Poison, only after Hunger applied ({@code < 0.15}). */
    static final double ROTTEN_POISON_CHANCE = 0.15;
    /** TFC {@code PlayerInfo#eat(IFood)}: duration of both rotten-food effects, 1800 ticks (90 seconds). */
    public static final int ROTTEN_EFFECT_TICKS = 1800;

    /** One potion effect from eating rotten food, with the vanilla amplifier (0 = level I). */
    public record RottenEffect(Kind kind, int durationTicks, int amplifier) {
        /** Vanilla hunger and poison, the two effects TFC gives. */
        public enum Kind { HUNGER, POISON }
    }

    /** The vanilla food level and saturation TFC leaves the player with after a meal. */
    public record VanillaFood(int food, float saturation) {
    }

    private NutritionEating() {
    }

    /**
     * The vanilla food level and saturation after eating, under TFC's rule. TFC changes the vanilla food only for a
     * known, non-rotten food with hunger above 0: {@code PlayerInfo#eat(FoodData)}, {@code PlayerInfo.java} lines
     * 208-211, calls {@code this.food.eat(food.hunger(), food.saturation() / (2f * food.hunger()))}, which is vanilla
     * {@code FoodData#eat}: hunger rises by the food's hunger and is capped at 20, and saturation rises by
     * {@code hunger x modifier x 2} (that is, the food's saturation) and is capped at the new food level. Rotten food
     * (lines 174-190) and unknown foods leave both values as they are. The rule is {@link HungerState#eaten}.
     *
     * @param vanillaFood       the vanilla food level before eating
     * @param vanillaSaturation the vanilla saturation before eating; a negative or NaN value counts as 0
     */
    public static VanillaFood vanillaAfterEating(int vanillaFood, float vanillaSaturation, Optional<FoodData> food,
            boolean rotten) {
        int hunger = Math.clamp(vanillaFood, 0, NutritionState.MAX_HUNGER);
        float saturation = Float.isNaN(vanillaSaturation) || vanillaSaturation < 0 ? 0f : vanillaSaturation;
        if (food.isEmpty() || rotten) {
            return new VanillaFood(hunger, saturation);
        }
        HungerState after = new HungerState(hunger, saturation, 0, 0)
                .eaten(food.get().hunger(), food.get().saturation());
        return new VanillaFood(after.hunger(), after.saturation());
    }

    /**
     * The effects of eating one rotten food, in TFC's order. TFC {@code PlayerInfo#eat(IFood)} rolls once for Hunger II
     * (1800 ticks); only when that applies, it rolls once more for Poison I (1800 ticks). Pure: {@code roll} supplies
     * uniform values in [0, 1), called in that order (a single roll when Hunger does not apply).
     */
    public static List<RottenEffect> rottenEffects(DoubleSupplier roll) {
        List<RottenEffect> effects = new ArrayList<>(2);
        if (roll.getAsDouble() < ROTTEN_HUNGER_CHANCE) {
            effects.add(new RottenEffect(RottenEffect.Kind.HUNGER, ROTTEN_EFFECT_TICKS, 1));
            if (roll.getAsDouble() < ROTTEN_POISON_CHANCE) {
                effects.add(new RottenEffect(RottenEffect.Kind.POISON, ROTTEN_EFFECT_TICKS, 0));
            }
        }
        return effects;
    }

    /**
     * The data after eating {@code food} at player tick {@code now}. Returns {@code stored} unchanged when the food is
     * unknown or rotten (TFC eats no nutrition from rotten food; its rotten effects come from {@link #rottenEffects}).
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
