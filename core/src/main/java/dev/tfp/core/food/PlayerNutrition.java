/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (eat, tick: hunger and nutrition)
 */
package dev.tfp.core.food;

import dev.tfp.core.recipe.food.FoodData;
import java.util.Objects;

/**
 * A player's hunger and nutrition together, the pure core of TFC's {@code PlayerInfo} food handling (M3-SURV-02).
 * Immutable and lazy: store it with the player tick it was evaluated at, and every query first brings it to "now"
 * ({@link #at}); nothing ticks. Thirst, intoxication and the rotten-food effects of eating are left to the caller
 * (the food's {@code water} and {@code intoxication} are in the {@link FoodData}).
 *
 * <p>Use: on a meal, {@code state = state.eat(food, now, config)}; for the HUD or the health modifier,
 * {@code state.at(now, config).levels(config)}. Decide off-thread from a snapshot, apply on the player's thread.
 */
public record PlayerNutrition(HungerState hunger, NutritionState nutrition) {
    public PlayerNutrition {
        Objects.requireNonNull(hunger, "hunger");
        Objects.requireNonNull(nutrition, "nutrition");
    }

    /** A new player at player tick {@code tick}. */
    public static PlayerNutrition fresh(long tick) {
        return new PlayerNutrition(HungerState.fresh(tick), NutritionState.EMPTY);
    }

    /** The state at player tick {@code now}: hunger spent by passive exhaustion, records past the window dropped. */
    public PlayerNutrition at(long now, NutritionConfig config) {
        HungerState h = hunger.advance(now, config);
        return new PlayerNutrition(h, nutrition.truncated(h.hunger(), config));
    }

    /**
     * TFC {@code PlayerInfo.eat(FoodData)} at player tick {@code now}: the nutrients are recorded at the hunger
     * before eating, then hunger and saturation are added, then nutrition is recomputed at the new hunger.
     * The caller checks first that the food is not rotten (TFC eats nothing from rotten food).
     */
    public PlayerNutrition eat(FoodData food, long now, NutritionConfig config) {
        PlayerNutrition s = at(now, config);
        NutritionState n = s.nutrition.withEaten(food, s.hunger.hunger(), config);
        HungerState h = s.hunger.eaten(food.hunger(), food.saturation());
        return new PlayerNutrition(h, n.truncated(h.hunger(), config));
    }

    /** Exhaustion from an action at player tick {@code now} (see {@link HungerState#addExhaustion}). */
    public PlayerNutrition exhaust(float vanillaExhaustion, long now, NutritionConfig config) {
        PlayerNutrition s = at(now, config);
        HungerState h = s.hunger.addExhaustion(vanillaExhaustion, now, config);
        return new PlayerNutrition(h, s.nutrition.truncated(h.hunger(), config));
    }

    /** Hunger set from outside at player tick {@code now} (TFC {@code setFoodLevel} then {@code setHungerAndUpdate}). */
    public PlayerNutrition withHunger(int value, long now, NutritionConfig config) {
        PlayerNutrition s = at(now, config);
        HungerState h = s.hunger.withHunger(value);
        return new PlayerNutrition(h, s.nutrition.truncated(h.hunger(), config));
    }

    /** The nutrient values at this state's hunger; call on a state brought to "now" with {@link #at}. */
    public NutritionLevels levels(NutritionConfig config) {
        return nutrition.levels(hunger.hunger(), config);
    }

    /** TFC {@code getHealthModifier} at this state's hunger. */
    public float healthModifier(NutritionConfig config) {
        return levels(config).healthModifier(config);
    }
}
