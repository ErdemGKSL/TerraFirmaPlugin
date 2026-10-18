/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * M3-GATH-02 golden values: the yields of TFC's plant, leaf, twig, fallen-leaf and groundcover-stick loot tables at c3b4d9536.
 *
 * <p>The expected values are in {@code harvest-golden.txt}, which a scratch oracle wrote from the raw TFC JSON, not from
 * {@link HarvestTables}. Each row is one block and one block state; its 12 cells are the scenarios in this order: for each tool
 * state NONE, SHARP, SHEAR, AXE, SILK_TOUCH, SHARP+SHEAR, the roll 0.0 (passes every chance, takes the minimum count) and then 0.999
 * (fails every chance, takes the maximum count).
 */
class HarvestGoldenTest {

    // from TFC at c3b4d9536: 277 loot tables (216 plants, 20 leaves, 20 twigs, 20 fallen leaves, the groundcover stick),
    // 542 (block, state) rows, 6504 scenario cells
    private static final int EXPECTED_TABLES = 277;
    private static final int EXPECTED_ROWS = 542;
    private static final int EXPECTED_CELLS = 6504;
    private static final int CELLS_PER_ROW = 12;

    private static final List<HeldTool> TOOL_STATES = List.of(
            HeldTool.NONE,
            HeldTool.of(ToolTag.SHARP),
            HeldTool.of(ToolTag.SHEAR),
            HeldTool.of(ToolTag.AXE),
            HeldTool.of(ToolTag.SILK_TOUCH),
            HeldTool.of(ToolTag.SHARP, ToolTag.SHEAR));
    private static final double[] ROLLS = {0.0, 0.999};

    private static List<String> rows() throws IOException {
        try (InputStream in = HarvestGoldenTest.class.getResourceAsStream("/dev/tfp/core/gather/harvest-golden.txt")) {
            assertThat(in).as("harvest-golden.txt on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        }
    }

    private static Map<String, String> stateOf(String text) {
        if (text.equals("-")) {
            return Map.of();
        }
        Map<String, String> state = new HashMap<>();
        for (String pair : text.split(",")) {
            int eq = pair.indexOf('=');
            state.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return state;
    }

    private static String format(List<GatherYield> yields) {
        if (yields.isEmpty()) {
            return "-";
        }
        return yields.stream().map(y -> y.itemId() + "*" + y.count()).collect(Collectors.joining(";"));
    }

    @Test
    void goldenFileCoversEveryTable() throws IOException {
        List<String> rows = rows();
        assertThat(rows).hasSize(EXPECTED_ROWS);
        Set<String> blocks = rows.stream().map(row -> row.split(" ", 2)[0]).collect(Collectors.toSet());
        assertThat(blocks).hasSize(EXPECTED_TABLES);
        assertThat(HarvestTables.all()).hasSize(EXPECTED_TABLES);
        assertThat(HarvestTables.all().stream().map(HarvestTable::blockId).toList())
                .containsExactlyInAnyOrderElementsOf(blocks);
    }

    @Test
    void everyCellMatchesTheTfcOracle() throws IOException {
        int cells = 0;
        for (String row : rows()) {
            String[] head = row.split(" \\| ", 2);
            String[] key = head[0].split(" ", 2);
            HarvestTable table = HarvestRules.table(key[0]);
            Map<String, String> state = stateOf(key[1]);
            String[] expected = head[1].split(" ");
            assertThat(expected).as(row).hasSize(CELLS_PER_ROW);
            int cell = 0;
            for (HeldTool tool : TOOL_STATES) {
                for (double u : ROLLS) {
                    String actual = format(HarvestRules.drops(table, tool, state, () -> u));
                    assertThat(actual).as(row + " tool " + tool.tags() + " roll " + u).isEqualTo(expected[cell]);
                    cell++;
                    cells++;
                }
            }
        }
        assertThat(cells).isEqualTo(EXPECTED_CELLS);
    }
}
