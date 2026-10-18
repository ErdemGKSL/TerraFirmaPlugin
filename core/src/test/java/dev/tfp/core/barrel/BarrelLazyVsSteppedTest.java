/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick and
 * onCalendarUpdate, which must agree for an unloaded barrel)
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BarrelLazyVsSteppedTest {

    private static final int CAP = BarrelRules.DEFAULT_CAPACITY;

    /** One lazy jump equals the per-tick step, for every golden progress row. */
    @Test
    void oneJumpEqualsTickByTickForEveryGoldenRow() {
        for (String[] row : BarrelFixtures.goldenProgress()) {
            final List<BarrelRecipe> recipes = BarrelFixtures.recipesFor(row[1]);
            final BarrelState start = BarrelFixtures.startOf(row, recipes);
            final long now = BarrelFixtures.BASE + BarrelFixtures.elapsed(row);
            final BarrelState lazy = BarrelProgress.settle(start, now, recipes, CAP).state();
            assertThat(lazy).as(String.join(" ", row)).isEqualTo(stepped(start, now, recipes));
        }
    }

    /** Settling in random increments equals one jump, and equals the per-tick step, over a long horizon. */
    @Test
    void randomIncrementsEqualOneJumpAndTheStep() {
        final Random random = new Random(0x7fc3L);
        for (List<BarrelRecipe> recipes : List.of(BarrelFixtures.tfcGrid(), BarrelFixtures.CHAIN)) {
            for (int round = 0; round < 40; round++) {
                final BarrelState start = startFor(recipes, random);
                final long horizon = BarrelFixtures.BASE + 3L * 72_000L;
                BarrelState walked = start;
                long at = start.asOf();
                while (at < horizon) {
                    at = Math.min(horizon, at + 1 + random.nextInt(9000));
                    walked = BarrelProgress.settle(walked, at, recipes, CAP).state();
                }
                final BarrelState once = BarrelProgress.settle(start, horizon, recipes, CAP).state();
                assertThat(walked).as("round " + round).isEqualTo(once);
                assertThat(once).as("round " + round).isEqualTo(stepped(start, horizon, recipes));
            }
        }
    }

    /** Settling to every tick of a chain, one at a time, equals the step at each tick. */
    @Test
    void everyTickOfTheChainEqualsTheStep() {
        final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:brine", 500), new BarrelStack("food:maize", 3))
            .seal(BarrelFixtures.BASE, BarrelFixtures.CHAIN);
        BarrelState walked = start;
        BarrelState stepped = start;
        for (long t = BarrelFixtures.BASE + 1; t <= BarrelFixtures.BASE + 8000; t++) {
            stepped = BarrelProgress.tick(stepped, t, BarrelFixtures.CHAIN, CAP);
            walked = BarrelProgress.settle(walked, t, BarrelFixtures.CHAIN, CAP).state();
            assertThat(walked).as("tick " + t).isEqualTo(stepped);
        }
    }

    private static BarrelState stepped(BarrelState start, long now, List<BarrelRecipe> recipes) {
        BarrelState state = start;
        for (long t = start.sealedTick() + 1; t <= now; t++) {
            state = BarrelProgress.tick(state, t, recipes, CAP);
        }
        return state;
    }

    /** A sealed barrel with random contents in the range of the recipe list, sealed at a random tick near the base. */
    private static BarrelState startFor(List<BarrelRecipe> recipes, Random random) {
        final long seal = BarrelFixtures.BASE + random.nextInt(50);
        if (recipes == BarrelFixtures.CHAIN) {
            final FluidAmount fluid = new FluidAmount("tfc:brine", 125 * (1 + random.nextInt(5)));
            final BarrelStack items = new BarrelStack("food:maize", 1 + random.nextInt(3));
            return BarrelState.unsealed(fluid, items).seal(seal, recipes);
        }
        final int k = random.nextInt(recipes.size());
        final BarrelRecipe pick = recipes.get(k);
        final String item = BarrelFixtures.itemOf(pick.id());
        final FluidAmount fluid = new FluidAmount(pick.inputFluid(), pick.inputFluidAmount() * (1 + random.nextInt(4)));
        final BarrelStack items = item == null ? BarrelStack.EMPTY : new BarrelStack(item, random.nextInt(4));
        return BarrelState.unsealed(fluid, items).seal(seal, recipes);
    }
}
