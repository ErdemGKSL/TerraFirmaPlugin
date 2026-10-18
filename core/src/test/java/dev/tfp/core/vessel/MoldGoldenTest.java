/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Mold.java @ c3b4d9536 (fill, isMolten, updateFluid),
 *       src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (fill),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (mold capacity defaults)
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-POT-02 golden values for the mold rules at c3b4d9536. The rows come from a scratch copy of TFC's arithmetic
 * ({@code scratchpad/vessel/gen_golden.py}): integer rows are the {@code FluidComponent.fill} and {@code isMolten} and
 * firing comparisons, and float rows are {@code POTTERY_HEAT_CAPACITY + specificHeat x amount} evaluated in float32 per
 * operation.
 */
class MoldGoldenTest {

    // from TFC at c3b4d9536: MoldKind|capacity (mB)
    private static final String MOLD_CAP = """
            INGOT|100
            FIRE_INGOT|100
            PICKAXE_HEAD|100
            PROPICK_HEAD|100
            AXE_HEAD|100
            SHOVEL_HEAD|100
            HOE_HEAD|100
            CHISEL_HEAD|100
            HAMMER_HEAD|100
            SAW_BLADE|100
            JAVELIN_HEAD|100
            SWORD_BLADE|200
            MACE_HEAD|200
            KNIFE_BLADE|100
            SCYTHE_BLADE|100
            BELL|100
            """;

