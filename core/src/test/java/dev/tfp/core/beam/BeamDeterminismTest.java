/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.beam;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M3-PHYS-03 determinism: the same inputs give the same decision, whatever the view, insertion order or call order. */
class BeamDeterminismTest {

    /** The cross of cells around the origin used by the golden scenarios, in the same layout as the golden input strings. */
    private static Map<BlockPos, BeamCell> cross(String input) {
        Map<BlockPos, BeamCell> cells = new LinkedHashMap<>();
        cells.put(BeamGoldenTest.ORIGIN, BeamGoldenTest.cellOf(input.charAt(0)));
        Direction[] order = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int di = 0; di < 6; di++) {
            Direction d = order[di];
            for (int k = 1; k <= 5; k++) {
                cells.put(BeamGoldenTest.ORIGIN.offset(d.dx() * k, d.dy() * k, d.dz() * k), BeamGoldenTest.cellOf(input.charAt(1 + di * 5 + k - 1)));
            }
        }
        return cells;
    }

    @Test
    void repeatedCallsAndInsertionOrderGiveTheSameDecisions() {
        for (String row : BeamGoldenTest.SCENARIOS) {
            String input = row.split(" ")[0];
            Map<BlockPos, BeamCell> forward = cross(input);
            List<Map.Entry<BlockPos, BeamCell>> entries = new ArrayList<>(forward.entrySet());
            Collections.reverse(entries);
            Map<BlockPos, BeamCell> reversed = new HashMap<>();
            for (Map.Entry<BlockPos, BeamCell> e : entries) {
                reversed.put(e.getKey(), e.getValue());
            }
            BeamView a = BeamGoldenTest.view(forward);
            BeamView b = BeamGoldenTest.view(reversed);
            BeamShape shape = BeamGoldenTest.cellOf(input.charAt(0)).shape();

            assertThat(BeamRules.canSurvive(shape, a, BeamGoldenTest.ORIGIN)).isEqualTo(BeamRules.canSurvive(shape, b, BeamGoldenTest.ORIGIN));
            assertThat(BeamRules.connections(a, BeamGoldenTest.ORIGIN).toString())
                .isEqualTo(BeamRules.connections(b, BeamGoldenTest.ORIGIN).toString());
            for (Direction d : Direction.values()) {
                BeamRules.BeamUpdate first = BeamRules.update(a, BeamGoldenTest.ORIGIN, d);
                BeamRules.BeamUpdate again = BeamRules.update(b, BeamGoldenTest.ORIGIN, d);
                assertThat(first).isEqualTo(again);
            }
        }
    }

    @Test
    void callOrderDoesNotChangeTheSpanTable() {
        // Evaluate the span table backwards and forwards with one view each: the results must match index by index.
        String table = BeamGoldenTest.SPAN_DIGITS;
        int n = table.length();
        int[] forward = new int[n];
        int[] backward = new int[n];
        for (int i = 0; i < n; i++) {
            forward[i] = spanAt(i);
        }
        for (int i = n - 1; i >= 0; i--) {
            backward[i] = spanAt(i);
        }
        assertThat(backward).containsExactly(forward);
    }

    /** The span for table index {@code index}, along north, as the golden table defines it. */
    private static int spanAt(int index) {
        int origin = index / 1024;
        int code = index % 1024;
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        cells.put(BeamGoldenTest.ORIGIN, BeamGoldenTest.cellOf(origin == 0 ? 'O' : 'H'));
        for (int k = 1; k <= 5; k++) {
            cells.put(BeamGoldenTest.ORIGIN.offset(0, 0, -k), BeamGoldenTest.cellOf("VHOS".charAt((code >> (2 * (k - 1))) & 3)));
        }
        return BeamRules.span(BeamGoldenTest.view(cells), BeamGoldenTest.ORIGIN, Direction.NORTH);
    }

    @Test
    void connectionsIterateInEnumOrder() {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        cells.put(BeamGoldenTest.ORIGIN.offset(Direction.WEST), BeamGoldenTest.cellOf('V'));
        cells.put(BeamGoldenTest.ORIGIN.offset(Direction.NORTH), BeamGoldenTest.cellOf('H'));
        cells.put(BeamGoldenTest.ORIGIN.offset(Direction.EAST), BeamGoldenTest.cellOf('H'));
        for (int i = 0; i < 3; i++) {
            // EnumSet iterates in Direction ordinal order (NORTH before WEST before EAST), on every call.
            assertThat(BeamRules.connections(BeamGoldenTest.view(cells), BeamGoldenTest.ORIGIN)).containsExactly(Direction.NORTH, Direction.WEST, Direction.EAST);
        }
    }
}
