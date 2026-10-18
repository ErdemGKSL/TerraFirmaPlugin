/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (consumeFuelForTicks)
 */
package dev.tfp.core.fuel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-02 golden values: {@code HeatCapability.consumeFuelForTicks} at c3b4d9536, run on TFC's fuel durations and
 * temperatures. Row: {@code burnTicks|queue|burnTemperature|ticks|after burnTicks|after burnTemperature|after ticks|consumed}.
 * The queue is fuel ids in slot order, {@code -} for none. Produced by a scratch copy of TFC's loop.
 */
class FuelBurnGoldenTest {
    // from TFC at c3b4d9536
    private static final String GOLDEN = """
            # burnTicks|queue|burnTemperature|ticks|after burnTicks|after burnTemperature|after ticks|consumed
            0|oak_logs,coal|0.0|0|2250|728.0|0|1
            0|oak_logs,coal|0.0|1|2249|728.0|0|1
            0|oak_logs,coal|0.0|499|1751|728.0|0|1
            0|oak_logs,coal|0.0|500|1750|728.0|0|1
            0|oak_logs,coal|0.0|2250|2000|1415.0|0|2
            0|oak_logs,coal|0.0|2251|1999|1415.0|0|2
            0|oak_logs,coal|0.0|4250|0|0.0|0|2
            0|oak_logs,coal|0.0|4251|0|0.0|1|2
            0|oak_logs,coal|0.0|9000|0|0.0|4750|2
            500|oak_logs,coal|1200.0|0|500|1200.0|0|0
            500|oak_logs,coal|1200.0|1|499|1200.0|0|0
            500|oak_logs,coal|1200.0|499|1|1200.0|0|0
            500|oak_logs,coal|1200.0|500|2250|728.0|0|1
            500|oak_logs,coal|1200.0|2250|500|728.0|0|1
            500|oak_logs,coal|1200.0|2251|499|728.0|0|1
            500|oak_logs,coal|1200.0|4250|500|1415.0|0|2
            500|oak_logs,coal|1200.0|4251|499|1415.0|0|2
            500|oak_logs,coal|1200.0|9000|0|1200.0|4250|2
            3000|oak_logs,coal|700.0|0|3000|700.0|0|0
            3000|oak_logs,coal|700.0|1|2999|700.0|0|0
            3000|oak_logs,coal|700.0|499|2501|700.0|0|0
            3000|oak_logs,coal|700.0|500|2500|700.0|0|0
            3000|oak_logs,coal|700.0|2250|750|700.0|0|0
            3000|oak_logs,coal|700.0|2251|749|700.0|0|0
            3000|oak_logs,coal|700.0|4250|1000|728.0|0|1
            3000|oak_logs,coal|700.0|4251|999|728.0|0|1
            3000|oak_logs,coal|700.0|9000|0|700.0|1750|2
            0|groundcover/pinecone,leaves,stick_bundle|0.0|0|220|150.0|0|1
            0|groundcover/pinecone,leaves,stick_bundle|0.0|1|219|150.0|0|1
            0|groundcover/pinecone,leaves,stick_bundle|0.0|499|321|100.0|0|2
            0|groundcover/pinecone,leaves,stick_bundle|0.0|500|320|100.0|0|2
            0|groundcover/pinecone,leaves,stick_bundle|0.0|2250|0|0.0|830|3
            0|groundcover/pinecone,leaves,stick_bundle|0.0|2251|0|0.0|831|3
            0|groundcover/pinecone,leaves,stick_bundle|0.0|4250|0|0.0|2830|3
            0|groundcover/pinecone,leaves,stick_bundle|0.0|4251|0|0.0|2831|3
            0|groundcover/pinecone,leaves,stick_bundle|0.0|9000|0|0.0|7580|3
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|0|500|1200.0|0|0
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|1|499|1200.0|0|0
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|499|1|1200.0|0|0
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|500|220|150.0|0|1
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|2250|0|1200.0|330|3
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|2251|0|1200.0|331|3
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|4250|0|1200.0|2330|3
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|4251|0|1200.0|2331|3
            500|groundcover/pinecone,leaves,stick_bundle|1200.0|9000|0|1200.0|7080|3
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|0|3000|700.0|0|0
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|1|2999|700.0|0|0
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|499|2501|700.0|0|0
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|500|2500|700.0|0|0
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|2250|750|700.0|0|0
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|2251|749|700.0|0|0
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|4250|170|900.0|0|3
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|4251|169|900.0|0|3
            3000|groundcover/pinecone,leaves,stick_bundle|700.0|9000|0|700.0|4580|3
            0|maple_planks,charcoal,peat|0.0|0|800|795.0|0|1
            0|maple_planks,charcoal,peat|0.0|1|799|795.0|0|1
            0|maple_planks,charcoal,peat|0.0|499|301|795.0|0|1
            0|maple_planks,charcoal,peat|0.0|500|300|795.0|0|1
            0|maple_planks,charcoal,peat|0.0|2250|350|1350.0|0|2
            0|maple_planks,charcoal,peat|0.0|2251|349|1350.0|0|2
            0|maple_planks,charcoal,peat|0.0|4250|850|600.0|0|3
            0|maple_planks,charcoal,peat|0.0|4251|849|600.0|0|3
            0|maple_planks,charcoal,peat|0.0|9000|0|0.0|3900|3
            500|maple_planks,charcoal,peat|1200.0|0|500|1200.0|0|0
            500|maple_planks,charcoal,peat|1200.0|1|499|1200.0|0|0
            500|maple_planks,charcoal,peat|1200.0|499|1|1200.0|0|0
            500|maple_planks,charcoal,peat|1200.0|500|800|795.0|0|1
            500|maple_planks,charcoal,peat|1200.0|2250|850|1350.0|0|2
            500|maple_planks,charcoal,peat|1200.0|2251|849|1350.0|0|2
            500|maple_planks,charcoal,peat|1200.0|4250|1350|600.0|0|3
            500|maple_planks,charcoal,peat|1200.0|4251|1349|600.0|0|3
            500|maple_planks,charcoal,peat|1200.0|9000|0|1200.0|3400|3
            3000|maple_planks,charcoal,peat|700.0|0|3000|700.0|0|0
            3000|maple_planks,charcoal,peat|700.0|1|2999|700.0|0|0
            3000|maple_planks,charcoal,peat|700.0|499|2501|700.0|0|0
            3000|maple_planks,charcoal,peat|700.0|500|2500|700.0|0|0
            3000|maple_planks,charcoal,peat|700.0|2250|750|700.0|0|0
            3000|maple_planks,charcoal,peat|700.0|2251|749|700.0|0|0
            3000|maple_planks,charcoal,peat|700.0|4250|1350|1350.0|0|2
            3000|maple_planks,charcoal,peat|700.0|4251|1349|1350.0|0|2
            3000|maple_planks,charcoal,peat|700.0|9000|0|700.0|900|3
            0|-|0.0|0|0|0.0|0|0
            0|-|0.0|1|0|0.0|1|0
            0|-|0.0|499|0|0.0|499|0
            0|-|0.0|500|0|0.0|500|0
            0|-|0.0|2250|0|0.0|2250|0
            0|-|0.0|2251|0|0.0|2251|0
            0|-|0.0|4250|0|0.0|4250|0
            0|-|0.0|4251|0|0.0|4251|0
            0|-|0.0|9000|0|0.0|9000|0
            500|-|1200.0|0|500|1200.0|0|0
            500|-|1200.0|1|499|1200.0|0|0
            500|-|1200.0|499|1|1200.0|0|0
            500|-|1200.0|500|0|1200.0|0|0
            500|-|1200.0|2250|0|1200.0|1750|0
            500|-|1200.0|2251|0|1200.0|1751|0
            500|-|1200.0|4250|0|1200.0|3750|0
            500|-|1200.0|4251|0|1200.0|3751|0
            500|-|1200.0|9000|0|1200.0|8500|0
            3000|-|700.0|0|3000|700.0|0|0
            3000|-|700.0|1|2999|700.0|0|0
            3000|-|700.0|499|2501|700.0|0|0
            3000|-|700.0|500|2500|700.0|0|0
            3000|-|700.0|2250|750|700.0|0|0
            3000|-|700.0|2251|749|700.0|0|0
            3000|-|700.0|4250|0|700.0|1250|0
            3000|-|700.0|4251|0|700.0|1251|0
            3000|-|700.0|9000|0|700.0|6000|0
            """;

