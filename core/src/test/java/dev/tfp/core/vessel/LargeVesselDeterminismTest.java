/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The same seal and unseal sequence gives the same food dates, every time. */
class LargeVesselDeterminismTest {
    private static final FoodConfig CFG = FoodConfig.DEFAULT;

    private static List<FoodState> replay(long seed) {
        Random random = new Random(seed);
        List<FoodState> foods = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            foods.add(new FoodState(random.nextInt(200_000), List.of()));
        }
        long now = 0L;
        for (int step = 0; step < 300; step++) {
            now += random.nextInt(5_000);
            int slot = random.nextInt(foods.size());
            boolean sealing = random.nextBoolean();
            foods.set(slot, LargeVesselRules.sealFood(foods.get(slot), sealing, 1f, now, CFG));
        }
        return foods;
    }

    @Test
    void sameSeedGivesTheSameDates() {
        for (long seed = 1; seed <= 50; seed++) {
            assertThat(replay(seed)).as("seed %s", seed).isEqualTo(replay(seed));
        }
    }

    @Test
    void sealingTwiceAtOneTickIsIdempotent() {
        FoodState food = new FoodState(4_000L, List.of());
        FoodState once = LargeVesselRules.sealFood(food, true, 1f, 9_000L, CFG);
        FoodState twice = LargeVesselRules.sealFood(once, true, 1f, 9_000L, CFG);
        assertThat(twice).isEqualTo(once);
    }
}
