/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/JugItem.java @ c3b4d9536 (drink drain, break draw),
 *       src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (fill, drain),
 *       src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (applyTrait, removeTrait,
 *       calculateNewCreationDate), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (jugCapacity,
 *       jugBreakChance, foodDecayStackTicks, traitPreservedModifier)
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * M3-POT-03 golden values for the ceramic jug at c3b4d9536, generated from the scratch copy of TFC's arithmetic in
 * {@code scratchpad/m3pot3/gen_jug_vessel.py}. Integer mB rows only; the break draw compares a float32 draw with the
 * double {@code jugBreakChance} 0.02, as TFC does.
 */
class JugGoldenTest {
    // from TFC at c3b4d9536: kind|content|input|canContain|filled|resultAmount
    // kind E = empty jug, W = jug holds the input fluid, L = jug holds another fluid. Capacity 100.
    private static final String FILL = """
            E|0|0|1|0|0
            E|0|0|0|0|0
            E|0|1|1|1|1
            E|0|1|0|0|0
            E|0|50|1|50|50
            E|0|50|0|0|0
            E|0|99|1|99|99
            E|0|99|0|0|0
            E|0|100|1|100|100
            E|0|100|0|0|0
            E|0|101|1|100|100
            E|0|101|0|0|0
            E|0|150|1|100|100
            E|0|150|0|0|0
            E|0|3000|1|100|100
            E|0|3000|0|0|0
            W|1|0|1|0|1
            W|1|0|0|0|1
            W|1|1|1|1|2
            W|1|1|0|0|1
            W|1|50|1|50|51
            W|1|50|0|0|1
            W|1|99|1|99|100
            W|1|99|0|0|1
            W|1|100|1|99|100
            W|1|100|0|0|1
            W|1|101|1|99|100
            W|1|101|0|0|1
            W|1|150|1|99|100
            W|1|150|0|0|1
            W|1|3000|1|99|100
            W|1|3000|0|0|1
            W|50|0|1|0|50
            W|50|0|0|0|50
            W|50|1|1|1|51
            W|50|1|0|0|50
            W|50|50|1|50|100
            W|50|50|0|0|50
            W|50|99|1|50|100
            W|50|99|0|0|50
            W|50|100|1|50|100
            W|50|100|0|0|50
            W|50|101|1|50|100
            W|50|101|0|0|50
            W|50|150|1|50|100
            W|50|150|0|0|50
            W|50|3000|1|50|100
            W|50|3000|0|0|50
            W|99|0|1|0|99
            W|99|0|0|0|99
            W|99|1|1|1|100
            W|99|1|0|0|99
            W|99|50|1|1|100
            W|99|50|0|0|99
            W|99|99|1|1|100
            W|99|99|0|0|99
            W|99|100|1|1|100
            W|99|100|0|0|99
            W|99|101|1|1|100
            W|99|101|0|0|99
            W|99|150|1|1|100
            W|99|150|0|0|99
            W|99|3000|1|1|100
            W|99|3000|0|0|99
            W|100|0|1|0|100
            W|100|0|0|0|100
            W|100|1|1|0|100
            W|100|1|0|0|100
            W|100|50|1|0|100
            W|100|50|0|0|100
            W|100|99|1|0|100
            W|100|99|0|0|100
            W|100|100|1|0|100
            W|100|100|0|0|100
            W|100|101|1|0|100
            W|100|101|0|0|100
            W|100|150|1|0|100
            W|100|150|0|0|100
            W|100|3000|1|0|100
            W|100|3000|0|0|100
            L|1|0|1|0|1
            L|1|0|0|0|1
            L|1|1|1|0|1
            L|1|1|0|0|1
            L|1|50|1|0|1
            L|1|50|0|0|1
            L|1|99|1|0|1
            L|1|99|0|0|1
            L|1|100|1|0|1
            L|1|100|0|0|1
            L|1|101|1|0|1
            L|1|101|0|0|1
            L|1|150|1|0|1
            L|1|150|0|0|1
            L|1|3000|1|0|1
            L|1|3000|0|0|1
            L|50|0|1|0|50
            L|50|0|0|0|50
            L|50|1|1|0|50
            L|50|1|0|0|50
            L|50|50|1|0|50
            L|50|50|0|0|50
            L|50|99|1|0|50
            L|50|99|0|0|50
            L|50|100|1|0|50
            L|50|100|0|0|50
            L|50|101|1|0|50
            L|50|101|0|0|50
            L|50|150|1|0|50
            L|50|150|0|0|50
            L|50|3000|1|0|50
            L|50|3000|0|0|50
            L|99|0|1|0|99
            L|99|0|0|0|99
            L|99|1|1|0|99
            L|99|1|0|0|99
            L|99|50|1|0|99
            L|99|50|0|0|99
            L|99|99|1|0|99
            L|99|99|0|0|99
            L|99|100|1|0|99
            L|99|100|0|0|99
            L|99|101|1|0|99
            L|99|101|0|0|99
            L|99|150|1|0|99
            L|99|150|0|0|99
            L|99|3000|1|0|99
            L|99|3000|0|0|99
            L|100|0|1|0|100
            L|100|0|0|0|100
            L|100|1|1|0|100
            L|100|1|0|0|100
            L|100|50|1|0|100
            L|100|50|0|0|100
            L|100|99|1|0|100
            L|100|99|0|0|100
            L|100|100|1|0|100
            L|100|100|0|0|100
            L|100|101|1|0|100
            L|100|101|0|0|100
            L|100|150|1|0|100
            L|100|150|0|0|100
            L|100|3000|1|0|100
            L|100|3000|0|0|100
            """;

