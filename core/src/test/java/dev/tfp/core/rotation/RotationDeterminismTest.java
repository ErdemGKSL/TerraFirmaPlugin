/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/RotationNetwork.java @ c3b4d9536 (updateOnAdd, the handedness check, lines 90-101)
 */
package dev.tfp.core.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RotationDeterminismTest {

    /**
     * A four-cycle: source S at the origin on SOUTH and EAST; axle C at (0,0,1) on NORTH/EAST; gearboxes G1 at (1,0,0) on
     * WEST/SOUTH and G2 at (1,0,1) on NORTH/WEST. The cycle is the case where TFC's result depends on the order nodes arrive
     * in, since a node next to two members of one network is accepted only when the handedness agrees.
     * TFC: {@code RotationNetwork.updateOnAdd} (lines 90-101) checks each second face against the first; the outcome is the
     * same for any order of {@code load} here, because the port inserts in (x, y, z) order.
     *
     * <p>The test loads the same nodes in forward order, reverse order, and several seeded shuffles, and asserts that the network
     * of every position, the rotation it receives, and the broken list are the same in every run.
     */
    @Test
    void sameGraphInAnyInputOrderGivesTheSameNetworks() {
        List<RotationNode> graph = List.of(
            RotationNode.source(new BlockPos(0, 0, 0), Set.of(Direction.SOUTH, Direction.EAST), Direction.UP, 0.2f),
            RotationNode.of(new BlockPos(0, 0, 1), NodeKind.AXLE, Set.of(Direction.NORTH, Direction.EAST)),
            RotationNode.of(new BlockPos(1, 0, 0), NodeKind.GEAR_BOX, Set.of(Direction.WEST, Direction.SOUTH)),
            RotationNode.of(new BlockPos(1, 0, 1), NodeKind.GEAR_BOX, Set.of(Direction.NORTH, Direction.WEST))
        );
        List<BlockPos> positions = graph.stream().map(RotationNode::pos).toList();

        String reference = snapshot(new ArrayList<>(graph), positions);
        assertThat(reference).isNotEmpty();

        List<RotationNode> reversed = new ArrayList<>(graph);
        Collections.reverse(reversed);
        assertThat(snapshot(reversed, positions)).isEqualTo(reference);

        for (long seed = 1; seed <= 8; seed++) {
            List<RotationNode> shuffled = new ArrayList<>(graph);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(snapshot(shuffled, positions)).as("seed %d", seed).isEqualTo(reference);
        }
    }

    /** Loads the nodes into a fresh set of networks and renders, per position in a fixed order, its network, its incoming rotation and the broken list. */
    private static String snapshot(List<RotationNode> nodes, List<BlockPos> positions) {
        RotationNetworks networks = new RotationNetworks();
        List<BlockPos> broken = networks.load(nodes);
        StringBuilder out = new StringBuilder();
        out.append("broken=").append(broken).append('\n');
        for (BlockPos pos : positions) {
            out.append(pos).append(" network=").append(networks.networkOf(pos))
                .append(" incoming=").append(networks.incoming(pos)).append('\n');
        }
        return out.toString();
    }
}
