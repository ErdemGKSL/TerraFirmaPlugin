/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodStage;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A sealed food decays lazily from the creation date its seal moved. Stepping one calendar tick at a time (rot checked at
 * every tick) must agree with the closed-form stage at each tick. Unsealing must keep the remaining decay fraction.
 */
class LargeVesselLazyVsSteppedTest {
    private static final FoodConfig CFG = FoodConfig.DEFAULT;

    @Test
    void steppingEveryTickMatchesTheLazyStage() {
        for (long creation : new long[] {0L, 5_000L, 100_000L}) {
            for (long sealTick : new long[] {1_000L, 60_000L}) {
                FoodState sealed = LargeVesselRules.sealFood(new FoodState(creation, List.of()), true, 1f, sealTick, CFG);
                long rotten = FoodDecay.rottenDate(sealed.creationDate(), 2f);
                FoodState stepped = sealed;
                for (long t = sealTick; t <= rotten + 1_000L; t++) {
                    stepped = stepped.sanitize(1f, t, CFG);
                    if (t % 1_000L == 0 || Math.abs(t - rotten) <= 2) {
                        assertThat(stepped.stage(1f, t, CFG)).as("food %s sealed %s at %s", creation, sealTick, t)
                            .isEqualTo(sealed.stage(1f, t, CFG));
                    }
                }
                assertThat(stepped.stage(1f, rotten + 1_000L, CFG)).isEqualTo(FoodStage.ROTTEN);
            }
        }
    }

    @Test
    void preservedFoodRotsAtTwiceTheLifeFromItsMovedDate() {
        long creation = 0L;
        long seal = 1_000L;
        FoodState sealed = LargeVesselRules.sealFood(new FoodState(creation, List.of()), true, 1f, seal, CFG);
        // the moved date is (1 - p) * seal + p * creation with p = 2: 2 * creation - seal. The preserved modifier is 0.5,
        // so the life doubles.
        assertThat(sealed.creationDate()).isEqualTo(2 * creation - seal);
        long rotten = FoodDecay.rottenDate(sealed.creationDate(), 2f);
        assertThat(sealed.stage(1f, rotten - 1, CFG)).isEqualTo(FoodStage.FRESH);
        assertThat(sealed.stage(1f, rotten, CFG)).isEqualTo(FoodStage.ROTTEN);
    }

    @Test
    void unsealingKeepsTheRemainingDecayFraction() {
        long creation = 20_000L;
        long seal = 30_000L;
        long unseal = 250_000L;
        FoodState sealed = LargeVesselRules.sealFood(new FoodState(creation, List.of()), true, 1f, seal, CFG);
        double before = (double) (FoodDecay.rottenDate(sealed.creationDate(), 2f) - unseal) / FoodDecay.lifeTime(2f);

        FoodState unsealed = LargeVesselRules.sealFood(sealed, false, 1f, unseal, CFG);
        assertThat(unsealed.hasTrait(FoodTrait.PRESERVED)).isFalse();
        double after = (double) (FoodDecay.rottenDate(unsealed.creationDate(), 1f) - unseal) / FoodDecay.lifeTime(1f);
        assertThat(after).isCloseTo(before, within(1e-5));
    }
}
