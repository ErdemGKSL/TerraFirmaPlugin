/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTick, growthTickStep, checkClimate),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java @ c3b4d9536 (constants),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/DoubleCropBlock.java @ c3b4d9536 (postGrowthTick),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/IPickableCrop.java @ c3b4d9536 (yieldItemStack),
 *       src/main/java/net/dries007/tfc/util/loot/CropYieldProvider.java @ c3b4d9536 (getFloat)
 */
package dev.tfp.core.crop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ClimateRange;
import dev.tfp.core.crop.CropRules.Alive;
import dev.tfp.core.crop.CropRules.Dead;
import dev.tfp.core.crop.CropRules.Died;
import dev.tfp.core.crop.CropRules.Grown;
import dev.tfp.core.crop.CropRules.Jitter;
import dev.tfp.core.crop.CropRules.NutrientStep;
import dev.tfp.core.crop.CropRules.Progress;
import dev.tfp.core.crop.CropRules.Sample;
import dev.tfp.core.crop.CropRules.Step;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-FARM-02 golden values from TFC at c3b4d9536, in {@code crop-golden.txt}. A scratch oracle copies TFC's crop arithmetic literally in
 * float32: the growth step with its climate checks, nutrient share, yield and death; the lazy catch-up loop of {@code growthTick}; the
 * stage of {@code postGrowthTick}; the harvest counts and regrowth; and the crop table from {@code Crop.java} and the crop climate
 * JSON. Each float is compared exactly, through its {@code Float.toString} form.
 */
class CropGoldenTest {
    private static final String RESOURCE = "/dev/tfp/core/crop/crop-golden.txt";
    private static final int CALENDAR_START = 3 * CalendarTicks.CALENDAR_TICKS_IN_DAY + 777;
    /** TFC {@code NUTRIENT_CONSUMPTION}: {@code 1f / (12 * CALENDAR_TICKS_IN_DAY)}. */
    private static final float NUTRIENT_CONSUMPTION = 1f / (12 * CalendarTicks.CALENDAR_TICKS_IN_DAY);