    // from TFC at c3b4d9536: capacity|contentFluid(E empty, A fluid a, B fluid b)|contentAmount|inputAmount|canContain|newAmount|filled
    private static final String MOLD_FILL = """
            100|E|0|0|1|0|0
            100|E|0|0|0|0|0
            100|E|0|1|1|1|1
            100|E|0|1|0|0|0
            100|E|0|50|1|50|50
            100|E|0|50|0|0|0
            100|E|0|99|1|99|99
            100|E|0|99|0|0|0
            100|E|0|100|1|100|100
            100|E|0|100|0|0|0
            100|E|0|150|1|100|100
            100|E|0|150|0|0|0
            100|E|0|200|1|100|100
            100|E|0|200|0|0|0
            100|E|0|250|1|100|100
            100|E|0|250|0|0|0
            100|A|1|0|1|1|0
            100|A|1|0|0|1|0
            100|A|1|1|1|2|1
            100|A|1|1|0|1|0
            100|A|1|50|1|51|50
            100|A|1|50|0|1|0
            100|A|1|99|1|100|99
            100|A|1|99|0|1|0
            100|A|1|100|1|100|99
            100|A|1|100|0|1|0
            100|A|1|150|1|100|99
            100|A|1|150|0|1|0
            100|A|1|200|1|100|99
            100|A|1|200|0|1|0
            100|A|1|250|1|100|99
            100|A|1|250|0|1|0
            100|A|50|0|1|50|0
            100|A|50|0|0|50|0
            100|A|50|1|1|51|1
            100|A|50|1|0|50|0
            100|A|50|50|1|100|50
            100|A|50|50|0|50|0
            100|A|50|99|1|100|50
            100|A|50|99|0|50|0
            100|A|50|100|1|100|50
            100|A|50|100|0|50|0
            100|A|50|150|1|100|50
            100|A|50|150|0|50|0
            100|A|50|200|1|100|50
            100|A|50|200|0|50|0
            100|A|50|250|1|100|50
            100|A|50|250|0|50|0
            100|A|99|0|1|99|0
            100|A|99|0|0|99|0
            100|A|99|1|1|100|1
            100|A|99|1|0|99|0
            100|A|99|50|1|100|1
            100|A|99|50|0|99|0
            100|A|99|99|1|100|1
            100|A|99|99|0|99|0
            100|A|99|100|1|100|1
            100|A|99|100|0|99|0
            100|A|99|150|1|100|1
            100|A|99|150|0|99|0
            100|A|99|200|1|100|1
            100|A|99|200|0|99|0
            100|A|99|250|1|100|1
            100|A|99|250|0|99|0
            100|A|100|0|1|100|0
            100|A|100|0|0|100|0
            100|A|100|1|1|100|0
            100|A|100|1|0|100|0
            100|A|100|50|1|100|0
            100|A|100|50|0|100|0
            100|A|100|99|1|100|0
            100|A|100|99|0|100|0
            100|A|100|100|1|100|0
            100|A|100|100|0|100|0
            100|A|100|150|1|100|0
            100|A|100|150|0|100|0
            100|A|100|200|1|100|0
            100|A|100|200|0|100|0
            100|A|100|250|1|100|0
            100|A|100|250|0|100|0
            100|A|150|0|1|150|0
            100|A|150|0|0|150|0
            100|A|150|1|1|100|-50
            100|A|150|1|0|150|0
            100|A|150|50|1|100|-50
            100|A|150|50|0|150|0
            100|A|150|99|1|100|-50
            100|A|150|99|0|150|0
            100|A|150|100|1|100|-50
            100|A|150|100|0|150|0
            100|A|150|150|1|100|-50
            100|A|150|150|0|150|0
            100|A|150|200|1|100|-50
            100|A|150|200|0|150|0
            100|A|150|250|1|100|-50
            100|A|150|250|0|150|0
            100|A|199|0|1|199|0
            100|A|199|0|0|199|0
            100|A|199|1|1|100|-99
            100|A|199|1|0|199|0
            100|A|199|50|1|100|-99
            100|A|199|50|0|199|0
            100|A|199|99|1|100|-99
            100|A|199|99|0|199|0
            100|A|199|100|1|100|-99
            100|A|199|100|0|199|0
            100|A|199|150|1|100|-99
            100|A|199|150|0|199|0
            100|A|199|200|1|100|-99
            100|A|199|200|0|199|0
            100|A|199|250|1|100|-99
            100|A|199|250|0|199|0
            100|A|200|0|1|200|0
            100|A|200|0|0|200|0
            100|A|200|1|1|100|-100
            100|A|200|1|0|200|0
            100|A|200|50|1|100|-100
            100|A|200|50|0|200|0
            100|A|200|99|1|100|-100
            100|A|200|99|0|200|0
            100|A|200|100|1|100|-100
            100|A|200|100|0|200|0
            100|A|200|150|1|100|-100
            100|A|200|150|0|200|0
            100|A|200|200|1|100|-100
            100|A|200|200|0|200|0
            100|A|200|250|1|100|-100
            100|A|200|250|0|200|0
            100|B|1|0|1|1|0
            100|B|1|0|0|1|0
            100|B|1|1|1|1|0
            100|B|1|1|0|1|0
            100|B|1|50|1|1|0
            100|B|1|50|0|1|0
            100|B|1|99|1|1|0
            100|B|1|99|0|1|0
            100|B|1|100|1|1|0
            100|B|1|100|0|1|0
            100|B|1|150|1|1|0
            100|B|1|150|0|1|0
            100|B|1|200|1|1|0
            100|B|1|200|0|1|0
            100|B|1|250|1|1|0
            100|B|1|250|0|1|0
            100|B|50|0|1|50|0
            100|B|50|0|0|50|0
            100|B|50|1|1|50|0
            100|B|50|1|0|50|0
            100|B|50|50|1|50|0
            100|B|50|50|0|50|0
            100|B|50|99|1|50|0
            100|B|50|99|0|50|0
            100|B|50|100|1|50|0
            100|B|50|100|0|50|0
            100|B|50|150|1|50|0
            100|B|50|150|0|50|0
            100|B|50|200|1|50|0
            100|B|50|200|0|50|0
            100|B|50|250|1|50|0
            100|B|50|250|0|50|0
            100|B|100|0|1|100|0
            100|B|100|0|0|100|0
            100|B|100|1|1|100|0
            100|B|100|1|0|100|0
            100|B|100|50|1|100|0
            100|B|100|50|0|100|0
            100|B|100|99|1|100|0
            100|B|100|99|0|100|0
            100|B|100|100|1|100|0
            100|B|100|100|0|100|0
            100|B|100|150|1|100|0
            100|B|100|150|0|100|0
            100|B|100|200|1|100|0
            100|B|100|200|0|100|0
            100|B|100|250|1|100|0
            100|B|100|250|0|100|0
            200|E|0|0|1|0|0
            200|E|0|0|0|0|0
            200|E|0|1|1|1|1
            200|E|0|1|0|0|0
            200|E|0|50|1|50|50
            200|E|0|50|0|0|0
            200|E|0|99|1|99|99
            200|E|0|99|0|0|0
            200|E|0|100|1|100|100
            200|E|0|100|0|0|0
            200|E|0|150|1|150|150
            200|E|0|150|0|0|0
            200|E|0|200|1|200|200
            200|E|0|200|0|0|0
            200|E|0|250|1|200|200
            200|E|0|250|0|0|0
            200|A|1|0|1|1|0
            200|A|1|0|0|1|0
            200|A|1|1|1|2|1
            200|A|1|1|0|1|0
            200|A|1|50|1|51|50
            200|A|1|50|0|1|0
            200|A|1|99|1|100|99
            200|A|1|99|0|1|0
            200|A|1|100|1|101|100
            200|A|1|100|0|1|0
            200|A|1|150|1|151|150
            200|A|1|150|0|1|0
            200|A|1|200|1|200|199
            200|A|1|200|0|1|0
            200|A|1|250|1|200|199
            200|A|1|250|0|1|0
            200|A|50|0|1|50|0
            200|A|50|0|0|50|0
            200|A|50|1|1|51|1
            200|A|50|1|0|50|0
            200|A|50|50|1|100|50
            200|A|50|50|0|50|0
            200|A|50|99|1|149|99
            200|A|50|99|0|50|0
            200|A|50|100|1|150|100
            200|A|50|100|0|50|0
            200|A|50|150|1|200|150
            200|A|50|150|0|50|0
            200|A|50|200|1|200|150
            200|A|50|200|0|50|0
            200|A|50|250|1|200|150
            200|A|50|250|0|50|0
            200|A|99|0|1|99|0
            200|A|99|0|0|99|0
            200|A|99|1|1|100|1
            200|A|99|1|0|99|0
            200|A|99|50|1|149|50
            200|A|99|50|0|99|0
            200|A|99|99|1|198|99
            200|A|99|99|0|99|0
            200|A|99|100|1|199|100
            200|A|99|100|0|99|0
            200|A|99|150|1|200|101
            200|A|99|150|0|99|0
            200|A|99|200|1|200|101
            200|A|99|200|0|99|0
            200|A|99|250|1|200|101
            200|A|99|250|0|99|0
            200|A|100|0|1|100|0
            200|A|100|0|0|100|0
            200|A|100|1|1|101|1
            200|A|100|1|0|100|0
            200|A|100|50|1|150|50
            200|A|100|50|0|100|0
            200|A|100|99|1|199|99
            200|A|100|99|0|100|0
            200|A|100|100|1|200|100
            200|A|100|100|0|100|0
            200|A|100|150|1|200|100
            200|A|100|150|0|100|0
            200|A|100|200|1|200|100
            200|A|100|200|0|100|0
            200|A|100|250|1|200|100
            200|A|100|250|0|100|0
            200|A|150|0|1|150|0
            200|A|150|0|0|150|0
            200|A|150|1|1|151|1
            200|A|150|1|0|150|0
            200|A|150|50|1|200|50
            200|A|150|50|0|150|0
            200|A|150|99|1|200|50
            200|A|150|99|0|150|0
            200|A|150|100|1|200|50
            200|A|150|100|0|150|0
            200|A|150|150|1|200|50
            200|A|150|150|0|150|0
            200|A|150|200|1|200|50
            200|A|150|200|0|150|0
            200|A|150|250|1|200|50
            200|A|150|250|0|150|0
            200|A|199|0|1|199|0
            200|A|199|0|0|199|0
            200|A|199|1|1|200|1
            200|A|199|1|0|199|0
            200|A|199|50|1|200|1
            200|A|199|50|0|199|0
            200|A|199|99|1|200|1
            200|A|199|99|0|199|0
            200|A|199|100|1|200|1
            200|A|199|100|0|199|0
            200|A|199|150|1|200|1
            200|A|199|150|0|199|0
            200|A|199|200|1|200|1
            200|A|199|200|0|199|0
            200|A|199|250|1|200|1
            200|A|199|250|0|199|0
            200|A|200|0|1|200|0
            200|A|200|0|0|200|0
            200|A|200|1|1|200|0
            200|A|200|1|0|200|0
            200|A|200|50|1|200|0
            200|A|200|50|0|200|0
            200|A|200|99|1|200|0
            200|A|200|99|0|200|0
            200|A|200|100|1|200|0
            200|A|200|100|0|200|0
            200|A|200|150|1|200|0
            200|A|200|150|0|200|0
            200|A|200|200|1|200|0
            200|A|200|200|0|200|0
            200|A|200|250|1|200|0
            200|A|200|250|0|200|0
            200|B|1|0|1|1|0
            200|B|1|0|0|1|0
            200|B|1|1|1|1|0
            200|B|1|1|0|1|0
            200|B|1|50|1|1|0
            200|B|1|50|0|1|0
            200|B|1|99|1|1|0
            200|B|1|99|0|1|0
            200|B|1|100|1|1|0
            200|B|1|100|0|1|0
            200|B|1|150|1|1|0
            200|B|1|150|0|1|0
            200|B|1|200|1|1|0
            200|B|1|200|0|1|0
            200|B|1|250|1|1|0
            200|B|1|250|0|1|0
            200|B|50|0|1|50|0
            200|B|50|0|0|50|0
            200|B|50|1|1|50|0
            200|B|50|1|0|50|0
            200|B|50|50|1|50|0
            200|B|50|50|0|50|0
            200|B|50|99|1|50|0
            200|B|50|99|0|50|0
            200|B|50|100|1|50|0
            200|B|50|100|0|50|0
            200|B|50|150|1|50|0
            200|B|50|150|0|50|0
            200|B|50|200|1|50|0
            200|B|50|200|0|50|0
            200|B|50|250|1|50|0
            200|B|50|250|0|50|0
            200|B|100|0|1|100|0
            200|B|100|0|0|100|0
            200|B|100|1|1|100|0
            200|B|100|1|0|100|0
            200|B|100|50|1|100|0
            200|B|100|50|0|100|0
            200|B|100|99|1|100|0
            200|B|100|99|0|100|0
            200|B|100|100|1|100|0
            200|B|100|100|0|100|0
            200|B|100|150|1|100|0
            200|B|100|150|0|100|0
            200|B|100|200|1|100|0
            200|B|100|200|0|100|0
            200|B|100|250|1|100|0
            200|B|100|250|0|100|0
            """;

