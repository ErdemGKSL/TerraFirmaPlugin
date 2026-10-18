/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/heating/**.json @ c3b4d9536,
 *       src/generated/resources/data/tfc/tfc/fluid_heat/*.json @ c3b4d9536
 */
package dev.tfp.core.crucible;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.metal.Metal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden test against {@code heating.tsv} and {@code fluid_heat.tsv}, exported from TFC at c3b4d9536. The heating folder holds
 * 884 recipes: the crucible melts all of them, so the row count is checked against that file count. Each row's melt decision,
 * fluid amount and molten gate are checked against the Java rules.
 */
class CrucibleGoldenTest {
    private static final String HEATING = "heating.tsv";
    private static final String FLUID_HEAT = "fluid_heat.tsv";
    /** The number of JSON files in TFC's {@code data/tfc/recipe/heating/} (recursive) at c3b4d9536. */
    private static final int EXPECTED_HEATING_FILES = 884;
    /** The number of fluid heat entries in TFC's {@code data/tfc/tfc/fluid_heat/} at c3b4d9536: 27 metals and unknown. */
    private static final int EXPECTED_FLUID_HEAT_FILES = 28;
    private static final int DURABILITY_MAX = 100;
    private static final int DURABILITY_DAMAGE = 50;

    @Test
    void heatingRowCountMatchesTfcFileCount() {
        assertThat(rows(HEATING)).hasSize(EXPECTED_HEATING_FILES);
    }

    @Test
    void fluidHeatRowCountMatchesTfcFileCount() {
        assertThat(rows(FLUID_HEAT)).hasSize(EXPECTED_FLUID_HEAT_FILES);
    }

    @Test
    void everyMetalHasItsTfcMeltTemperature() {
        Map<String, String> melts = new LinkedHashMap<>();
        for (Map<String, String> row : rows(FLUID_HEAT)) {
            melts.put(row.get("fluid"), row.get("melt_temperature"));
        }
        for (Metal metal : Metal.values()) {
            assertThat(melts).containsKey(metal.id());
            assertThat(metal.meltTemperature()).isEqualTo(Float.parseFloat(melts.get(metal.id())));
        }
        assertThat(CrucibleRules.meltTemperature(Metal.UNKNOWN_ID))
            .contains(Float.parseFloat(melts.get(Metal.UNKNOWN_ID)));
    }

    @Test
    void everyHeatingFluidHasAMeltTemperature() {
        for (Map<String, String> row : rows(HEATING)) {
            if (!row.get("result_fluid").isEmpty()) {
                assertThat(row.get("melt_temperature")).as(row.get("file")).isNotEqualTo("NONE");
            }
        }
    }

    @Test
    void meltDecisionMatchesEachRow() {
        for (Map<String, String> row : rows(HEATING)) {
            CrucibleRecipe recipe = recipe(row);
            float temperature = recipe.temperature();
            CrucibleRules.Melt atTemperature = CrucibleRules.melt(recipe, temperature, 0, 0);
            CrucibleRules.Melt below = CrucibleRules.melt(recipe, Math.nextDown(temperature), 0, 0);
            assertThat(atTemperature.converts()).as(row.get("file")).isTrue();
            assertThat(below.converts()).as(row.get("file")).isFalse();
            assertThat(atTemperature.fluid()).isEqualTo(row.get("result_fluid"));
            assertThat(atTemperature.amount()).isEqualTo(Integer.parseInt(row.get("result_amount")));
        }
    }

    @Test
    void durabilityScalingMatchesEachRow() {
        for (Map<String, String> row : rows(HEATING)) {
            CrucibleRecipe recipe = recipe(row);
            int amount = Integer.parseInt(row.get("result_amount"));
            boolean durable = Boolean.parseBoolean(row.get("use_durability"));
            // A new item keeps the full amount, and a half worn item gets floor(amount x 0.5) when the recipe scales.
            assertThat(recipe.fluidAmount(0, DURABILITY_MAX)).as(row.get("file")).isEqualTo(amount);
            int half = recipe.fluidAmount(DURABILITY_DAMAGE, DURABILITY_MAX);
            assertThat(half).as(row.get("file")).isEqualTo(durable ? (int) Math.floor(amount * (1 - 0.5f)) : amount);
        }
    }

    @Test
    void moltenGateMatchesEachFluidHeatEntry() {
        for (Map<String, String> row : rows(HEATING)) {
            if (row.get("result_fluid").isEmpty()) {
                continue;
            }
            float melt = Float.parseFloat(row.get("melt_temperature"));
            String fluid = row.get("result_fluid");
            assertThat(CrucibleRules.isMolten(fluid, melt)).as(row.get("file")).isFalse();
            assertThat(CrucibleRules.isMolten(fluid, Math.nextUp(melt))).as(row.get("file")).isTrue();
        }
    }

    @Test
    void fillAcceptsEachHeatingFluidUpToCapacity() {
        for (Map<String, String> row : rows(HEATING)) {
            if (row.get("result_fluid").isEmpty()) {
                continue;
            }
            int amount = Integer.parseInt(row.get("result_amount"));
            FluidAlloy.Fill fill = CrucibleRules.fill(FluidAlloy.empty(), row.get("result_fluid"), amount, CrucibleRules.DEFAULT_CAPACITY);
            assertThat(fill.accepted()).as(row.get("file")).isEqualTo(amount);
        }
    }

    private static CrucibleRecipe recipe(Map<String, String> row) {
        String fluid = row.get("result_fluid");
        return new CrucibleRecipe(
            row.get("i_kind") + ":" + row.get("i_value"),
            Float.parseFloat(row.get("temperature")),
            fluid,
            fluid.isEmpty() ? 0 : Integer.parseInt(row.get("result_amount")),
            Boolean.parseBoolean(row.get("use_durability")),
            Boolean.parseBoolean(row.get("has_result_item"))
        );
    }

    /** Parses a TSV resource: the first line holds the column names, and each later line one row. */
    private static List<Map<String, String>> rows(String resource) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStream in = CrucibleGoldenTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            String[] header = lines[0].split("\t");
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) {
                    continue;
                }
                String[] cells = lines[i].split("\t", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < header.length; c++) {
                    row.put(header[c], cells[c]);
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
        return rows;
    }
}
