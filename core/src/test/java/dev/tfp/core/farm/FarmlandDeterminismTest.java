/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTickStep)
 */
package dev.tfp.core.farm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.farm.FarmlandRules.SoilHydration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The same inputs always give the same state, and the nutrients stay in {@code [0, 1]} through any sequence of operations. */
class FarmlandDeterminismTest {

    /** One seeded run: a long mixed sequence of water updates, growth steps, fertilizer and reads. Returns every state visited. */
    private static List<FarmlandState> run(long seed) {
        Random random = new Random(seed);
        FarmlandState state = FarmlandState.fresh(0L);
        long calendar = 0L;
        List<FarmlandState> visited = new ArrayList<>();
        for (int step = 0; step < 2_000; step++) {
            int op = random.nextInt(4);
            if (op == 0) {
                long next = calendar + 1 + random.nextInt(50_000);
                state = state.waterTick(calendar, next, next);
                calendar = next;
            } else if (op == 1) {
                NutrientGrowth.Demand demand = new NutrientGrowth.Demand(random.nextFloat() - 0.3f, random.nextFloat() - 0.2f, random.nextFloat() - 0.4f);
                NutrientGrowth.Demand absorbed = new NutrientGrowth.Demand(0f, 0f, 0f);
                NutrientGrowth.Consumption c = NutrientGrowth.consumeForGrowthStep(state.nutrients(), demand, absorbed, 1 + random.nextInt(48_000));
                Nutrients after = NutrientGrowth.produceAfterGrowthStep(c, demand, SoilTier.values()[random.nextInt(5)].bonus(), random.nextFloat() * 0.05f);
                state = state.withNutrients(after);
            } else if (op == 2) {
                Nutrients after = state.nutrients().addFertilizer(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat() * 2f);
                state = state.withNutrients(after);
            } else {
                // A read: pure, so it does not change the state.
                FarmlandRules.moisture(random.nextFloat() * 600f, random.nextBoolean(), random.nextBoolean(), SoilHydration.values()[random.nextInt(3)]);
            }
            visited.add(state);
        }
        return visited;
    }

    @Test
    void identicalInputsGiveIdenticalStatesOnEveryRun() {
        List<FarmlandState> first = run(42L);
        List<FarmlandState> second = run(42L);
        assertThat(second).isEqualTo(first);
        assertThat(run(43L)).isNotEqualTo(first);
    }

    @Test
    void nutrientsStayInUnitRangeThroughAnySequence() {
        for (FarmlandState state : run(7L)) {
            assertThat(state.nutrients().nitrogen()).isBetween(0f, 1f);
            assertThat(state.nutrients().phosphorus()).isBetween(0f, 1f);
            assertThat(state.nutrients().potassium()).isBetween(0f, 1f);
            assertThat(state.additionalWater()).isBetween(0f, FarmlandRules.MAX_ADDITIONAL_WATER);
        }
    }

    @Test
    void independentNutrientTakesCommuteExactly() {
        // Taking from different nutrients touches different values, so the order of the takes cannot change the result.
        Nutrients soil = new Nutrients(0.6f, 0.4f, 0.9f);
        Nutrients nThenP = soil.consume(Nutrient.NITROGEN, 0.1f).after().consume(Nutrient.PHOSPHOROUS, 0.3f).after();
        Nutrients pThenN = soil.consume(Nutrient.PHOSPHOROUS, 0.3f).after().consume(Nutrient.NITROGEN, 0.1f).after();
        assertThat(nThenP).isEqualTo(pThenN);
    }
}
