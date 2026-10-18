/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick, consumeFuel, extinguish)
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-03 golden values: the firepit's lazy rule against TFC's {@code serverTick} burn and temperature part at
 * c3b4d9536, with TFC's default device settings (deviceHeatingModifier 1). Each row is one window of calendar ticks.
 * Burn ticks, consumed fuel and the burn temperature are exact. The device temperature is compared within 1e-3, the
 * closed form may differ from the per-tick float sums by rounding.
 */
class FirepitGoldenTest {
    private static final Map<String, List<Fuel>> QUEUES = Map.of(
        "-", List.of(),
        "oak", List.of(fuel("oak_logs")),
        "oak_coal", List.of(fuel("oak_logs"), fuel("coal")),
        "pine_leaves_stick", List.of(fuel("groundcover/pinecone"), fuel("leaves"), fuel("stick_bundle")));

    @Test
    void lazyMatchesTfcFirepitGolden() throws IOException {
        List<String[]> rows = rows("firepit.txt");
        assertThat(rows).hasSize(392);
        for (String[] r : rows) {
            FirepitState start = new FirepitState(Integer.parseInt(r[0]), Float.parseFloat(r[1]), Float.parseFloat(r[2]), Integer.parseInt(r[3]));
            boolean raining = r[4].equals("1");
            List<Fuel> queue = QUEUES.get(r[5]);
            FirepitRules.Advance out = FirepitRules.advance(start, Long.parseLong(r[6]), raining, queue, FirepitRules.DeviceConfig.DEFAULT);
            String row = String.join("|", r);
            assertThat(out.state().burnTicks()).as(row).isEqualTo(Integer.parseInt(r[7]));
            assertThat(out.state().burnTemperature()).as(row).isEqualTo(Float.parseFloat(r[8]));
            assertThat(out.state().temperature()).as(row).isCloseTo(Float.parseFloat(r[9]), within(1e-3f));
            assertThat(out.state().airTicks()).as(row).isEqualTo(Integer.parseInt(r[10]));
            assertThat(out.consumed()).as(row).isEqualTo(Integer.parseInt(r[11]));
        }
    }

    static Fuel fuel(String id) {
        return FuelTable.byId(id).orElseThrow();
    }

    static List<String[]> rows(String name) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = FirepitGoldenTest.class.getResourceAsStream("/dev/tfp/core/cooking/" + name)) {
            assertThat(in).as(name).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line.split("\\|", -1));
                }
            }
        }
        return out;
    }
}
