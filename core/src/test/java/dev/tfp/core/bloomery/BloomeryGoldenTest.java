/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/bloomery/*.json @ c3b4d9536 (fluid, catalyst, result, duration),
 *       src/generated/resources/data/tfc/recipe/heating/raw_iron_bloom.json @ c3b4d9536 (the result's melting temperature)
 */
package dev.tfp.core.bloomery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden test against {@code bloomery.tsv}, exported from TFC's {@code recipe/bloomery/} at c3b4d9536. The folder holds
 * one recipe (the raw iron bloom: 100 mB of cast iron and 2 charcoal per bloom, 14400 ticks, a bloom at 1535 degrees
 * which starts 1 degree lower). The row count is checked against that file count, and each row's output rule is checked.
 *
 * <p>The melt items used below are stand-ins: only the fluid id and the per-item amount matter to the rule, so a unit
 * is a stand-in item that melts into 100 mB of cast iron.
 */
class BloomeryGoldenTest {
    private static final String RESOURCE = "bloomery.tsv";
    /** The number of JSON files in TFC's {@code data/tfc/recipe/bloomery/} at c3b4d9536. */
    private static final int EXPECTED_FILES = 1;
    private static final String CHARCOAL = "minecraft:charcoal";
    private static final String STAND_IN = "test:cast_iron_melt";

    @Test
    void rowCountMatchesTfcFileCount() {
        assertThat(rows()).hasSize(EXPECTED_FILES);
    }

    @Test
    void recipeFieldsMatchTheRow() {
        for (Map<String, String> row : rows()) {
            BloomeryRecipe recipe = recipe(row);
            assertThat(recipe.inputFluid()).isEqualTo(row.get("fluid"));
            assertThat(recipe.inputAmount()).isEqualTo(Integer.parseInt(row.get("fluid_amount")));
            assertThat(recipe.catalyst()).isEqualTo(row.get("catalyst"));
            assertThat(recipe.catalystCount()).isEqualTo(Integer.parseInt(row.get("catalyst_count")));
            assertThat(recipe.result()).isEqualTo(row.get("result"));
            assertThat(recipe.duration()).isEqualTo(Integer.parseInt(row.get("duration_ticks")));
            assertThat(recipe.matchesInput(row.get("fluid"))).isTrue();
            assertThat(recipe.matchesCatalyst(row.get("catalyst"), recipe.catalystCount())).isTrue();
            assertThat(recipe.matchesCatalyst(row.get("catalyst"), recipe.catalystCount() - 1)).isFalse();
        }
    }

    @Test
    void burnTimeFiresAtTheRowDuration() {
        for (Map<String, String> row : rows()) {
            BloomeryRecipe recipe = recipe(row);
            long lit = 5000L;
            assertThat(BloomeryRules.phaseAt(lit + recipe.duration() - 1, lit, recipe.duration(), BloomeryRules.NEVER))
                .isEqualTo(BloomeryRules.Phase.LIT);
            assertThat(BloomeryRules.phaseAt(lit + recipe.duration(), lit, recipe.duration(), BloomeryRules.NEVER))
                .isEqualTo(BloomeryRules.Phase.FIRED);
        }
    }

    @Test
    void outputRuleMatchesTheRow() {
        for (Map<String, String> row : rows()) {
            BloomeryRecipe recipe = recipe(row);
            float meltTemperature = Float.parseFloat(row.get("result_melt_temperature"));
            int fluid = recipe.inputAmount();
            int catalysts = recipe.catalystCount();

            // one batch short of fluid: nothing is made, and the contents are still consumed by the caller
            assertThat(BloomeryRules.produce(recipe, units(recipe.inputFluid(), fluid - 1, catalysts * 2), meltTemperature)).isNull();
            // one batch short of catalyst: nothing is made
            assertThat(BloomeryRules.produce(recipe, units(recipe.inputFluid(), fluid, catalysts - 1), meltTemperature)).isNull();
            // a single batch, starting one degree below the melting temperature
            BloomeryRules.Bloom one = BloomeryRules.produce(recipe, units(recipe.inputFluid(), fluid, catalysts), meltTemperature);
            assertThat(one).isEqualTo(new BloomeryRules.Bloom(row.get("result"), 1, meltTemperature - 1f));
            // two batches of fluid with three batches of catalyst make two: fluid is the limit
            BloomeryRules.Bloom two = BloomeryRules.produce(recipe, units(recipe.inputFluid(), 2 * fluid, 3 * catalysts), meltTemperature);
            assertThat(two.count()).isEqualTo(2);
            // three batches of fluid with two batches of catalyst make two: catalyst is the limit
            BloomeryRules.Bloom limitedByCatalyst = BloomeryRules.produce(recipe, units(recipe.inputFluid(), 3 * fluid, 2 * catalysts), meltTemperature);
            assertThat(limitedByCatalyst.count()).isEqualTo(2);
        }
    }

    @Test
    void intakeAcceptsAStackOfTheRowCatalyst() {
        for (Map<String, String> row : rows()) {
            BloomeryRecipe recipe = recipe(row);
            List<BloomeryItem> world = List.of(
                BloomeryItem.inert(row.get("catalyst"), recipe.catalystCount()),
                new BloomeryItem(STAND_IN, 3, row.get("fluid"), recipe.inputAmount()));
            assertThat(BloomeryRules.intake(recipe, world, 0, 16)).containsExactly(recipe.catalystCount(), 3);
        }
    }

    /** One stand-in unit carrying {@code fluidTotal} mB of the row's fluid, plus {@code catalysts} charcoal units. */
    private static List<BloomeryItem> units(String fluid, int fluidTotal, int catalysts) {
        List<BloomeryItem> out = new ArrayList<>();
        if (fluidTotal > 0) {
            out.add(new BloomeryItem(STAND_IN, 1, fluid, fluidTotal));
        }
        for (int i = 0; i < catalysts; i++) {
            out.add(BloomeryItem.inert(CHARCOAL, 1));
        }
        return out;
    }

    private static BloomeryRecipe recipe(Map<String, String> row) {
        return new BloomeryRecipe(
            row.get("fluid"),
            Integer.parseInt(row.get("fluid_amount")),
            row.get("catalyst"),
            Integer.parseInt(row.get("catalyst_count")),
            row.get("result"),
            Integer.parseInt(row.get("duration_ticks")));
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
        try (InputStream in = BloomeryGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("resource %s", RESOURCE).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