    // from TFC at c3b4d9536: specificHeat|amount|heatCapacity
    private static final String MOLD_HEAT_CAP = """
            0.5|0|1.2000000476837158
            0.5|1|1.7000000476837158
            0.5|100|51.20000076293945
            0.5|200|101.19999694824219
            0.5|3000|1501.199951171875
            1.25|0|1.2000000476837158
            1.25|1|2.450000047683716
            1.25|100|126.19999694824219
            1.25|200|251.1999969482422
            1.25|3000|3751.199951171875
            2.0|0|1.2000000476837158
            2.0|1|3.200000047683716
            2.0|100|201.1999969482422
            2.0|200|401.20001220703125
            2.0|3000|6001.2001953125
            0.11428571492433548|0|1.2000000476837158
            0.11428571492433548|1|1.3142857551574707
            0.11428571492433548|100|12.628571510314941
            0.11428571492433548|200|24.057144165039062
            0.11428571492433548|3000|344.0571594238281
            """;

    // from TFC at c3b4d9536: temperature|moldMoltenAt1200|vesselMoltenAt1200 (1 = true)
    private static final String MOLTEN = """
            1199.0|0|0
            1200.0|1|0
            1200.5|1|1
            """;

    // from TFC at c3b4d9536: temperature|firesAt1399 (1 = true)
    private static final String FIRING = """
            0.0|0
            1398.9000244140625|0
            1399.0|1
            1399.0999755859375|1
            1400.0|1
            """;

