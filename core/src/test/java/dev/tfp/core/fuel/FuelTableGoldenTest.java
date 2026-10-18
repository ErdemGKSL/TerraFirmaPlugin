/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tfc/fuel/*.json @ c3b4d9536 (every file's duration, temperature and purity)
 */
package dev.tfp.core.fuel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-02 golden values: TFC's 50 fuel data files at c3b4d9536, one row per file as {@code id|duration|temperature|purity}.
 * The rows were read from the pinned data by a scratch script; the temperatures and durations are exact, the purity is
 * the float32 value that TFC's {@code float} field holds.
 */
class FuelTableGoldenTest {
    // from TFC at c3b4d9536 (src/generated/resources/data/tfc/tfc/fuel)
    private static final String GOLDEN = """
            # id|duration|temperature|purity (TFC fuel/*.json at c3b4d9536)
            acacia_logs|1000|650.0|0.949999988079071
            acacia_planks|400|700.0|0.949999988079071
            ash_logs|1250|696.0|0.949999988079071
            ash_planks|500|746.0|0.949999988079071
            aspen_logs|1000|611.0|0.949999988079071
            aspen_planks|400|661.0|0.949999988079071
            birch_logs|1750|652.0|0.949999988079071
            birch_planks|700|702.0|0.949999988079071
            blackwood_logs|1750|720.0|0.949999988079071
            blackwood_planks|700|770.0|0.949999988079071
            charcoal|1800|1350.0|1.0
            chestnut_logs|1500|651.0|0.949999988079071
            chestnut_planks|600|701.0|0.949999988079071
            coal|2000|1415.0|1.0
            douglas_fir_logs|1500|707.0|1.0
            douglas_fir_planks|600|757.0|1.0
            dried_cactus_wood|603|1000.0|0.949999988079071
            groundcover/driftwood|400|650.0|0.4000000059604645
            groundcover/pinecone|220|150.0|0.15000000596046448
            hickory_logs|2000|762.0|0.800000011920929
            hickory_planks|800|812.0|0.800000011920929
            kapok_logs|1000|645.0|0.949999988079071
            kapok_planks|400|695.0|0.949999988079071
            leaves|600|100.0|0.25
            mangrove_logs|1000|655.0|0.949999988079071
            mangrove_planks|400|705.0|0.949999988079071
            maple_logs|2000|745.0|0.949999988079071
            maple_planks|800|795.0|0.949999988079071
            oak_logs|2250|728.0|1.0
            oak_planks|900|778.0|1.0
            ore/lignite|2200|1350.0|1.0
            palm_logs|1250|730.0|0.949999988079071
            palm_planks|500|780.0|0.949999988079071
            paper|150|199.0|0.699999988079071
            peat|2500|600.0|1.0
            pine_logs|1250|627.0|0.6000000238418579
            pine_planks|500|677.0|0.6000000238418579
            rosewood_logs|1500|640.0|0.949999988079071
            rosewood_planks|600|690.0|0.949999988079071
            sequoia_logs|1750|612.0|0.699999988079071
            sequoia_planks|700|662.0|0.699999988079071
            spruce_logs|1500|608.0|0.6000000238418579
            spruce_planks|600|658.0|0.6000000238418579
            stick_bundle|600|900.0|0.800000011920929
            sycamore_logs|1750|653.0|0.949999988079071
            sycamore_planks|700|703.0|0.949999988079071
            white_cedar_logs|1500|625.0|0.949999988079071
            white_cedar_planks|600|675.0|0.949999988079071
            willow_logs|1000|603.0|0.949999988079071
            willow_planks|400|653.0|0.949999988079071
            """;

    @Test
    void everyFuelFileMatchesTfc() {
        String[] rows = GOLDEN.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toArray(String[]::new);
        assertThat(rows).hasSize(50);
        assertThat(FuelTable.ALL).hasSize(rows.length);
        for (int i = 0; i < rows.length; i++) {
            String[] cols = rows[i].split("\\|");
            Fuel fuel = FuelTable.ALL.get(i);
            assertThat(fuel.id()).as(cols[0]).isEqualTo(cols[0]);
            assertThat(fuel.duration()).as(cols[0]).isEqualTo(Integer.parseInt(cols[1]));
            assertThat(fuel.temperature()).as(cols[0]).isEqualTo(Float.parseFloat(cols[2]));
            assertThat(fuel.purity()).as(cols[0]).isEqualTo(Float.parseFloat(cols[3]));
        }
    }

    @Test
    void lookupByIdFindsTfcEntries() {
        assertThat(FuelTable.byId("coal")).contains(new Fuel("coal", 2000, 1415f, 1f));
        assertThat(FuelTable.byId("groundcover/pinecone")).contains(new Fuel("groundcover/pinecone", 220, 150f, 0.15f));
        assertThat(FuelTable.byId("nope")).isEqualTo(Optional.empty());
    }

    @Test
    void recordRejectsImpossibleValues() {
        assertThatThrownBy(() -> new Fuel("x", -1, 1f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fuel("x", 1, -1f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fuel("x", 1, 1f, 1.5f)).isInstanceOf(IllegalArgumentException.class);
    }
}
