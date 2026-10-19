/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.PlayerNutrition;
import dev.tfp.core.recipe.food.FoodData;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** The pure part of eating ({@link NutritionEating}): the records and the modifier they give. */
class NutritionEatingTest {
    private static final NutritionConfig CFG = NutritionConfig.DEFAULT;
    /** One full meal of every nutrient group, like {@code PlayerNutritionHealthTest.FED}. */
    private static final FoodData ALL_GROUPS = new FoodData(20, 0f, 0f, 0, 1f, 1f, 1f, 1f, 1f, 0f);

    @Test
    void eatingAKnownFoodAddsARecordAndChangesTheModifierAsTheCoreRuleSays() {
        PlayerData eaten = NutritionEating.eat(PlayerData.NEW, Optional.of(ALL_GROUPS), false, 20, 0f, 0L);

        assertThat(eaten.nutrition()).hasSize(1);
        assertThat(eaten.nutrition().getFirst().hunger()).isEqualTo(20);
        // the same rule as the core state, read at the same food level
        float core = PlayerNutrition.fresh(0L).eat(ALL_GROUPS, 0L, CFG).healthModifier(CFG);
        float stored = PlayerNeedsHudService.healthModifier(Optional.of(eaten), 20);
        assertThat(stored).isCloseTo(core, Offset.offset(1e-6f));
        // average 0.55 (0.25 eaten + 0.3 default fill): 0.85 + (0.15 / 0.55) * (3.0 - 0.85)
        assertThat(stored).isCloseTo(1.4364f, Offset.offset(0.001f));
        assertThat(stored).isGreaterThan(PlayerNeedsHudService.healthModifier(Optional.of(PlayerData.NEW), 20));
    }

    @Test
    void theNewestMealIsFirst() {
        FoodData grain = new FoodData(10, 0f, 0f, 0, 1f, 0f, 0f, 0f, 0f, 0f);
        PlayerData first = NutritionEating.eat(PlayerData.NEW, Optional.of(grain), false, 20, 0f, 0L);
        PlayerData second = NutritionEating.eat(first, Optional.of(ALL_GROUPS), false, 20, 0f, 0L);
        assertThat(second.nutrition()).hasSize(2);
        assertThat(second.nutrition().getFirst().hunger()).isEqualTo(20);
        assertThat(second.nutrition().get(1).hunger()).isEqualTo(10);
    }

    @Test
    void anUnknownFoodChangesNothing() {
        PlayerData stored = PlayerData.NEW.withThirst(40f).withNutrition(List.of(new PlayerData.FoodRecord(
                5, 0f, 0f, 0, PlayerData.Nutrients.NONE, 0f)));
        PlayerData after = NutritionEating.eat(stored, Optional.empty(), false, 20, 5f, 0L);
        assertThat(after).isSameAs(stored);
    }

    @Test
    void aRottenFoodChangesNothing() {
        PlayerData after = NutritionEating.eat(PlayerData.NEW, Optional.of(ALL_GROUPS), true, 20, 0f, 0L);
        assertThat(after).isSameAs(PlayerData.NEW);
    }

    /**
     * TFC {@code PlayerInfo#eat(IFood)} rotten branch, {@code PlayerInfo.java} lines 178-190: with probability 0.6
     * Hunger (amplifier 1, 1800 ticks); only inside that, with probability 0.15, Poison (amplifier 0, 1800 ticks).
     */
    @Test
    void rottenFoodGivesHungerThenPoisonWhenBothRollsPass() {
        double[] rolls = {0.0, 0.0};
        int[] next = {0};
        var effects = NutritionEating.rottenEffects(() -> rolls[next[0]++]);

        assertThat(effects).containsExactly(
                new NutritionEating.RottenEffect(NutritionEating.RottenEffect.Kind.HUNGER, 1800, 1),
                new NutritionEating.RottenEffect(NutritionEating.RottenEffect.Kind.POISON, 1800, 0));
        assertThat(next[0]).isEqualTo(2);
    }

    /** TFC {@code PlayerInfo.java} lines 182-188: the poison roll is only made when the hunger roll passes. */
    @Test
    void rottenFoodGivesOnlyHungerWhenThePoisonRollFails() {
        double[] rolls = {0.59, 0.15};
        int[] next = {0};
        var effects = NutritionEating.rottenEffects(() -> rolls[next[0]++]);

        assertThat(effects).containsExactly(
                new NutritionEating.RottenEffect(NutritionEating.RottenEffect.Kind.HUNGER, 1800, 1));
        assertThat(next[0]).isEqualTo(2);
    }

