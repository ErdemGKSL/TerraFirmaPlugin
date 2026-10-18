/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (hydration, findMinCostWater),
 *       src/main/java/net/dries007/tfc/common/blockentities/IFarmland.java @ c3b4d9536 (updateAdditionalWater, consumeNutrients,
 *       produceNutrients),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTickStep nutrient section, getSoilModifier)
 */
package dev.tfp.core.farm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.farm.FarmlandRules.SoilHydration;
import dev.tfp.core.farm.NutrientGrowth.Demand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-FARM-01 golden values from TFC at c3b4d9536, in {@code farm-golden.txt}. The file is produced by a scratch oracle that
 * copies TFC's expressions literally in float32: rain hydration, instant hydration, the source search over every offset in
 * reach, the reserve update, the soil tiers, the nutrient consume and produce, and the growth-step nutrient section over a grid of
 * nutrient levels, demands, absorbed amounts, tick deltas, growth deltas and soil bonuses.
 */
class FarmlandGoldenTest {
    private static final String RESOURCE = "/dev/tfp/core/farm/farm-golden.txt";

    @Test
    void rainHydrationMatchesTfc() throws IOException {
        List<String[]> rows = section("rain");
        assertThat(rows).hasSize(13);
        for (String[] row : rows) {
            assertThat(FarmlandRules.rainHydration(Float.parseFloat(row[0])))
                .as("rainfall %s", row[0])
                .isEqualTo(Integer.parseInt(row[1]));
        }
    }

    @Test
    void instantHydrationMatchesTfcOverRainWaterlogSourceAndSoil() throws IOException {
        List<String[]> rows = section("hyd");
        assertThat(rows).hasSize(156);
        for (String[] row : rows) {
            int rain = Integer.parseInt(row[1]);
            boolean waterlogged = row[2].equals("1");
            boolean source = row[3].equals("1");
            float multiplier = Float.parseFloat(row[4]);
            int expected = Integer.parseInt(row[5]);
            assertThat(FarmlandRules.instantHydration(rain, waterlogged, source, multiplier))
                .as("rain %d waterlogged %s source %s mult %s", rain, waterlogged, source, row[4])
                .isEqualTo(expected);
            // The lazy moisture from the rainfall must agree with the two-step form, for the matching soil tier.
            SoilHydration tier = switch (row[4]) {
                case "2.0" -> SoilHydration.INCREASES;
                case "0.5" -> SoilHydration.DECREASES;
                default -> SoilHydration.NORMAL;
            };
            assertThat(FarmlandRules.moisture(Float.parseFloat(row[0]), waterlogged, source, tier)).isEqualTo(expected);
        }
    }

    @Test
    void sourceSearchMatchesTfcForEverySingleCellInReach() throws IOException {
        List<String[]> rows = section("cost");
        assertThat(rows).hasSize(163);
        int checks = 0;
        for (String[] row : rows) {
            if (row[0].equals("none")) {
                assertThat(FarmlandRules.minCostWater((dx, dy, dz) -> false)).isEqualTo(Integer.parseInt(row[1]));
            } else {
                int dx = Integer.parseInt(row[0]);
                int dy = Integer.parseInt(row[1]);
                int dz = Integer.parseInt(row[2]);
                int expected = Integer.parseInt(row[3]);
                int cost = FarmlandRules.minCostWater((x, y, z) -> x == dx && y == dy && z == dz);
                assertThat(cost).as("water at %d %d %d", dx, dy, dz).isEqualTo(expected);
                assertThat(FarmlandRules.sourcePresent(cost)).isEqualTo(expected < 5);
            }
            checks++;
        }
        assertThat(checks).isEqualTo(163);
    }

    @Test
    void reserveUpdateMatchesTfcAcrossGapsAndCaps() throws IOException {
        List<String[]> rows = section("water");
        assertThat(rows).hasSize(60);
        for (String[] row : rows) {
            float previous = Float.parseFloat(row[0]);
            long from = Long.parseLong(row[1]);
            long to = Long.parseLong(row[2]);
            float expected = Float.parseFloat(row[3]);
            assertThat(FarmlandRules.additionalWater(previous, from, to))
                .as("prev %s from %d to %d", row[0], from, to)
                .isEqualTo(expected);
        }
    }

