/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prospect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden tests against TFC at c3b4d9536. The TSV files are exported by a scratch script from the TFC source and generated data:
 * {@code prospectable.tsv} (the flattened {@code #c:ores} tag with its representatives), {@code rating.tsv} (the thresholds of
 * {@code PropickItem.useOn}), {@code false-negative.tsv} (the chance per tier level, float32 arithmetic), and {@code decision.tsv}
 * (TFC's decision for one representative, with the Java {@code Random} stream emulated).
 */
class ProspectGoldenTest {
    private static final int PROSPECTABLE_ROWS = 1116;
    private static final int POOR_ROWS = 252;
    private static final int NORMAL_ROWS = 252;
    private static final int RICH_ROWS = 252;
    private static final int UNGRADED_ROWS = 360;

    private static List<String[]> rows(String name, int columns) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = ProspectGoldenTest.class.getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].split("\t", -1);
                assertThat(cols).as("%s row %d", name, i).hasSize(columns);
                out.add(cols);
            }
        }
        return out;
    }

    @Test
    void constantsAreAsInTfc() {
        assertThat(ProspectRules.RADIUS).isEqualTo(12);
        assertThat(ProspectRules.COOLDOWN_TICKS).isEqualTo(10);
        assertThat(ProspectRules.DAMAGE_PER_USE).isEqualTo(1);
        assertThat(ProspectRules.SEED_SALT).isEqualTo(19827384739241223L);
    }

    @Test
    void prospectableRowCountMatchesTfcTag() throws IOException {
        assertThat(rows("prospectable.tsv", 5)).hasSize(PROSPECTABLE_ROWS);
    }

    @Test
    void gradeCountsMatchTfc() throws IOException {
        List<String[]> rows = rows("prospectable.tsv", 5);
        assertThat(rows).filteredOn(r -> r[2].equals("poor")).hasSize(POOR_ROWS);
        assertThat(rows).filteredOn(r -> r[2].equals("normal")).hasSize(NORMAL_ROWS);
        assertThat(rows).filteredOn(r -> r[2].equals("rich")).hasSize(RICH_ROWS);
        assertThat(rows).filteredOn(r -> r[2].equals("ungraded")).hasSize(UNGRADED_ROWS);
    }

    @Test
    void representativeOfEveryRowMatchesTfc() throws IOException {
        for (String[] row : rows("prospectable.tsv", 5)) {
            assertThat(ProspectRules.representative(row[0])).as(row[0]).isEqualTo(row[1]);
        }
    }

    @Test
    void everyRepresentativeIsAProspectableBlock() throws IOException {
        List<String[]> rows = rows("prospectable.tsv", 5);
        Set<String> blocks = new HashSet<>();
        rows.forEach(r -> blocks.add(r[0]));
        for (String[] row : rows) {
            assertThat(blocks).as(row[1]).contains(row[1]);
        }
    }

    @Test
    void ratingThresholdsMatchTfcForEveryRow() throws IOException {
        List<String[]> rows = rows("rating.tsv", 2);
        assertThat(rows).hasSize(94);
        for (String[] row : rows) {
            int amount = Integer.parseInt(row[0]);
            assertThat(ProspectRules.rate(amount)).as("amount %d", amount).isEqualTo(ProspectResult.valueOf(row[1]));
        }
    }

    @Test
    void falseNegativeChancesMatchTfcInFloat32() throws IOException {
        List<String[]> rows = rows("false-negative.tsv", 2);
        assertThat(rows).hasSize(8);
        for (String[] row : rows) {
            int level = Integer.parseInt(row[0]);
            float expected = Float.parseFloat(row[1]);
            assertThat(Float.floatToIntBits(ProspectRules.falseNegativeChance(level)))
                .as("level %d", level)
                .isEqualTo(Float.floatToIntBits(expected));
        }
    }

    @Test
    void decisionForEveryRowMatchesTfc() throws IOException {
        List<String[]> rows = rows("decision.tsv", 7);
        assertThat(rows).hasSize(306);
        for (String[] row : rows) {
            int x = Integer.parseInt(row[0]);
            int y = Integer.parseInt(row[1]);
            int z = Integer.parseInt(row[2]);
            int level = Integer.parseInt(row[3]);
            int amount = Integer.parseInt(row[4]);
            boolean clicked = row[5].equals("1");
            List<ScanFact> cube = new ArrayList<>();
            for (int i = 0; i < amount; i++) {
                cube.add(new ScanFact(i % 25 - 12, (i / 25) % 25 - 12, (i / 625) % 25 - 12, "tfc:ore/poor_copper/granite"));
            }
            ProspectRequest request = new ProspectRequest(x, y, z, "tfc:stone/granite", clicked, level, cube);
            ProspectOutcome outcome = ProspectRules.decide(request);
            assertThat(outcome.result().name()).as("row %s", String.join(",", row)).isEqualTo(row[6]);
        }
    }
}
