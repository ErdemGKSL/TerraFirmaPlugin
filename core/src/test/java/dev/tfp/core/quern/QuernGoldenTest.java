/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (MANUAL_TICKS, NETWORK_RECIPE_PER_SPEED),
 * file: src/main/java/net/dries007/tfc/common/recipes/QuernRecipe.java @ c3b4d9536
 */
package dev.tfp.core.quern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * M3-PROC-01 golden values: the 89 TFC quern recipes at c3b4d9536 and the grind progress of TFC's {@code QuernBlockEntity}.
 * The recipe table and the lookup golden rows are generated from TFC's recipe JSON (see the resource headers); the progress
 * constants are TFC's arithmetic: 90 manual ticks, and a network speed that adds {@code speed * 90 / 2pi} per tick.
 */
class QuernGoldenTest {
    /** Progress after these turns, from TFC: turns x 90 units, clamped at 90 (manual: 90 ticks; network: one turn is 90). */
    static final double[] TURN_GRID = {0.0, 0.25, 0.5, 0.75, 0.999, 1.0, 1.5, 2.0};
    static final double[] TURN_PROGRESS = {0.0, 22.5, 45.0, 67.5, 89.91, 90.0, 90.0, 90.0};

    /** Manual ticks from TFC: one unit per tick, finishing at tick 90. */
    static final long[] TICK_GRID = {0, 1, 45, 89, 90, 91, 180};
    static final double[] TICK_PROGRESS = {0, 1, 45, 89, 90, 90, 90};

    @Test
    void recipeTableHasAllTfc89Recipes() throws IOException {
        Table table = Table.load();
        assertThat(table.recipes).hasSize(89);
        assertThat(table.recipes.stream().map(QuernRecipe::id).distinct().count()).isEqualTo(89);
    }

    @Test
    void lookupMatchesTfcGolden() throws IOException {
        Table table = Table.load();
        List<String[]> rows = Table.rows("quern-golden.txt");
        assertThat(rows).hasSize(380);
        for (String[] r : rows) {
            Optional<QuernRecipe> found = QuernRules.find(table.recipes, r[0], r[1].equals("1"), table.tags);
            String row = String.join(" ", r);
            if (r[2].equals("-")) {
                assertThat(found).as(row).isEmpty();
                continue;
            }
            assertThat(found).as(row).isPresent();
            QuernRecipe recipe = found.get();
            assertThat(recipe.id()).as(row).isEqualTo(r[2]);
            assertThat(recipe.resultItem()).as(row).isEqualTo(r[3]);
            assertThat(recipe.resultCount()).as(row).isEqualTo(Integer.parseInt(r[4]));
            assertThat(recipe.copyFood()).as(row).isEqualTo(r[5].equals("1"));
        }
    }

    @Test
    void grindProgressMatchesTfcTurnGrid() {
        for (int i = 0; i < TURN_GRID.length; i++) {
            assertThat(QuernRules.progress(TURN_GRID[i])).as("turns " + TURN_GRID[i]).isCloseTo(TURN_PROGRESS[i], within(1e-9));
            assertThat(QuernRules.isFinished(TURN_GRID[i])).as("turns " + TURN_GRID[i]).isEqualTo(TURN_GRID[i] >= 1.0);
        }
    }

    @Test
    void manualProgressMatchesTfcTickGrid() {
        for (int i = 0; i < TICK_GRID.length; i++) {
            assertThat(QuernRules.manualProgress(TICK_GRID[i])).as("ticks " + TICK_GRID[i]).isEqualTo(TICK_PROGRESS[i]);
            // A manual tick is 1/90 of a turn, so the two forms agree.
            assertThat(QuernRules.progress(QuernRules.turnsFromRadians(0) + TICK_GRID[i] / 90.0))
                .isCloseTo(TICK_PROGRESS[i], within(1e-9));
        }
    }

    @Test
    void oneFullTurnIsOneGrind() {
        assertThat(QuernRules.turnsFromRadians(2 * Math.PI)).isCloseTo(1.0, within(1e-12));
        assertThat(QuernRules.isFinished(QuernRules.turnsFromRadians(2 * Math.PI))).isTrue();
        assertThat(QuernRules.isFinished(QuernRules.turnsFromRadians(Math.PI))).isFalse();
    }

    @Test
    void refusesNegativeOrNonFiniteInputs() {
        assertThatThrownBy(() -> QuernRules.progress(-0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuernRules.progress(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuernRules.progress(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuernRules.manualProgress(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuernRules.turnsFromRadians(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grindNeedsRecipeAndHandstone() throws IOException {
        Table table = Table.load();
        Optional<QuernRecipe> wheat = QuernRules.find(table.recipes, "tfc:food/wheat_grain", false, table.tags);
        assertThat(QuernRules.canStart(wheat, true)).isTrue();
        assertThat(QuernRules.canStart(wheat, false)).isFalse();
        Optional<QuernRecipe> none = QuernRules.find(table.recipes, "minecraft:stone", false, table.tags);
        assertThat(QuernRules.canStart(none, true)).isFalse();
    }

    /** The recipe table and tag from the generated resource, parsed by this test only. */
    static final class Table {
        final List<QuernRecipe> recipes = new ArrayList<>();
        final TagLookup tags;

        private Table(Map<String, Set<String>> tagMembers) {
            this.tags = new TagLookup() {
                @Override
                public boolean contains(TagType type, String tag, String id) {
                    return type == TagType.ITEM && tagMembers.getOrDefault(tag, Set.of()).contains(id);
                }

                @Override
                public boolean exists(TagType type, String tag) {
                    return type == TagType.ITEM && tagMembers.containsKey(tag);
                }
            };
        }

        static Table load() throws IOException {
            Map<String, Set<String>> tagMembers = new HashMap<>();
            List<QuernRecipe> recipes = new ArrayList<>();
            for (String[] p : rows("quern-recipes.txt")) {
                if (p[0].equals("TAG")) {
                    tagMembers.put(p[2], new HashSet<>(List.of(p[3].split(","))));
                    continue;
                }
                assertThat(p[0]).isEqualTo("RECIPE");
                Set<String> items = p[2].equals("-") ? Set.of() : Set.of(p[2].split(","));
                String tag = p[3].equals("-") ? null : p[3];
                recipes.add(new QuernRecipe(p[1], items, tag, p[4].equals("1"), p[5], Integer.parseInt(p[6]), p[7].equals("1")));
            }
            Table table = new Table(tagMembers);
            table.recipes.addAll(recipes);
            return table;
        }

        /** Non-comment lines of a resource, split on spaces. */
        static List<String[]> rows(String name) throws IOException {
            List<String[]> out = new ArrayList<>();
            try (InputStream in = QuernGoldenTest.class.getResourceAsStream("/dev/tfp/core/quern/" + name)) {
                assertThat(in).as(name).isNotNull();
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    out.add(line.trim().split(" "));
                }
            }
            return out;
        }
    }
}
