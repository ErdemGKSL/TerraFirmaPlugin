/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick, onSeal, fill, drain),
 * src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java @ c3b4d9536 (durations)
 *
 * The expected values in tfc-barrel-golden.tsv and tfc-barrel-recipes.tsv were transcribed from TFC's recipe JSON
 * (src/generated/resources/data/tfc/recipe/barrel) and worked out by a scratch script that mirrors TFC's per-tick serverTick,
 * assembleOutputs and the fill/drain rules. The values are not computed by this code.
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BarrelGoldenTest {

    /** Sealed-recipe durations in TFC at c3b4d9536: 279 x 1200, 16 x 7200, 8 x 72000, 3 x 3600, 1 x 28800, 1 x 14400, 1 x -1. */
    @Test
    void sealedRecipeTableHasTfcDurations() {
        final List<String[]> rows = BarrelFixtures.tfcTable().values().stream().toList();
        assertThat(rows).hasSize(309);
        final Map<Integer, Long> byDuration = rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(r -> Integer.parseInt(r[1]), java.util.stream.Collectors.counting()));
        assertThat(byDuration).containsEntry(1200, 279L)
            .containsEntry(7200, 16L)
            .containsEntry(72000, 8L)
            .containsEntry(3600, 3L)
            .containsEntry(28800, 1L)
            .containsEntry(14400, 1L)
            .containsEntry(-1, 1L)
            .hasSize(7);
    }

    /** The six grid recipes build from their table rows with TFC's durations, inputs and outputs. */
    @Test
    void gridRecipesMatchTheTable() {
        final List<BarrelRecipe> grid = BarrelFixtures.tfcGrid();
        assertThat(grid).extracting(BarrelRecipe::id).containsExactlyElementsOf(BarrelFixtures.TFC_GRID);
        final BarrelRecipe hide = grid.get(2);
        assertThat(hide.duration()).isEqualTo(7200);
        assertThat(hide.inputFluid()).isEqualTo("minecraft:water");
        assertThat(hide.inputFluidAmount()).isEqualTo(500);
        assertThat(hide.inputItemCount()).isEqualTo(1);
        assertThat(hide.outputItem()).isEqualTo("tfc:large_prepared_hide");
        final BarrelRecipe curdled = grid.get(4);
        assertThat(curdled.outputFluid()).isEqualTo("tfc:curdled_milk");
        assertThat(curdled.outputFluidAmount()).isEqualTo(1);
        assertThat(curdled.inputItem()).isNull();
    }

    @Test
    void fillRuleMatchesTfcTank() {
        int fills = 0;
        for (String[] r : BarrelFixtures.goldenFillDrain()) {
            if (!r[0].equals("fill")) {
                continue;
            }
            fills++;
            final int capacity = Integer.parseInt(r[1]);
            final FluidAmount stored = BarrelFixtures.fluid(r[2], Integer.parseInt(r[3]));
            final boolean sealed = r[4].equals("1");
            final boolean usable = r[5].equals("1");
            final FluidAmount offered = BarrelFixtures.fluid(r[6], Integer.parseInt(r[7]));
            assertThat(BarrelRules.fillAccepted(stored, sealed, usable, offered, capacity))
                .as(String.join(" ", r))
                .isEqualTo(Integer.parseInt(r[8]));
        }
        assertThat(fills).isEqualTo(300);
    }

    @Test
    void drainRuleMatchesTfcTank() {
        int drains = 0;
        for (String[] r : BarrelFixtures.goldenFillDrain()) {
            if (!r[0].equals("drain")) {
                continue;
            }
            drains++;
            final FluidAmount stored = BarrelFixtures.fluid(r[2], Integer.parseInt(r[3]));
            final boolean sealed = r[4].equals("1");
            assertThat(BarrelRules.drainAccepted(stored, sealed, Integer.parseInt(r[7])))
                .as(String.join(" ", r))
                .isEqualTo(Integer.parseInt(r[8]));
        }
        assertThat(drains).isPositive();
    }

    /** The progress golden: the lazy settle reproduces TFC's completions, outputs, recipe ticks and remaining ticks. */
    @Test
    void settledProgressMatchesTfcGolden() {
        int checked = 0;
        for (String[] row : BarrelFixtures.goldenProgress()) {
            final List<BarrelRecipe> recipes = BarrelFixtures.recipesFor(row[1]);
            final BarrelState sealed = BarrelFixtures.startOf(row, recipes);
            final long now = BarrelFixtures.BASE + BarrelFixtures.elapsed(row);
            final BarrelProgress.Settled settled = BarrelProgress.settle(sealed, now, recipes, BarrelRules.DEFAULT_CAPACITY);
            final String label = String.join(" ", row);

            assertThat(settled.state()).as(label).isEqualTo(BarrelFixtures.expectedState(row, now));
            final BarrelRecipe active = BarrelRecipe.select(recipes, settled.state());
            assertThat(active == null ? "-" : active.id()).as(label).isEqualTo(row[14]);
            assertThat(settled.completions()).as(label).isEqualTo(Integer.parseInt(row[15]));
            assertThat(settled.firstMultiplier()).as(label).isEqualTo(Integer.parseInt(row[16]));
            assertThat(settled.state().remainingTicks(now, recipes)).as(label).isEqualTo(Long.parseLong(row[17]));
            checked++;
        }
        assertThat(checked).isEqualTo(BarrelFixtures.goldenProgress().size());
        assertThat(checked).isGreaterThan(600);
    }
}
