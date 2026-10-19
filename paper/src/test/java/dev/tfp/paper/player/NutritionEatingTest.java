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

    @Test
    void eatingLeavesTheThirstAndExhaustionClocksAlone() {
        PlayerData stored = PlayerData.NEW.withThirstClock(70f, 1_000L, 123_456L);
        PlayerData after = NutritionEating.eat(stored, Optional.of(ALL_GROUPS), false, 15, 2f, 1_500L);
        assertThat(after.thirst()).isEqualTo(70f);
        assertThat(after.thirstTick()).isEqualTo(1_000L);
        assertThat(after.passiveExhaustion()).isEqualTo(123_456L);
    }
}