    @Test
    void cropTableMatchesTfc() throws IOException {
        List<String[]> rows = section("crop");
        assertThat(rows).hasSize(29);
        assertThat(CropType.values()).hasSize(29);
        for (String[] r : rows) {
            CropType crop = CropType.valueOf(r[0]);
            assertThat(crop.nitrogen()).as(r[0]).isEqualTo(Float.parseFloat(r[1]));
            assertThat(crop.phosphorus()).as(r[0]).isEqualTo(Float.parseFloat(r[2]));
            assertThat(crop.potassium()).as(r[0]).isEqualTo(Float.parseFloat(r[3]));
            assertThat(crop.shape().name()).as(r[0]).isEqualTo(r[4]);
            assertThat(crop.stages().maxAge()).as(r[0]).isEqualTo(Integer.parseInt(r[5]));
            assertThat(crop.stages().maxSingleAge()).as(r[0]).isEqualTo(Integer.parseInt(r[6]));
            ClimateRange climate = crop.climate();
            assertThat(climate.minHydration()).as(r[0]).isEqualTo(Integer.parseInt(r[7]));
            assertThat(climate.maxHydration()).as(r[0]).isEqualTo(Integer.parseInt(r[8]));
            assertThat(climate.minTemperature()).as(r[0]).isEqualTo(Float.parseFloat(r[9]));
            assertThat(climate.maxTemperature()).as(r[0]).isEqualTo(Float.parseFloat(r[10]));
            assertThat(crop.serializedName()).isEqualTo(r[0].toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Test
    void stagesMatchTfcPostGrowthTick() throws IOException {
        List<String[]> rows = section("stage");
        assertThat(rows).hasSize(261);
        for (String[] r : rows) {
            CropStages stages = CropType.valueOf(r[0]).stages();
            CropStages.Stage stage = stages.stage(Float.parseFloat(r[1]));
            assertThat(stage.age()).as("%s growth %s", r[0], r[1]).isEqualTo(Integer.parseInt(r[2]));
            assertThat(stage.top()).as("%s growth %s", r[0], r[1]).isEqualTo(r[3].equals("1"));
        }
    }

    @Test
    void growthStepMatchesTfc() throws IOException {
        List<String[]> rows = section("step");
        assertThat(rows).hasSize(696);
        int alive = 0, deadFull = 0, deadPart = 0;
        for (String[] r : rows) {
            CropType crop = CropType.valueOf(r[0]);
            float gm = Float.parseFloat(r[1]), em = Float.parseFloat(r[2]), gl = Float.parseFloat(r[3]);
            float jitter = Float.parseFloat(r[4]);
            long from = Long.parseLong(r[5]), to = Long.parseLong(r[6]);
            Sample start = new Sample(Integer.parseInt(r[7]), Float.parseFloat(r[9]));
            Sample end = new Sample(Integer.parseInt(r[8]), Float.parseFloat(r[10]));
            CropState state = new CropState(Float.parseFloat(r[11]), Float.parseFloat(r[12]), Float.parseFloat(r[13]), from);
            NutrientStep nutrients = new NutrientStep(Float.parseFloat(r[14]), Float.parseFloat(r[15]), Float.parseFloat(r[16]),
                Float.parseFloat(r[17]));
            // columns after "=>": status, growth, expiry, yield, delta, pct
            String status = r[19];
            float expectedDelta = Float.parseFloat(r[23]), expectedPct = Float.parseFloat(r[24]);
            Step step = CropRules.step(state, crop.spec(gm, em, gl), start, end, from, to, jitter, nutrients);
            if (status.equals("ALIVE")) {
                alive++;
                assertThat(step).as(String.join(" ", r)).isInstanceOf(Grown.class);
                Grown grown = (Grown) step;
                assertThat(grown.state().growth()).as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[20]));
                assertThat(grown.state().expiry()).as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[21]));
                assertThat(grown.state().yield()).as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[22]));
                assertThat(grown.state().lastTick()).isEqualTo(to);
                assertThat(grown.growthDelta()).as(String.join(" ", r)).isEqualTo(expectedDelta);
                assertThat(grown.percentSatisfied()).as(String.join(" ", r)).isEqualTo(expectedPct);
            } else {
                boolean fully = status.equals("DEAD_FULL");
                if (fully) {
                    deadFull++;
                } else {
                    deadPart++;
                }
                assertThat(step).as(String.join(" ", r)).isInstanceOf(Died.class);
                Died died = (Died) step;
                assertThat(died.fullyGrown()).as(String.join(" ", r)).isEqualTo(fully);
                assertThat(died.growthDelta()).as(String.join(" ", r)).isEqualTo(expectedDelta);
                assertThat(died.percentSatisfied()).as(String.join(" ", r)).isEqualTo(expectedPct);
            }
        }
        // The grid exercises both outcomes and both death kinds.
        // Pinned to the TFC-derived grid: each value above is checked; these counts pin the grid's shape.
        assertThat(alive).isEqualTo(90);
        assertThat(deadFull).isZero();
        assertThat(deadPart).isPositive();
    }

    @Test
    void lazyCatchUpMatchesTfcGrowthTick() throws IOException {
        List<String[]> rows = section("lazy");
        assertThat(rows).hasSize(812);
        int dead = 0;
        for (String[] r : rows) {
            CropType crop = CropType.valueOf(r[0]);
            int sched = Integer.parseInt(r[1]);
            long elapsed = Long.parseLong(r[2]);
            Progress progress = CropRules.advance(CropState.planted(CALENDAR_START), crop.spec(1f, 1f, 1f), schedule(sched),
                feed(crop), Jitter.seeded(42L), CALENDAR_START + elapsed);
            if (r[4].equals("ALIVE")) {
                assertThat(progress).as(String.join(" ", r)).isInstanceOf(Alive.class);
                CropState state = ((Alive) progress).state();
                assertThat(state.growth()).as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[5]));
                assertThat(state.expiry()).as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[6]));
                assertThat(state.yield()).as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[7]));
                assertThat(state.lastTick()).as(String.join(" ", r)).isEqualTo(Long.parseLong(r[8]));
            } else {
                dead++;
                assertThat(progress).as(String.join(" ", r)).isInstanceOf(Dead.class);
                Dead d = (Dead) progress;
                assertThat(d.fullyGrown()).as(String.join(" ", r)).isEqualTo(r[5].equals("1"));
                assertThat(d.diedAt()).as(String.join(" ", r)).isEqualTo(Long.parseLong(r[6]));
            }
        }
        assertThat(dead).isGreaterThan(50);
    }

    @Test
    void harvestMatchesTfc() throws IOException {
        int picks = 0, regrows = 0, loots = 0;
        for (String[] r : section("pick")) {
            picks++;
            assertThat(CropHarvest.pickCount(Float.parseFloat(r[0]), Integer.parseInt(r[1])))
                .as(String.join(" ", r)).isEqualTo(Integer.parseInt(r[2]));
        }
        for (String[] r : section("regrow")) {
            regrows++;
            CropHarvest.Fruit fruit = CropHarvest.Fruit.valueOf(r[0]);
            assertThat(CropHarvest.regrowGrowth(fruit, Float.parseFloat(r[1]))).as(String.join(" ", r))
                .isEqualTo(Float.parseFloat(r[2]));
        }
        for (String[] r : section("loot")) {
            loots++;
            assertThat(CropHarvest.lootYield(Float.parseFloat(r[0]), Float.parseFloat(r[1]), Float.parseFloat(r[2])))
                .as(String.join(" ", r)).isEqualTo(Float.parseFloat(r[3]));
        }
        assertThat(picks).isEqualTo(12);
        assertThat(regrows).isEqualTo(6);
        assertThat(loots).isEqualTo(9);
    }

    /** The climate of each lazy schedule, as the oracle's {@code hyd} and {@code temp}. */
    static CropRules.ClimateSource schedule(int sched) {
        return t -> {
            long day = t / CalendarTicks.CALENDAR_TICKS_IN_DAY;
            return switch (sched) {
                case 0 -> new Sample(50, 10f);
                case 1 -> new Sample(50, -40f);
                case 2 -> new Sample((int) ((day * 37 + 11) % 101), (float) (-25 + (day * 13) % 60));
                default -> new Sample((int) ((day * 7 + 60) % 101), (float) (((day * 5) % 40) - 5));
            };
        };
    }

    /** The soil of the lazy rows: each demand is met, 3/4 of the need is taken and half of it is available. */
    static CropRules.NutrientFeed feed(CropType crop) {
        return (from, to) -> {
            final long tickDelta = to - from;
            final float nReq = NUTRIENT_CONSUMPTION * tickDelta * crop.nitrogen();
            final float pReq = NUTRIENT_CONSUMPTION * tickDelta * crop.phosphorus();
            final float kReq = NUTRIENT_CONSUMPTION * tickDelta * crop.potassium();
            final float required = Math.max(0, nReq) + Math.max(0, pReq) + Math.max(0, kReq);
            final float sumPos = Math.max(0, crop.nitrogen()) + Math.max(0, crop.phosphorus()) + Math.max(0, crop.potassium());
            final float forGrowth = sumPos > 0 ? sumPos : 1f;
            final float consumed = sumPos > 0 ? required * 0.75f : 0f;
            final float available = sumPos > 0 ? required * 0.5f : 0f;
            return new NutrientStep(consumed, forGrowth, required, available);
        };
    }

    private static List<String[]> section(String kind) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = CropGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as(RESOURCE).isNotNull();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.trim().split(" ");
                    if (parts[0].equals(kind)) {
                        rows.add(java.util.Arrays.copyOfRange(parts, 1, parts.length));
                    }
                }
            }
        }
        return rows;
    }
}
