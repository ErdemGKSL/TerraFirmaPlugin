/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: the next recipe's
 * onSealed in a sequential chain, the instant recipe check after a change)
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The generic simulation equals {@link BarrelProgress} on the count model (and so TFC's golden rows), and its own lazy jump
 * equals its per-tick step when the on-seal and instant hooks change the contents.
 */
class BarrelSimulationTest {

    private static final int CAP = BarrelRules.DEFAULT_CAPACITY;

    /** The count model as simulation rules; the hooks record their calls. */
    static final class CountRules implements BarrelSimulation.Rules<BarrelState, BarrelRecipe> {
        final List<BarrelRecipe> recipes;
        final List<String> onSealed = new ArrayList<>();
        final boolean hooks;
        int instantChecks;

        CountRules(List<BarrelRecipe> recipes, boolean hooks) {
            this.recipes = recipes;
            this.hooks = hooks;
        }

        @Override
        public BarrelRecipe select(BarrelState contents) {
            return BarrelRecipe.select(recipes, contents);
        }

        @Override
        public int duration(BarrelRecipe recipe) {
            return recipe.duration();
        }

        @Override
        public BarrelState assemble(BarrelRecipe recipe, BarrelState contents, long at) {
            return recipe.assemble(contents, CAP).state();
        }

        @Override
        public BarrelState onSealed(BarrelRecipe recipe, BarrelState contents, long at) {
            if (!hooks) {
                return contents;
            }
            onSealed.add(recipe.id() + "@" + at);
            // like the vinegar preservation: the infinite stage marks its item
            if (recipe.isInfinite() && contents.main().item().equals("food:pickled")) {
                return contents.with(contents.fluid(), new BarrelStack("food:pickled", contents.main().count()),
                    contents.excess());
            }
            return contents;
        }

        @Override
        public BarrelState flush(BarrelState s) {
            if (s.excess().isEmpty() || !s.main().isEmpty()) {
                return s;
            }
            return s.with(s.fluid(), s.excess(), BarrelStack.EMPTY);
        }

        @Override
        public BarrelState instant(BarrelState contents, long at) {
            if (hooks) {
                instantChecks++;
            }
            return contents;
        }
    }

    private static BarrelState strip(BarrelState s) {
        return new BarrelState(s.fluid(), s.main(), s.excess(), true, 0L, 0L, 0L);
    }

    @Test
    void equalsBarrelProgressOnEveryGoldenRow() {
        for (String[] row : BarrelFixtures.goldenProgress()) {
            final List<BarrelRecipe> recipes = BarrelFixtures.recipesFor(row[1]);
            final BarrelState start = BarrelFixtures.startOf(row, recipes);
            final long now = BarrelFixtures.BASE + BarrelFixtures.elapsed(row);
            final BarrelProgress.Settled expected = BarrelProgress.settle(start, now, recipes, CAP);
            final BarrelSimulation.Progress<BarrelState> got = BarrelSimulation.settle(start, start.recipeTick(),
                start.asOf(), now, new CountRules(recipes, false));
            assertThat(strip(got.contents())).as(String.join(" ", row)).isEqualTo(strip(expected.state()));
            assertThat(got.recipeTick()).as(String.join(" ", row)).isEqualTo(expected.state().recipeTick());
            assertThat(got.completions()).isEqualTo(expected.completions());
            assertThat(got.asOf()).isEqualTo(now);
        }
    }

    @Test
    void theChainCallsOnSealedOfEachNextRecipeAtItsCompletionTick() {
        final CountRules rules = new CountRules(BarrelFixtures.CHAIN, true);
        final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:brine", 1000), new BarrelStack("food:maize", 2))
            .seal(BarrelFixtures.BASE, BarrelFixtures.CHAIN);
        final BarrelSimulation.Progress<BarrelState> done = BarrelSimulation.settle(start, start.recipeTick(), start.asOf(),
            BarrelFixtures.BASE + 20_000, rules);
        assertThat(done.completions()).isEqualTo(2);
        assertThat(rules.onSealed).containsExactly("chain:pickle@4601", "chain:preserve@8202");
        assertThat(done.contents().main()).isEqualTo(new BarrelStack("food:pickled", 2));
        assertThat(done.recipeTick()).isEqualTo(8202);
        assertThat(rules.instantChecks).as("one instant check after each completion").isEqualTo(2);
    }

    @Test
    void randomJumpsEqualTheStepWithTheHooks() {
        final Random random = new Random(0x51aL);
        for (int round = 0; round < 30; round++) {
            final CountRules lazyRules = new CountRules(BarrelFixtures.CHAIN, true);
            final CountRules stepRules = new CountRules(BarrelFixtures.CHAIN, true);
            final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:brine", 125 * (1 + random.nextInt(5))),
                new BarrelStack("food:maize", 1 + random.nextInt(4))).seal(BarrelFixtures.BASE, BarrelFixtures.CHAIN);
            final long horizon = BarrelFixtures.BASE + 12_000;
            BarrelState walked = start;
            long tick = start.recipeTick();
            long at = start.asOf();
            while (at < horizon) {
                final long next = Math.min(horizon, at + 1 + random.nextInt(3000));
                final BarrelSimulation.Progress<BarrelState> p = BarrelSimulation.settle(walked, tick, at, next, lazyRules);
                walked = p.contents();
                tick = p.recipeTick();
                at = next;
            }
            BarrelState stepped = start;
            long stepTick = start.recipeTick();
            for (long t = BarrelFixtures.BASE + 1; t <= horizon; t++) {
                final BarrelSimulation.Progress<BarrelState> p = BarrelSimulation.tick(stepped, stepTick, t, stepRules);
                stepped = p.contents();
                stepTick = p.recipeTick();
            }
            assertThat(strip(walked)).as("round " + round).isEqualTo(strip(stepped));
            assertThat(tick).as("round " + round).isEqualTo(stepTick);
            assertThat(lazyRules.onSealed).as("round " + round).isEqualTo(stepRules.onSealed);
        }
    }

    @Test
    void settlingBackwardsIsRefused() {
        final CountRules rules = new CountRules(BarrelFixtures.CHAIN, false);
        assertThatThrownBy(() -> BarrelSimulation.settle(BarrelState.empty(), BarrelRules.NO_TICK, 10, 5, rules))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBarrelWithoutARecipeOnlyMovesItsExcess() {
        final CountRules rules = new CountRules(BarrelFixtures.CHAIN, false);
        final BarrelState s = new BarrelState(new FluidAmount("tfc:brine", 100), BarrelStack.EMPTY,
            new BarrelStack("food:maize", 3), true, 0, BarrelRules.NO_TICK, 0);
        final BarrelSimulation.Progress<BarrelState> p = BarrelSimulation.settle(s, BarrelRules.NO_TICK, 0, 50, rules);
        assertThat(p.contents().main()).isEqualTo(new BarrelStack("food:maize", 3));
        assertThat(p.contents().excess().isEmpty()).isTrue();
        assertThat(p.completions()).isZero();
    }
}
