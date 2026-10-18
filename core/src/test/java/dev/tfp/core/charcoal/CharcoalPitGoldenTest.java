/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (serverTick, createCharcoal,
 *       getCharcoalAmount),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (getTicksSinceUpdate)
 */
package dev.tfp.core.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-PROC-04 golden values from TFC at c3b4d9536, in {@code charcoal-golden.txt}. The file is produced by a scratch oracle
 * that copies TFC's expressions literally: {@code getCharcoalAmount} in double arithmetic over float32 rolls, the serverTick
 * test stepped one calendar tick at a time, and the {@code createCharcoal} walk. Three sections:
 * {@code amount} rows {@code logs|rolls|amounts}, {@code time} rows {@code lit|charcoalTicks|now|converted|convertTick}, and
 * {@code column} rows {@code piles|rolls|layersBelow|blocked|converted|cells|layersBelowAfter|lost}.
 */
class CharcoalPitGoldenTest {
    private static final String RESOURCE = "/dev/tfp/core/charcoal/charcoal-golden.txt";

    @Test
    void charcoalAmountMatchesTfcOver16LogCountsAndEightRolls() throws IOException {
        List<String[]> rows = section("amount");
        assertThat(rows).hasSize(16);
        int checks = 0;
        for (String[] row : rows) {
            int logs = Integer.parseInt(row[0]);
            String[] rolls = row[1].split(",");
            String[] expected = row[2].split(",");
            assertThat(rolls).hasSameSizeAs(expected);
            for (int i = 0; i < rolls.length; i++) {
                float roll = Float.parseFloat(rolls[i]);
                assertThat(CharcoalPit.charcoalFromLogs(logs, roll))
                    .as("logs %d roll %s", logs, rolls[i])
                    .isEqualTo(Integer.parseInt(expected[i]));
                checks++;
            }
        }
        assertThat(checks).isEqualTo(16 * 8);
    }

    @Test
    void conversionTickMatchesTfcOverTimesAndConfigs() throws IOException {
        List<String[]> rows = section("time");
        assertThat(rows).hasSize(226);
        for (String[] row : rows) {
            long lit = Long.parseLong(row[0]);
            int charcoalTicks = Integer.parseInt(row[1]);
            long now = Long.parseLong(row[2]);
            boolean converted = "1".equals(row[3]);
            long convert = Long.parseLong(row[4]);
            assertThat(CharcoalPit.hasConverted(now, lit, charcoalTicks))
                .as("lit %d cc %d now %d", lit, charcoalTicks, now)
                .isEqualTo(converted);
            assertThat(CharcoalPit.convertTick(lit, charcoalTicks))
                .as("lit %d cc %d", lit, charcoalTicks)
                .isEqualTo(convert);
        }
    }

    @Test
    void columnConversionMatchesTfcCreateCharcoal() throws IOException {
        List<String[]> rows = section("column");
        assertThat(rows).hasSize(300);
        int converted = 0;
        for (String[] row : rows) {
            List<Integer> piles = parseInts(row[0]);
            float[] rolls = parseFloats(row[1]);
            int below = Integer.parseInt(row[2]);
            boolean blocked = "1".equals(row[3]);
            int[] cursor = {0};
            CharcoalPit.Conversion result = CharcoalPit.convert(piles,
                () -> rolls[cursor[0]++], blocked, below);
            assertThat(result.converted()).as("row %s", String.join("|", row)).isEqualTo("1".equals(row[4]));
            assertThat(result.layers()).as("cells of %s", String.join("|", row)).isEqualTo(parseCells(row[5]));
            assertThat(result.layersBelow()).as("below of %s", String.join("|", row)).isEqualTo(Integer.parseInt(row[6]));
            assertThat(result.lost()).as("lost of %s", String.join("|", row)).isEqualTo(Integer.parseInt(row[7]));
            if (result.converted()) {
                converted++;
            }
        }
        assertThat(converted).isGreaterThan(200);
    }

    private static List<Integer> parseInts(String field) {
        List<Integer> out = new ArrayList<>();
        for (String part : field.split(",")) {
            out.add(Integer.parseInt(part));
        }
        return out;
    }

    private static float[] parseFloats(String field) {
        String[] parts = field.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i]);
        }
        return out;
    }

    private static List<Integer> parseCells(String field) {
        return "-".equals(field) ? List.of() : parseInts(field);
    }

    /** The rows of one section, split on {@code |}. Lines starting with {@code #} are comments or section headers. */
    private static List<String[]> section(String name) throws IOException {
        List<String[]> out = new ArrayList<>();
        boolean in = false;
        try (InputStream stream = CharcoalPitGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(stream).as(RESOURCE).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("####")) {
                    in = line.trim().equals("#### " + name);
                } else if (in && !line.isBlank() && !line.startsWith("#")) {
                    out.add(line.split("\\|", -1));
                }
            }
        }
        return out;
    }
}
