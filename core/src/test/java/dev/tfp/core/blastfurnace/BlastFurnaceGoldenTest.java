/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/blast_furnace/*.json @ c3b4d9536 (type, fluid, catalyst, result)
 */
package dev.tfp.core.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.blastfurnace.BlastFurnaceRules.WorldItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden test against {@code blastfurnace.tsv}, exported from TFC's {@code recipe/blast_furnace/} at c3b4d9536 by a scratch
 * script. The folder holds one recipe, pig iron: 1 mB of cast iron and 1 flux make 1 mB of pig iron. The row count is
 * checked against that file count. Each row's assembled output is checked at the probe amounts the script computed from the
 * JSON with TFC's floor rule, and its matching decisions are checked against the Java rules.
 */
class BlastFurnaceGoldenTest {
    private static final String RESOURCE = "blastfurnace.tsv";
    /** The number of JSON files in TFC's {@code data/tfc/recipe/blast_furnace/} at c3b4d9536. */
    private static final int EXPECTED_FILES = 1;
    private static final String COAL = "tfc:fuel/charcoal";

    @Test
    void rowCountMatchesTfcFileCount() {
        assertThat(rows()).hasSize(EXPECTED_FILES);
    }

    @Test
    void recipeFieldsMatchTheRow() {
        for (Map<String, String> row : rows()) {
            BlastFurnaceRecipe recipe = recipe(row);
            assertThat(recipe.inputFluid()).isEqualTo(row.get("input_fluid"));
            assertThat(recipe.inputAmount()).isEqualTo(Integer.parseInt(row.get("input_amount")));
            assertThat(recipe.catalyst()).isEqualTo(row.get("catalyst"));
            assertThat(recipe.outputFluid()).isEqualTo(row.get("output_fluid"));
            assertThat(recipe.outputAmount()).isEqualTo(Integer.parseInt(row.get("output_amount")));
            assertThat(recipe.matchesInput(row.get("input_fluid"))).isTrue();
            assertThat(recipe.matchesInput(row.get("output_fluid"))).isFalse();
            assertThat(recipe.matchesCatalyst(row.get("catalyst"))).isTrue();
            assertThat(recipe.matchesCatalyst(COAL)).isFalse();
        }
    }

    @Test
    void assembledOutputMatchesTheRow() {
        for (Map<String, String> row : rows()) {
            BlastFurnaceRecipe recipe = recipe(row);
            for (int probe : new int[] {0, 1, 2, 7}) {
                BlastFurnaceRecipe.Assembly assembly = recipe.assemble(probe);
                assertThat(assembly.amount()).as("output at %d mB", probe).isEqualTo(Integer.parseInt(row.get("out_at_" + probe)));
                assertThat(assembly.fluid()).isEqualTo(row.get("output_fluid"));
            }
            assertThat(recipe.assemble(7).remainder()).isEqualTo(Integer.parseInt(row.get("remainder_at_7")));
        }
    }

    @Test
    void recipeIsSelectedByItsMeltAndTheWorldItems() {
        List<BlastFurnaceRecipe> recipes = new ArrayList<>();
        for (Map<String, String> row : rows()) {
            recipes.add(recipe(row));
        }
        for (Map<String, String> row : rows()) {
            BlastFurnaceRecipe recipe = recipe(row);
            assertThat(BlastFurnaceRules.recipeForMelt(recipes, row.get("input_fluid"))).isEqualTo(recipe);
            assertThat(BlastFurnaceRules.cachedRecipe(recipes, List.of(row.get("input_fluid")))).isEqualTo(recipe);
            assertThat(BlastFurnaceRules.worldRecipe(recipes, List.of(
                new WorldItem(COAL, 1, null, true),
                new WorldItem("minecraft:iron_ingot", 1, row.get("input_fluid"), false)))).isEqualTo(recipe);
        }
    }

    @Test
    void intakeTakesTheRowCatalystAndInputs() {
        for (Map<String, String> row : rows()) {
            BlastFurnaceRecipe recipe = recipe(row);
            List<WorldItem> world = List.of(
                new WorldItem(row.get("catalyst"), 3, null, false),
                new WorldItem("minecraft:iron_ingot", 2, row.get("input_fluid"), false),
                new WorldItem(COAL, 5, null, true));
            BlastFurnaceRules.Intake intake = BlastFurnaceRules.intake(recipe, world, 0, 0, 0, 4 * 4);
            assertThat(intake.inputTotal()).isEqualTo(2);
            assertThat(intake.catalystTotal()).isEqualTo(2);
            assertThat(intake.fuelTotal()).isEqualTo(5);
        }
    }

    private static BlastFurnaceRecipe recipe(Map<String, String> row) {
        return new BlastFurnaceRecipe(
            row.get("input_fluid"),
            Integer.parseInt(row.get("input_amount")),
            row.get("catalyst"),
            row.get("output_fluid"),
            Integer.parseInt(row.get("output_amount")));
    }

    private static List<Map<String, String>> rows() {
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> lines = lines();
        String[] header = lines.get(0).split("\t", -1);
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split("\t", -1);
            assertThat(cells).hasSize(header.length);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                row.put(header[i], cells[i]);
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> lines() {
        try (InputStream in = BlastFurnaceGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("resource %s", RESOURCE).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
