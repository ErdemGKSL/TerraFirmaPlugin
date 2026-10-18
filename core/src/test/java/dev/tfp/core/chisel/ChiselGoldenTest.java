/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.chisel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the 513 TFC chisel recipes. The table {@code chisel-recipes.tsv} is exported from TFC's {@code recipe/chisel/} at
 * c3b4d9536 by the scratch export script: one row per JSON file, with the pipe-joined ingredient list and the optional item output.
 */
class ChiselGoldenTest {
    /** The number of JSON files in TFC's {@code recipe/chisel/} at c3b4d9536. */
    private static final int RECIPE_COUNT = 513;

    /** Distinct (block, mode) claims: 513 recipes, of which 21 accept two blocks. */
    private static final int DISTINCT_INPUT_MODE_PAIRS = 534;

    private static List<ChiselRecipe> recipes() throws IOException {
        List<ChiselRecipe> out = new ArrayList<>();
        try (InputStream in = ChiselGoldenTest.class.getResourceAsStream("chisel-recipes.tsv")) {
            assertThat(in).isNotNull();
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].split("\t", -1);
                assertThat(cols).as("row %d", i).hasSize(6);
                ChiselRecipe.ItemOutput output = cols[4].isEmpty() ? null
                    : new ChiselRecipe.ItemOutput(cols[4], Integer.parseInt(cols[5]));
                out.add(new ChiselRecipe(cols[0], List.of(cols[2].split("\\|")), cols[3], ChiselMode.fromId(cols[1]), output));
            }
        }
        return out;
    }

    @Test
    void rowCountMatchesJsonFiles() throws IOException {
        assertThat(recipes()).hasSize(RECIPE_COUNT);
    }

    @Test
    void modeCountsAreAsInTfc() throws IOException {
        List<ChiselRecipe> recipes = recipes();
        assertThat(recipes).filteredOn(r -> r.mode() == ChiselMode.SMOOTH).hasSize(57);
        assertThat(recipes).filteredOn(r -> r.mode() == ChiselMode.SLAB).hasSize(228);
        assertThat(recipes).filteredOn(r -> r.mode() == ChiselMode.STAIR).hasSize(228);
    }

    @Test
    void noDuplicateInputAndModePairs() throws IOException {
        Map<String, Integer> claims = new HashMap<>();
        int duplicates = 0;
        for (ChiselRecipe recipe : recipes()) {
            for (String block : recipe.ingredients()) {
                if (claims.merge(block + "@" + recipe.mode().id(), 1, Integer::sum) > 1) {
                    duplicates++;
                }
            }
        }
        assertThat(duplicates).as("duplicate (input, mode) pairs").isZero();
        assertThat(claims).hasSize(DISTINCT_INPUT_MODE_PAIRS);
    }

    @Test
    void everyRecipeIsFoundByItsOwnInputAndMode() throws IOException {
        List<ChiselRecipe> recipes = recipes();
        for (ChiselRecipe recipe : recipes) {
            for (String block : recipe.ingredients()) {
                assertThat(ChiselRules.find(recipes, block, recipe.mode()))
                    .as("%s via %s", recipe.id(), block)
                    .map(ChiselRecipe::id)
                    .contains(recipe.id());
            }
        }
    }

    @Test
    void slabAndStairRowsAreNamedByTheirMode() throws IOException {
        for (ChiselRecipe recipe : recipes()) {
            if (recipe.mode() == ChiselMode.SLAB) {
                assertThat(recipe.result()).as(recipe.id()).endsWith("_slab");
                assertThat(recipe.itemOutput()).as(recipe.id()).isNotNull();
                assertThat(recipe.itemOutput().item()).as(recipe.id()).isEqualTo(recipe.result());
                assertThat(recipe.itemOutput().count()).as(recipe.id()).isEqualTo(1);
            } else if (recipe.mode() == ChiselMode.STAIR) {
                assertThat(recipe.result()).as(recipe.id()).endsWith("_stairs");
                assertThat(recipe.itemOutput()).as(recipe.id()).isNull();
            } else {
                assertThat(recipe.itemOutput()).as(recipe.id()).isNull();
            }
        }
    }

    @Test
    void sampledRowsMapToTheirModeAndPlaceTheirResult() throws IOException {
        List<ChiselRecipe> recipes = recipes();
        Set<ChiselMode> seen = new HashSet<>();
        for (int i = 0; i < recipes.size(); i += 17) {
            ChiselRecipe recipe = recipes.get(i);
            seen.add(recipe.mode());
            assertThat(recipe.mode().id()).as(recipe.id()).startsWith("tfc:");
            ChiselRules.Use use = ChiselRules.use(recipes, recipe.ingredients().get(0), recipe.mode(), true, true, true);
            assertThat(use.status()).as(recipe.id()).isEqualTo(ChiselRules.Status.CHISELED);
            assertThat(use.recipe().result()).as(recipe.id()).isEqualTo(recipe.result());
        }
        assertThat(seen).containsExactlyInAnyOrder(ChiselMode.values());
    }
}