    private static List<String[]> rows(String block) {
        List<String[]> rows = new ArrayList<>();
        for (String line : block.strip().split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                rows.add(trimmed.split("\\|", -1));
            }
        }
        return rows;
    }

    private static FluidAmount content(String kind, int amount) {
        return switch (kind) {
            case "E" -> FluidAmount.EMPTY;
            case "A" -> new FluidAmount("a", amount);
            case "B" -> new FluidAmount("b", amount);
            default -> throw new IllegalArgumentException(kind);
        };
    }

    @Test
    void defaultCapacitiesMatchTfc() {
        List<String[]> rows = rows(MOLD_CAP);
        assertThat(rows).hasSize(MoldKind.values().length);
        for (String[] row : rows) {
            assertThat(MoldKind.valueOf(row[0]).defaultCapacity()).as(row[0]).isEqualTo(Integer.parseInt(row[1]));
        }
    }

    @Test
    void fillMatchesTfcFluidComponentFill() {
        List<String[]> rows = rows(MOLD_FILL);
        assertThat(rows).hasSize(MOLD_FILL_ROWS);
        for (String[] r : rows) {
            int capacity = Integer.parseInt(r[0]);
            FluidAmount content = content(r[1], Integer.parseInt(r[2]));
            FluidAmount input = new FluidAmount("a", Integer.parseInt(r[3]));
            boolean canContain = r[4].equals("1");
            MoldRules.FillResult result = MoldRules.fill(content, input, capacity, canContain);
            String row = String.join("|", r);
            assertThat(result.content().amount()).as(row).isEqualTo(Integer.parseInt(r[5]));
            assertThat(result.filled()).as(row).isEqualTo(Integer.parseInt(r[6]));
            assertThat(result.content().amount()).as(row).isEqualTo(content.amount() + result.filled());
        }
    }

    @Test
    void heatCapacityMatchesTfcFloatArithmetic() {
        for (String[] r : rows(MOLD_HEAT_CAP)) {
            float specific = Float.parseFloat(r[0]);
            int amount = Integer.parseInt(r[1]);
            assertThat(MoldRules.heatCapacity(specific, amount)).as(String.join("|", r))
                .isEqualTo(Float.parseFloat(r[2]));
        }
    }

    @Test
    void moltenTestsMatchTfc() {
        for (String[] r : rows(MOLTEN)) {
            float temperature = Float.parseFloat(r[0]);
            assertThat(MoldRules.isMolten(temperature, 1200f)).as(r[0]).isEqualTo(r[1].equals("1"));
            assertThat(VesselRules.isMoltenVessel(true, temperature, 1200f)).as(r[0]).isEqualTo(r[2].equals("1"));
        }
    }

    @Test
    void firingTemperatureMatchesTfcHeatingRecipe() {
        for (String[] r : rows(FIRING)) {
            assertThat(MoldRules.firesAt(Float.parseFloat(r[0]))).as(r[0]).isEqualTo(r[1].equals("1"));
        }
    }

    @Test
    void castBreaksOnlyBelowTheChance() {
        // TFC: player.getRandom().nextFloat() < recipe.getBreakChance(). Copper ingot recipe: break_chance 0.1.
        assertThat(MoldRules.breaks(0.05f, 0.1f)).isTrue();
        assertThat(MoldRules.breaks(0.1f, 0.1f)).isFalse();
        assertThat(MoldRules.breaks(0.5f, 1f)).isTrue();
    }

    private static final int MOLD_FILL_ROWS = 352;
}