    @Test
    void soilTiersMatchTfc() throws IOException {
        List<String[]> rows = section("soil");
        assertThat(rows).hasSize(5);
        for (String[] row : rows) {
            assertThat(SoilTier.values()[Integer.parseInt(row[0])].bonus()).isEqualTo(Float.parseFloat(row[1]));
        }
    }

    @Test
    void nutrientConsumeMatchesTfc() throws IOException {
        List<String[]> rows = section("cons");
        assertThat(rows).hasSize(20);
        for (String[] row : rows) {
            Nutrients soil = new Nutrients(Float.parseFloat(row[0]), 0f, 0f);
            Nutrients.Taken taken = soil.consume(Nutrient.NITROGEN, Float.parseFloat(row[1]));
            assertThat(taken.taken()).as("consume %s %s", row[0], row[1]).isEqualTo(Float.parseFloat(row[2]));
            assertThat(taken.after().nitrogen()).isEqualTo(Float.parseFloat(row[3]));
        }
    }

    @Test
    void nutrientProduceMatchesTfc() throws IOException {
        List<String[]> rows = section("prod");
        assertThat(rows).hasSize(144);
        for (String[] row : rows) {
            Nutrients soil = new Nutrients(Float.parseFloat(row[0]), 0f, 0f);
            Nutrients after = soil.produce(Nutrient.NITROGEN, Float.parseFloat(row[1]), Float.parseFloat(row[2]), Float.parseFloat(row[3]));
            assertThat(after.nitrogen()).as("produce %s", String.join(" ", row)).isEqualTo(Float.parseFloat(row[4]));
        }
    }

    @Test
    void growthStepNutrientsMatchTfcOverDemandsAbsorbedTicksAndBonus() throws IOException {
        List<String[]> rows = section("grow");
        assertThat(rows).hasSize(1296);
        for (String[] row : rows) {
            // tokens: soil(3) | demand(3) | absorbed(3) | tickDelta | growthDelta | bonus -> soilAfterConsume(3) | taken(3) | consumed required available forGrowth
            float[] v = new float[22];
            int i = 0;
            for (String token : row) {
                if (token.equals("|") || token.equals("->")) {
                    continue;
                }
                v[i++] = Float.parseFloat(token);
            }
            assertThat(i).isEqualTo(22);
            Nutrients soil = new Nutrients(v[0], v[1], v[2]);
            Demand forGrowth = new Demand(v[3], v[4], v[5]);
            Demand absorbed = new Demand(v[6], v[7], v[8]);
            long tickDelta = (long) v[9];
            float growthDelta = v[10];
            float bonus = v[11];
            NutrientGrowth.Consumption step = NutrientGrowth.consumeForGrowthStep(soil, forGrowth, absorbed, tickDelta);
            assertThat(step.taken()).as("taken %s", String.join(" ", row))
                .isEqualTo(new Demand(v[15], v[16], v[17]));
            assertThat(step.consumed()).isEqualTo(v[18]);
            assertThat(step.required()).isEqualTo(v[19]);
            assertThat(step.available()).isEqualTo(v[20]);
            assertThat(step.forGrowth()).isEqualTo(v[21]);
            Nutrients after = NutrientGrowth.produceAfterGrowthStep(step, forGrowth, bonus, growthDelta);
            assertThat(after.nitrogen()).as("after N %s", String.join(" ", row)).isEqualTo(v[12]);
            assertThat(after.phosphorus()).isEqualTo(v[13]);
            assertThat(after.potassium()).isEqualTo(v[14]);
        }
    }

    /** The rows of one section: lines whose first word is {@code key}, split on whitespace, with the key removed. */
    private static List<String[]> section(String key) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = FarmlandGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("resource %s", RESOURCE).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts[0].equals(key)) {
                    String[] rest = new String[parts.length - 1];
                    System.arraycopy(parts, 1, rest, 0, rest.length);
                    rows.add(rest);
                }
            }
        }
        return rows;
    }
}
