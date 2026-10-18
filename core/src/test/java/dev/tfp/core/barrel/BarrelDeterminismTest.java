/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (onSeal, onUnseal,
 * updateRecipe, the excess move in serverTick)
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import org.junit.jupiter.api.Test;

class BarrelDeterminismTest {

    private static final int CAP = BarrelRules.DEFAULT_CAPACITY;

    /** The same inputs give the same settled barrel, however often they are run. */
    @Test
    void settleIsRepeatable() {
        for (String[] row : BarrelFixtures.goldenProgress()) {
            final List<BarrelRecipe> recipes = BarrelFixtures.recipesFor(row[1]);
            final BarrelState start = BarrelFixtures.startOf(row, recipes);
            final long now = BarrelFixtures.BASE + BarrelFixtures.elapsed(row);
            final BarrelProgress.Settled first = BarrelProgress.settle(start, now, recipes, CAP);
            final BarrelProgress.Settled second = BarrelProgress.settle(start, now, recipes, CAP);
            assertThat(second).as(String.join(" ", row)).isEqualTo(first);
        }
    }

    /** Settling in two steps equals settling to the end, so the answer does not depend on who asked first. */
    @Test
    void twoStepsEqualOneStep() {
        final List<BarrelRecipe> chain = BarrelFixtures.CHAIN;
        final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:brine", 500), new BarrelStack("food:maize", 3))
            .seal(BarrelFixtures.BASE, chain);
        for (long mid = BarrelFixtures.BASE; mid <= BarrelFixtures.BASE + 9000; mid += 137) {
            final BarrelState split = BarrelProgress.settle(BarrelProgress.settle(start, mid, chain, CAP).state(),
                BarrelFixtures.BASE + 9000, chain, CAP).state();
            final BarrelState whole = BarrelProgress.settle(start, BarrelFixtures.BASE + 9000, chain, CAP).state();
            assertThat(split).as("split at " + mid).isEqualTo(whole);
        }
    }

    @Test
    void settleRecordsTheRequestedTick() {
        final List<BarrelRecipe> grid = BarrelFixtures.tfcGrid();
        final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:black_dye", 25), new BarrelStack("minecraft:white_bed", 1))
            .seal(BarrelFixtures.BASE, grid);
        assertThat(BarrelProgress.settle(start, BarrelFixtures.BASE + 5, grid, CAP).state().asOf())
            .isEqualTo(BarrelFixtures.BASE + 5);
    }

    @Test
    void settleRefusesToGoBackwards() {
        final BarrelState start = BarrelState.unsealed(FluidAmount.EMPTY, BarrelStack.EMPTY).seal(100, List.of());
        assertThatThrownBy(() -> BarrelProgress.settle(start, 99, List.of(), CAP)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A seal with no matching recipe keeps the recipe tick, and the progress is empty. */
    @Test
    void sealWithoutRecipeLeavesNoProgress() {
        final BarrelState sealed = BarrelState.unsealed(FluidAmount.EMPTY, BarrelStack.EMPTY).seal(500, BarrelFixtures.CHAIN);
        assertThat(sealed.sealed()).isTrue();
        assertThat(sealed.sealedTick()).isEqualTo(500L);
        assertThat(sealed.recipeTick()).isEqualTo(BarrelRules.NO_TICK);
        final BarrelProgress.Settled later = BarrelProgress.settle(sealed, 10_000, BarrelFixtures.CHAIN, CAP);
        assertThat(later.completions()).isZero();
        assertThat(later.state().remainingTicks(10_000, BarrelFixtures.CHAIN)).isZero();
    }

    /** Unsealing clears the progress, and a new seal starts a fresh recipe at its own tick. */
    @Test
    void unsealDropsProgressAndResealStartsAgain() {
        final List<BarrelRecipe> grid = BarrelFixtures.tfcGrid();
        final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:black_dye", 25), new BarrelStack("minecraft:white_bed", 1))
            .seal(BarrelFixtures.BASE, grid);
        final BarrelState open = BarrelProgress.settle(start, BarrelFixtures.BASE + 1300, grid, CAP).state();
        assertThat(open.sealed()).isTrue();
        assertThat(open.fluid().isEmpty()).isTrue();

        final BarrelState unsealed = open.unseal(BarrelFixtures.BASE + 1300);
        assertThat(unsealed.sealed()).isFalse();
        assertThat(unsealed.sealedTick()).isZero();
        assertThat(unsealed.recipeTick()).isEqualTo(BarrelRules.NO_TICK);
        assertThat(unsealed.main().item()).isEqualTo("minecraft:black_bed");

        final BarrelState refilled = BarrelState.unsealed(new FluidAmount("tfc:black_dye", 25), new BarrelStack("minecraft:white_bed", 1));
        final BarrelState resealed = refilled.seal(9000, grid);
        assertThat(resealed.recipeTick()).isEqualTo(9000L);
        assertThat(BarrelProgress.settle(resealed, 9000 + 1200, grid, CAP).completions()).isZero();
        assertThat(BarrelProgress.settle(resealed, 9000 + 1201, grid, CAP).completions()).isEqualTo(1);
    }

    /** A completion whose output fluid does not fit is trimmed to the room left, as TFC does with the capacity. */
    @Test
    void completionTrimsToCapacity() {
        final BarrelRecipe amplify = new BarrelRecipe("test:amplify", 100, "tfc:water", 500, null, 0,
            "", 0, "tfc:wine", 4000);
        final BarrelState full = BarrelState.unsealed(new FluidAmount("tfc:water", CAP), BarrelStack.EMPTY);
        final BarrelRecipe.Assembly out = amplify.assemble(full, CAP);
        assertThat(out.multiplier()).isEqualTo(2);
        assertThat(out.state().fluid()).isEqualTo(new FluidAmount("tfc:wine", 8000));
    }

    /** An excess stack blocks the recipe lookup, and stays while the main slot holds the output. */
    @Test
    void excessHaltsTheChainWhileMainHoldsOutput() {
        final BarrelRecipe split = new BarrelRecipe("test:split", 100, "tfc:water", 125, id -> id.equals("test:raw"), 1,
            "test:cooked", 1, "", 0);
        final BarrelRecipe next = new BarrelRecipe("test:next", 100, "tfc:water", 125, id -> id.equals("test:cooked"), 1,
            "", 0, "", 0);
        final List<BarrelRecipe> recipes = List.of(split, next);
        final BarrelState start = BarrelState.unsealed(new FluidAmount("tfc:water", 125), new BarrelStack("test:raw", 3))
            .seal(0, recipes);
        final BarrelProgress.Settled done = BarrelProgress.settle(start, 500, recipes, CAP);
        assertThat(done.completions()).isEqualTo(1);
        assertThat(done.state().main()).isEqualTo(new BarrelStack("test:cooked", 1));
        assertThat(done.state().excess()).isEqualTo(new BarrelStack("test:raw", 2));
        assertThat(BarrelRecipe.select(recipes, done.state())).isNull();
    }
}
