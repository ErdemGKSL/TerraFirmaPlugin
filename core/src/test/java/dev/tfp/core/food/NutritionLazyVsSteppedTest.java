/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.recipe.food.FoodData;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * M3-SURV-02: lazy evaluation is exact. Hunger, saturation, exhaustion and nutrition computed in one jump from the
 * stored state equal the values reached by stepping through the same interval (tick by tick, or in uneven steps)
 * and recomputing at each step, as TFC does every tick.
 */
class NutritionLazyVsSteppedTest {
    private static final NutritionConfig CFG = NutritionConfig.DEFAULT;
    private static final Map<String, FoodData> FOODS = NutritionGoldenTest.FOODS;

    @Test
    void hungerOneJumpEqualsTickByTick() {
        for (float mod : new float[] {1f, 0.5f, 2f, 1.37f, 0f}) {
            NutritionConfig cfg = new NutritionConfig(80, 0.5f, 0f, 0.2f, 0.85f, 3f, mod);
            HungerState stepped = new HungerState(17, 3.5f, 1234, 1000);
            HungerState start = stepped;
            for (long t = 1001; t <= 1000 + 60_000; t++) {
                stepped = stepped.advance(t, cfg);
                HungerState direct = start.advance(t, cfg);
                if (t % 997 == 0 || t == 1000 + 60_000) {
                    assertThat(direct).as("mod %s at %d", mod, t).isEqualTo(stepped);
                }
            }
        }
    }

    @Test
    void passivePointEveryThreeThousandTicks() {
        HungerState s = new HungerState(20, 0f, 0, 0);
        assertThat(s.advance(3000, CFG).hunger()).isEqualTo(20);
        assertThat(s.advance(3001, CFG).hunger()).isEqualTo(19);
        assertThat(s.advance(6001, CFG).hunger()).isEqualTo(18);
        // a full bar in 2.5 default days (TFC's PASSIVE_EXHAUSTION_PER_TICK comment)
        assertThat(s.advance(60_000, CFG).hunger()).isEqualTo(1);
        assertThat(s.advance(60_001, CFG).hunger()).isZero();
        // saturation is spent first, one whole point at a time
        assertThat(new HungerState(20, 1.5f, 0, 0).advance(6001, CFG)).isEqualTo(new HungerState(20, 0f, 1000, 6001));
        assertThat(new HungerState(20, 1.5f, 0, 0).advance(9001, CFG).hunger()).isEqualTo(19);
    }

    @Test
    void pointCountStaysWithinOneOfTfcFloatStepping() {
        // TFC: PlayerInfo.tick adds 0.4 x PASSIVE_EXHAUSTION_PER_TICK as a float and vanilla spends 4 when above 4.
        float per = 0.4f * (20 * 4f / (2.5f * 24000 * 0.4f) * 1f);
        float ex = 0;
        long tfcPoints = 0;
        HungerState start = new HungerState(20, 0f, 0, 0);
        for (long t = 1; t <= 24_000L * 200; t++) {
            ex = Math.min(ex + per, 40f);
            if (ex > 4f) {
                ex -= 4f;
                tfcPoints++;
            }
            if (t % 7919 == 0) {
                long ours = (t - 1) / 3000; // what advance() spends by tick t
                assertThat(Math.abs(ours - tfcPoints)).as("tick %d", t).isLessThanOrEqualTo(1);
            }
        }
        assertThat(start.advance(3001, CFG).exhaustion()).isEqualTo(1000);
    }

    @Test
    void activityExhaustionIsAdditiveAndCapped() {
        HungerState s = new HungerState(20, 0f, 0, 0);
        // 10 vanilla exhaustion x 0.4 = 4 = one point exactly, not yet above it
        assertThat(s.addExhaustion(10f, 0, CFG)).isEqualTo(new HungerState(20, 0f, HungerState.UNITS_PER_POINT, 0));
        assertThat(s.addExhaustion(10f, 0, CFG).advance(1, CFG).hunger()).isEqualTo(19);
        // capped at 40 exhaustion: ten points at most
        assertThat(s.addExhaustion(1000f, 0, CFG).hunger()).isEqualTo(11);
    }

    @Test
    void mealsWithSteppedEvaluationEqualOneJumpBetweenMeals() {
        SplittableRandom random = new SplittableRandom(42);
        List<String> names = List.copyOf(new TreeMap<>(FOODS).keySet());
        TreeMap<Long, String> meals = new TreeMap<>();
        long t = 0;
        for (int i = 0; i < 60; i++) {
            t += 500 + random.nextLong(30_000);
            meals.put(t, names.get(random.nextInt(names.size())));
        }
        long end = t + 100_000;

        PlayerNutrition lazy = PlayerNutrition.fresh(0);
        PlayerNutrition stepped = PlayerNutrition.fresh(0);
        long now = 0;
        for (Map.Entry<Long, String> meal : meals.entrySet()) {
            while (now + 37 < meal.getKey()) {
                now += 37;
                stepped = stepped.at(now, CFG);
                assertThat(stepped.levels(CFG)).isEqualTo(lazy.at(now, CFG).levels(CFG));
            }
            now = meal.getKey();
            FoodData food = FOODS.get(meal.getValue());
            stepped = stepped.eat(food, now, CFG);
            lazy = lazy.eat(food, now, CFG);
            assertThat(stepped).isEqualTo(lazy);
        }
        for (long s = now; s <= end; s += 1009) {
            stepped = stepped.at(s, CFG);
        }
        assertThat(stepped.at(end, CFG)).isEqualTo(lazy.at(end, CFG));
        assertThat(stepped.at(end, CFG).levels(CFG)).isEqualTo(lazy.at(end, CFG).levels(CFG));
    }

    @Test
    void recordsBeyondTheWindowAreDroppedAndMilkCountsOnceAfterAMeal() {
        PlayerNutrition s = PlayerNutrition.fresh(0);
        for (int i = 0; i < 30; i++) {
            s = s.eat(FOODS.get("beef"), 0, CFG);
        }
        // 80 / 4 = 20 records fill the window at full hunger; one more crosses it and is kept partially
        assertThat(s.nutrition().records()).hasSizeLessThanOrEqualTo(21);
        assertThat(s.levels(CFG).get(Nutrient.PROTEIN)).isEqualTo(1f);
        PlayerNutrition milk = s.eat(FOODS.get("milk"), 0, CFG);
        assertThat(milk.nutrition().records()).hasSize(s.nutrition().records().size() + 1);
        assertThat(milk.eat(FOODS.get("milk"), 0, CFG).nutrition()).isEqualTo(milk.nutrition());
    }
}