    // from TFC at c3b4d9536: content|amount|remaining|drained (amount 2147483647 is Integer.MAX_VALUE, the drink drain)
    private static final String DRAIN = """
            0|0|0|0
            0|1|0|0
            0|49|0|0
            0|50|0|0
            0|51|0|0
            0|99|0|0
            0|100|0|0
            0|101|0|0
            0|2147483647|0|0
            1|0|1|0
            1|1|0|1
            1|49|0|1
            1|50|0|1
            1|51|0|1
            1|99|0|1
            1|100|0|1
            1|101|0|1
            1|2147483647|0|1
            50|0|50|0
            50|1|49|1
            50|49|1|49
            50|50|0|50
            50|51|0|50
            50|99|0|50
            50|100|0|50
            50|101|0|50
            50|2147483647|0|50
            99|0|99|0
            99|1|98|1
            99|49|50|49
            99|50|49|50
            99|51|48|51
            99|99|0|99
            99|100|0|99
            99|101|0|99
            99|2147483647|0|99
            100|0|100|0
            100|1|99|1
            100|49|51|49
            100|50|50|50
            100|51|49|51
            100|99|1|99
            100|100|0|100
            100|101|0|100
            100|2147483647|0|100
            """;

    // from TFC at c3b4d9536: draw|breaks (draw is a float32 nextFloat; breaks when draw < 0.02)
    private static final String BREAKS = """
            0.0|1
            0.0099999|1
            0.0199999|1
            0.02|1
            0.0200001|0
            0.5|0
            0.9999|0
            """;

    @Test
    void capacityIsTheConfigDefault() {
        assertThat(JugRules.CAPACITY).isEqualTo(100);
        assertThat(JugRules.BREAK_CHANCE).isEqualTo(0.02);
    }

    @Test
    void fillMatchesTfcRows() {
        int rows = 0;
        for (String line : FILL.strip().split("\n")) {
            String[] f = line.trim().split("\\|");
            String kind = f[0];
            FluidAmount content = switch (kind) {
                case "E" -> FluidAmount.EMPTY;
                case "W" -> new FluidAmount("water", Integer.parseInt(f[1]));
                default -> new FluidAmount("lava", Integer.parseInt(f[1]));
            };
            int input = Integer.parseInt(f[2]);
            boolean can = f[3].equals("1");
            JugRules.FillResult out = JugRules.fill(content, new FluidAmount("water", input), can);
            assertThat(out.filled()).as("row %s", line).isEqualTo(Integer.parseInt(f[4]));
            assertThat(out.content().amount()).as("row %s", line).isEqualTo(Integer.parseInt(f[5]));
            if (out.content().amount() > 0) {
                assertThat(out.content().fluid()).as("row %s", line).isEqualTo(kind.equals("L") ? "lava" : "water");
            }
            rows++;
        }
        assertThat(rows).isEqualTo(144);
    }

    @Test
    void drainMatchesTfcRows() {
        int rows = 0;
        for (String line : DRAIN.strip().split("\n")) {
            String[] f = line.trim().split("\\|");
            JugRules.DrainResult out = JugRules.drain(new FluidAmount("water", Integer.parseInt(f[0])), Integer.parseInt(f[1]));
            assertThat(out.content().amount()).as("row %s", line).isEqualTo(Integer.parseInt(f[2]));
            assertThat(out.drained().amount()).as("row %s", line).isEqualTo(Integer.parseInt(f[3]));
            rows++;
        }
        assertThat(rows).isEqualTo(45);
    }

    @Test
    void breakMatchesTfcRows() {
        int rows = 0;
        for (String line : BREAKS.strip().split("\n")) {
            String[] f = line.trim().split("\\|");
            assertThat(JugRules.breaksAfterDrink(Float.parseFloat(f[0]))).as("row %s", line).isEqualTo(f[1].equals("1"));
            rows++;
        }
        assertThat(rows).isEqualTo(7);
    }

    @Test
    void drinkTakesTheWholeContent() {
        JugRules.DrainResult out = JugRules.drink(new FluidAmount("water", 73));
        assertThat(out.content()).isEqualTo(FluidAmount.EMPTY);
        assertThat(out.drained().amount()).isEqualTo(73);
    }
}
