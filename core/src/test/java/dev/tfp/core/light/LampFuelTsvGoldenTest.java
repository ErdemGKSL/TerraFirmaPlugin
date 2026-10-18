/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tfc/lamp_fuel/*.json @ c3b4d9536 (fluid, burn_rate),
 *       src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut)
 */
package dev.tfp.core.light;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden test for the lamp fuel burn time against {@code lamp-fuel.tsv}, which is exported from TFC's {@code lamp_fuel/}
 * data at c3b4d9536 (4 fuels, 1 mB and the 250 mB default capacity: 8 rows). Each row is {@code fluid, lamps, burn_rate, mb,
 * duration_ticks}. {@code never} is a burn rate of -1 (lava). The burn time is {@link LampRules#durationTicks}, and a lit
 * lamp checked at exactly that time is empty and out, and one tick earlier still holds one mB, as TFC's
 * {@code floor(ticks / burnRate)} drain gives.
 */
class LampFuelTsvGoldenTest {
    private static final String RESOURCE = "lamp-fuel.tsv";
    private static final int EXPECTED_ROWS = 8;
    private static final Set<String> FUELS = Set.of("tfc:canola_oil", "tfc:olive_oil", "tfc:tallow", "minecraft:lava");

    @Test
    void burnTimeMatchesTfcGoldenRows() {
        List<Map<String, String>> rows = rows();
        assertThat(rows).hasSize(EXPECTED_ROWS);
        assertThat(rows.stream().map(r -> r.get("fluid")).distinct().toList()).hasSize(4);
        for (Map<String, String> row : rows) {
            LampRules.Fuel fuel = fuelFor(row.get("fluid"));
            int mb = Integer.parseInt(row.get("mb"));
            assertThat(fuel.burnRate()).as("burn rate of %s", row).isEqualTo(Integer.parseInt(row.get("burn_rate")));
            String expected = row.get("duration_ticks");
            long duration = LampRules.durationTicks(fuel.burnRate(), mb);
            if ("never".equals(expected)) {
                assertThat(duration).as("duration of %s", row).isEqualTo(LightSourceRules.NEVER);
                continue;
            }
            assertThat(duration).as("duration of %s", row).isEqualTo(Long.parseLong(expected));
            // the lamp is empty and out exactly at the duration, and still lit with one mB one tick earlier
            LampRules.Check empty = LampRules.check(duration, fuel.burnRate(), mb, true);
            assertThat(empty.drained()).as("drained at duration, %s", row).isEqualTo(mb);
            assertThat(empty.tankAfter()).as("tank at duration, %s", row).isZero();
            assertThat(empty.lit()).as("lit at duration, %s", row).isFalse();
            LampRules.Check almost = LampRules.check(duration - 1, fuel.burnRate(), mb, true);
            assertThat(almost.tankAfter()).as("tank one tick early, %s", row).isEqualTo(1);
            assertThat(almost.lit()).as("lit one tick early, %s", row).isTrue();
        }
    }

    @Test
    void fuelsMatchTheirTsvBurnRates() {
        Map<String, Integer> rates = new HashMap<>();
        for (Map<String, String> row : rows()) {
            rates.put(row.get("fluid"), Integer.parseInt(row.get("burn_rate")));
        }
        assertThat(rates).containsOnlyKeys(FUELS);
        assertThat(LampRules.CANOLA_OIL.burnRate()).isEqualTo(rates.get("tfc:canola_oil"));
        assertThat(LampRules.OLIVE_OIL.burnRate()).isEqualTo(rates.get("tfc:olive_oil"));
        assertThat(LampRules.TALLOW.burnRate()).isEqualTo(rates.get("tfc:tallow"));
        assertThat(LampRules.LAVA.burnRate()).isEqualTo(rates.get("minecraft:lava"));
    }

    private static LampRules.Fuel fuelFor(String fluid) {
        return switch (fluid) {
            case "tfc:canola_oil" -> LampRules.CANOLA_OIL;
            case "tfc:olive_oil" -> LampRules.OLIVE_OIL;
            case "tfc:tallow" -> LampRules.TALLOW;
            case "minecraft:lava" -> LampRules.LAVA;
            default -> throw new AssertionError("unknown fuel " + fluid);
        };
    }

    private static List<Map<String, String>> rows() {
        List<String> lines;
        try (InputStream in = LampFuelTsvGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("%s on the test classpath", RESOURCE).isNotNull();
            lines = new ArrayList<>(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<String> header = List.of(lines.get(0).split("\t"));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split("\t", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), cells[i]);
            }
            rows.add(row);
        }
        return rows;
    }
}