    /**
     * TFC {@code PlayerInfo.java} line 182: a failed hunger roll gives nothing and makes no second roll. The
     * comparison is strict ({@code <}), so a roll of exactly 0.6 fails.
     */
    @Test
    void rottenFoodGivesNothingWhenTheHungerRollFailsAtTheBoundary() {
        double[] rolls = {0.6, 0.0};
        int[] next = {0};
        var effects = NutritionEating.rottenEffects(() -> rolls[next[0]++]);

        assertThat(effects).isEmpty();
        assertThat(next[0]).isEqualTo(1);
    }

    @Test
    void eatingLeavesTheThirstAndExhaustionClocksAlone() {
        PlayerData stored = PlayerData.NEW.withThirstClock(70f, 1_000L, 123_456L);
        PlayerData after = NutritionEating.eat(stored, Optional.of(ALL_GROUPS), false, 15, 2f, 1_500L);
        assertThat(after.thirst()).isEqualTo(70f);
        assertThat(after.thirstTick()).isEqualTo(1_000L);
        assertThat(after.passiveExhaustion()).isEqualTo(123_456L);
    }

    /**
     * TFC {@code PlayerInfo#eat(FoodData)} sets the vanilla food level through {@code FoodData#eat}
     * ({@code PlayerInfo.java} lines 208-211): a food of hunger 4 and saturation 2.4 at food 10, saturation 1 gives
     * food 14 and saturation 1 + 2.4 = 3.4.
     */
    @Test
    void eatingAKnownFoodRaisesTheVanillaFoodLevelAndSaturationAsTfcDoes() {
        FoodData bread = new FoodData(4, 0f, 2.4f, 0, 0f, 0f, 0f, 0f, 0f, 0f);
        NutritionEating.VanillaFood after = NutritionEating.vanillaAfterEating(10, 1f, Optional.of(bread), false);

        assertThat(after.food()).isEqualTo(14);
        assertThat(after.saturation()).isCloseTo(3.4f, Offset.offset(1e-5f));
    }

    /** Vanilla caps the food level at 20 and the saturation at the new food level. */
    @Test
    void theVanillaFoodLevelAndSaturationAreCapped() {
        FoodData bread = new FoodData(4, 0f, 2.4f, 0, 0f, 0f, 0f, 0f, 0f, 0f);
        assertThat(NutritionEating.vanillaAfterEating(19, 0f, Optional.of(bread), false).food()).isEqualTo(20);

        FoodData rich = new FoodData(1, 0f, 10f, 0, 0f, 0f, 0f, 0f, 0f, 0f);
        NutritionEating.VanillaFood capped = NutritionEating.vanillaAfterEating(1, 0f, Optional.of(rich), false);
        assertThat(capped.food()).isEqualTo(2);
        assertThat(capped.saturation()).isCloseTo(2f, Offset.offset(1e-5f));
    }

    /** TFC eats nothing from rotten food, and an unknown or zero-hunger food changes nothing. */
    @Test
    void rottenUnknownAndZeroHungerFoodLeaveTheVanillaFoodAsItWas() {
        FoodData bread = new FoodData(4, 0f, 2.4f, 0, 0f, 0f, 0f, 0f, 0f, 0f);
        FoodData noHunger = new FoodData(0, 0f, 2.4f, 0, 0f, 0f, 0f, 0f, 0f, 0f);

        NutritionEating.VanillaFood rotten = NutritionEating.vanillaAfterEating(10, 1f, Optional.of(bread), true);
        assertThat(rotten).isEqualTo(new NutritionEating.VanillaFood(10, 1f));
        assertThat(NutritionEating.vanillaAfterEating(10, 1f, Optional.empty(), false))
                .isEqualTo(new NutritionEating.VanillaFood(10, 1f));
        assertThat(NutritionEating.vanillaAfterEating(10, 1f, Optional.of(noHunger), false))
                .isEqualTo(new NutritionEating.VanillaFood(10, 1f));
    }

    /** A NaN or negative vanilla saturation counts as 0 before TFC's rule is applied. */
    @Test
    void aBadVanillaSaturationCountsAsZero() {
        FoodData bread = new FoodData(4, 0f, 2.4f, 0, 0f, 0f, 0f, 0f, 0f, 0f);
        NutritionEating.VanillaFood nan = NutritionEating.vanillaAfterEating(10, Float.NaN, Optional.of(bread), false);
        assertThat(nan.saturation()).isCloseTo(2.4f, Offset.offset(1e-5f));
        assertThat(NutritionEating.vanillaAfterEating(10, -3f, Optional.empty(), false).saturation()).isZero();
    }
}
