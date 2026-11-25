/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tfc/lamp_fuel/*.json @ c3b4d9536 (exported to lamp_fuel.tsv)
 */
package dev.tfp.core.lamp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden test against {@code lamp_fuel.tsv}, exported from TFC's {@code data/tfc/tfc/lamp_fuel/} at c3b4d9536: the fuel
 * table of {@link LampFuel#TFC} is TFC's four files, and each fuel works in the lamps the file names ({@code #tfc:lamps} is
 * the nine lamp metals, lava only the red and blue steel lamps).
 */
class LampFuelTsvGoldenTest {
    private static final List<String> LAMPS = List.of("bismuth_bronze", "black_bronze", "black_steel", "blue_steel", "bronze",
        "copper", "red_steel", "steel", "wrought_iron");

    record Row(String id, String fluid, int burnRate, String lamps) {
    }

    private static List<Row> rows() {
        try (InputStream in = LampFuelTsvGoldenTest.class.getResourceAsStream("lamp_fuel.tsv")) {
            List<Row> rows = new ArrayList<>();
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] c = lines[i].split("\t");
                rows.add(new Row(c[0], c[1], Integer.parseInt(c[2]), c[3]));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theTableIsTfcsFourFuelsInFileOrder() {
        List<Row> rows = rows();
        assertThat(rows).hasSize(4);
        assertThat(LampFuel.TFC).hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            LampFuel f = LampFuel.TFC.get(i);
            Row r = rows.get(i);
            assertThat(f.fluid()).isEqualTo(r.fluid().replace("tfc:", "tfp:"));
            assertThat(f.burnRate()).as(r.id()).isEqualTo(r.burnRate());
        }
    }

    @Test
    void everyFuelWorksInTheLampsOfItsRow() {
        for (Row r : rows()) {
            String fluid = r.fluid().replace("tfc:", "tfp:");
            Set<String> expected = r.lamps().startsWith("#")
                ? Set.copyOf(LAMPS)
                : Set.of(r.lamps().split(",")).stream().map(s -> s.substring("tfc:metal/lamp/".length())).collect(java.util.stream.Collectors.toSet());
            for (String metal : LAMPS) {
                assertThat(LampFuel.get(fluid, metal).isPresent()).as(r.id() + " in " + metal).isEqualTo(expected.contains(metal));
            }
        }
    }

    @Test
    void onlyLavaIsEndless() {
        assertThat(LampFuel.TFC.stream().filter(LampFuel::endless).map(LampFuel::fluid)).containsExactly("minecraft:lava");
        assertThat(LampFuel.get("tfp:water", "copper")).isEmpty();
    }
}