    @Test
    void consumeMatchesTfcRows() {
        String[] rows = GOLDEN.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toArray(String[]::new);
        assertThat(rows).hasSize(108);
        for (String row : rows) {
            String[] c = row.split("\\|");
            List<Fuel> queue = new ArrayList<>();
            if (!c[1].equals("-")) {
                for (String id : c[1].split(",")) {
                    queue.add(FuelTable.byId(id).orElseThrow());
                }
            }
            FuelBurn.Remainder r = FuelBurn.consume(Long.parseLong(c[3]), Integer.parseInt(c[0]), Float.parseFloat(c[2]), queue);
            assertThat(r.burnTicks()).as(row).isEqualTo(Integer.parseInt(c[4]));
            assertThat(r.burnTemperature()).as(row).isEqualTo(Float.parseFloat(c[5]));
            assertThat(r.ticks()).as(row).isEqualTo(Long.parseLong(c[6]));
            assertThat(r.consumed()).as(row).isEqualTo(Integer.parseInt(c[7]));
        }
    }

    @Test
    void stackOfOneFuelBurnsItsDurationPerItem() {
        Fuel oak = FuelTable.byId("oak_logs").orElseThrow();
        assertThat(FuelBurn.stackDuration(oak, 3)).isEqualTo(3L * 2250);
        assertThat(FuelBurn.stackDuration(oak, 0)).isZero();
        assertThatThrownBy(() -> FuelBurn.stackDuration(oak, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeTicksAreRejected() {
        assertThatThrownBy(() -> FuelBurn.consume(-1, 0, 0f, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
