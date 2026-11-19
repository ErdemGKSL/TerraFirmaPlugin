/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.logging.TreeLogs.Pos;
import dev.tfp.core.worldgen.tree.BranchDirection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-08/03: TFC's {@code BranchDirection} geometry (rows exported from the pinned TFC) and the felling search. */
class TreeLogsTest {
    private static final BranchDirection D = BranchDirection.DOWN;

    private static List<String> rows() {
        try (var in = TreeLogsTest.class.getResourceAsStream("branch-offsets.tsv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void offsetsMatchTfcForEveryDirection() {
        assertThat(rows()).hasSize(BranchDirection.values().length);
        for (String row : rows()) {
            String[] c = row.split("\t");
            BranchDirection d = BranchDirection.byName(c[0]);
            assertThat(TreeLogs.dx(d)).as(c[0] + " dx").isEqualTo(Integer.parseInt(c[1]));
            assertThat(TreeLogs.dy(d)).as(c[0] + " dy").isEqualTo(Integer.parseInt(c[2]));
            assertThat(TreeLogs.dz(d)).as(c[0] + " dz").isEqualTo(Integer.parseInt(c[3]));
            assertThat(TreeLogs.trunk(d)).as(c[0] + " trunk").isEqualTo(c[4].equals("1"));
        }
    }

    private static final class World implements TreeLogs.Blocks {
        final Map<Pos, BranchDirection> logs = new HashMap<>();
        int reads;

        World log(int x, int y, int z, BranchDirection d) {
            logs.put(new Pos(x, y, z), d);
            return this;
        }

        @Override
        public Optional<BranchDirection> branchAt(int x, int y, int z) {
            reads++;
            return Optional.ofNullable(logs.get(new Pos(x, y, z)));
        }
    }

    private static World oak() {
        World w = new World();
        for (int y = 0; y < 5; y++) {
            w.log(0, y, 0, D);
        }
        w.log(1, 3, 0, BranchDirection.WEST);      // a branch hanging on the trunk at (0, 3, 0)
        w.log(2, 3, 0, BranchDirection.WEST);      // and its tip, hanging on the branch
        w.log(0, 2, 1, BranchDirection.NONE);      // a player-placed log against the trunk
        w.log(-1, 2, 0, BranchDirection.EAST);     // hangs on (0,2,0)? no: EAST dx=+1 -> (0,2,0) yes
        w.log(-1, 4, 0, BranchDirection.WEST);     // hangs on (-2,4,0): not on this tree
        return w;
    }

    @Test
    void felledSetHoldsTheConnectedLogsOnly() {
        List<Pos> logs = TreeLogs.find(oak(), new Pos(0, 0, 0));
        assertThat(logs).containsExactlyInAnyOrder(new Pos(0, 0, 0), new Pos(0, 1, 0), new Pos(0, 2, 0), new Pos(0, 3, 0),
                new Pos(0, 4, 0), new Pos(1, 3, 0), new Pos(2, 3, 0), new Pos(-1, 2, 0));
        assertThat(logs.getLast()).as("the cut block comes last (TFC reverses the discovery order)")
                .isEqualTo(new Pos(0, 0, 0));
    }

    @Test
    void cuttingHigherOnlyTakesWhatHangsOnIt() {
        List<Pos> logs = TreeLogs.find(oak(), new Pos(0, 3, 0));
        assertThat(logs).containsExactlyInAnyOrder(new Pos(0, 3, 0), new Pos(0, 4, 0), new Pos(1, 3, 0), new Pos(2, 3, 0));
    }

    @Test
    void placedLogsAreNotLoggingBlocks() {
        World w = oak();
        assertThat(TreeLogs.shouldLog(w, new Pos(0, 2, 1))).isFalse();
        assertThat(TreeLogs.shouldLog(w, new Pos(0, 0, 0))).isTrue();
        assertThat(TreeLogs.shouldLog(w, new Pos(5, 5, 5))).isFalse();
    }

    @Test
    void twoByTwoTrunkFallsOnlyWhenTheLastOfALevelIsCut() {
        World w = new World();
        w.log(0, 0, 0, BranchDirection.TRUNK_SOUTH_EAST);
        w.log(1, 0, 0, BranchDirection.TRUNK_SOUTH_WEST);
        w.log(0, 0, 1, BranchDirection.TRUNK_NORTH_EAST);
        w.log(1, 0, 1, BranchDirection.TRUNK_NORTH_WEST);
        w.log(0, 1, 0, BranchDirection.TRUNK_SOUTH_EAST);
        w.log(1, 1, 0, BranchDirection.TRUNK_SOUTH_WEST);
        w.log(0, 1, 1, BranchDirection.TRUNK_NORTH_EAST);
        w.log(1, 1, 1, BranchDirection.TRUNK_NORTH_WEST);
        for (Pos p : List.copyOf(w.logs.keySet())) {
            if (p.y() == 0) {
                assertThat(TreeLogs.shouldLog(w, p)).as("level 0 full: " + p).isFalse();
            }
        }
        w.logs.remove(new Pos(1, 0, 0));
        w.logs.remove(new Pos(0, 0, 1));
        w.logs.remove(new Pos(1, 0, 1));
        assertThat(TreeLogs.shouldLog(w, new Pos(0, 0, 0))).isTrue();
        // the whole 2x2 column above comes down with it (trunk blocks also connect sideways)
        assertThat(TreeLogs.find(w, new Pos(0, 0, 0))).hasSize(5);
    }

    @Test
    void searchIsBounded() {
        World w = new World();
        for (int y = 0; y < TreeLogs.MAX_LOGS + 500; y++) {
            w.log(0, y, 0, D);
        }
        assertThat(TreeLogs.find(w, new Pos(0, 0, 0))).hasSize(TreeLogs.MAX_LOGS);
    }
}
