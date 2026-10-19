/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/util/RotationNetworkTest.java @ c3b4d9536 (the straight-line and manager cases)
 *
 * The TFC test builds networks of a passthrough test node (MockNode: the rotation keeps its hand on the exit axis, speed 1.0).
 * That is the production AXLE for straight lines of at most five axles, so these cases use {@link NodeKind#AXLE} with
 * {@link NodeKind#SOURCE} sources. Where TFC's toString shows a node's rotation, the assertion checks its hand and speed, not the
 * string. Not ported here: the cycle cases (testSourceConnectedToCycle, testRemovingBranchInCycle,
 * testConnectTwoDifferentHandRotationsInCycle, testConnectTwoDifferentHandRotationsInCycleFromUpdate), which need the five-face
 * test node with axis remapping and an InvertNode with perpendicular exits, neither of which is a production kind. Also not
 * ported: testSourceAndDisconnectedNodes' first add, which places a node on the source's own position (TFC overwrites the entry;
 * {@link RotationNetworks} refuses a duplicate position). The rest of that test is ported with distinct positions.
 */
package dev.tfp.core.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RotationNetworkManagerPortTest {

    private static RotationNetworks.Outcome add(RotationNetworks networks, int x, int y, int z, Direction... faces) {
        return networks.add(RotationNode.of(new BlockPos(x, y, z), NodeKind.AXLE, Set.of(faces)));
    }

    private static RotationNetworks.Outcome update(RotationNetworks networks, int x, int y, int z, Direction... faces) {
        return networks.update(RotationNode.of(new BlockPos(x, y, z), NodeKind.AXLE, Set.of(faces)));
    }

    private static RotationNetworks.Outcome addSource(RotationNetworks networks, int x, int y, int z, Direction dir, Direction... rest) {
        Set<Direction> faces = EnumSet.of(dir, rest);
        return networks.addSource(RotationNode.source(new BlockPos(x, y, z), faces, dir, 1.0f));
    }

    private static void remove(RotationNetworks networks, int x, int y, int z) {
        networks.remove(new BlockPos(x, y, z));
    }

    private static void assertRotation(RotationNetworks networks, int x, int y, int z, Direction hand) {
        BlockPos pos = new BlockPos(x, y, z);
        assertThat(networks.networkOf(pos)).isPresent();
        assertThat(networks.incoming(pos).orElseThrow().direction()).as("hand at %s", pos).isEqualTo(hand);
        assertThat(networks.incoming(pos).orElseThrow().speed()).isEqualTo(1.0f);
    }

    private static void assertAbsent(RotationNetworks networks, int x, int y, int z) {
        assertThat(networks.networkOf(new BlockPos(x, y, z))).isEqualTo(OptionalLong.empty());
    }

    /** TFC testEmpty: an empty manager has no networks. */
    @Test
    void testEmpty() {
        assertThat(new RotationNetworks().networkCount()).isZero();
    }

    /** TFC testSingleSource: a lone source is network 0. */
    @Test
    void testSingleSource() {
        RotationNetworks networks = new RotationNetworks();
        assertThat(addSource(networks, 0, 0, 0, Direction.NORTH).accepted()).isTrue();
        assertThat(networks.networkOf(new BlockPos(0, 0, 0))).isEqualTo(OptionalLong.of(0));
    }

    /** TFC testConnectedSourcesCausesOtherToBreak: a second source that connects back into the first is refused. */
    @Test
    void testConnectedSourcesCausesOtherToBreak() {
        RotationNetworks networks = new RotationNetworks();
        assertThat(addSource(networks, 0, 0, 0, Direction.NORTH, Direction.SOUTH).accepted()).isTrue();
        RotationNetworks.Outcome second = addSource(networks, 0, 0, 1, Direction.NORTH, Direction.SOUTH);
        assertThat(second.accepted()).isFalse();
        assertThat(second.broken()).containsExactly(new BlockPos(0, 0, 1));
        assertAbsent(networks, 0, 0, 1);
        assertThat(networks.networkCount()).isEqualTo(1);
    }

    /** TFC testSingleNodeNoSource: a node with no source is stored, not in a network, and breaks nothing. */
    @Test
    void testSingleNodeNoSource() {
        RotationNetworks networks = new RotationNetworks();
        RotationNetworks.Outcome outcome = add(networks, 0, 0, 0, Direction.EAST, Direction.WEST);
        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.broken()).isEmpty();
        assertThat(networks.networkCount()).isZero();
        assertAbsent(networks, 0, 0, 0);
    }

    /**
     * TFC testSourceAndDisconnectedNodes, with distinct positions: a source facing north, and three nodes that do not face it
     * or each other. None joins.
     */
    @Test
    void testSourceAndDisconnectedNodes() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.NORTH);
        assertThat(add(networks, 1, 0, 0, Direction.EAST, Direction.WEST).accepted()).isTrue();
        assertThat(add(networks, 0, 0, 1, Direction.NORTH, Direction.SOUTH).accepted()).isTrue();
        assertThat(add(networks, 1, 0, 1, Direction.NORTH, Direction.SOUTH).accepted()).isTrue();
        assertThat(networks.networkOf(new BlockPos(0, 0, 0))).isEqualTo(OptionalLong.of(0));
        assertAbsent(networks, 1, 0, 0);
        assertAbsent(networks, 0, 0, 1);
        assertAbsent(networks, 1, 0, 1);
    }

    /** TFC testSourceAndConnectedNodes: nodes added after the source join it and carry its hand. */
    @Test
    void testSourceAndConnectedNodes() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.NORTH);
        assertThat(add(networks, 0, 0, -1, Direction.NORTH, Direction.SOUTH).accepted()).isTrue();
        assertThat(add(networks, 0, 0, -2, Direction.NORTH, Direction.SOUTH).accepted()).isTrue();
        assertRotation(networks, 0, 0, -1, Direction.NORTH);
        assertRotation(networks, 0, 0, -2, Direction.NORTH);
    }

    /** TFC testSourceAndConnectedNodesInWrongOrder: the far node is added first and joins when the near one arrives. */
    @Test
    void testSourceAndConnectedNodesInWrongOrder() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.NORTH);
        add(networks, 0, 0, -2, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, -1, Direction.NORTH, Direction.SOUTH);
        assertRotation(networks, 0, 0, -1, Direction.NORTH);
        assertRotation(networks, 0, 0, -2, Direction.NORTH);
    }

    /** TFC testSourceAndConnectedNodesWithSourceLast: the source placed last reaches the disconnected nodes beside it. */
    @Test
    void testSourceAndConnectedNodesWithSourceLast() {
        RotationNetworks networks = new RotationNetworks();
        add(networks, 0, 0, -1, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, -2, Direction.NORTH, Direction.SOUTH);
        assertThat(addSource(networks, 0, 0, 0, Direction.NORTH).accepted()).isTrue();
        assertRotation(networks, 0, 0, -1, Direction.NORTH);
        assertRotation(networks, 0, 0, -2, Direction.NORTH);
    }

    /** TFC testRemovingNodeAtLeaf: removing the far node leaves the near one connected. */
    @Test
    void testRemovingNodeAtLeaf() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.NORTH);
        add(networks, 0, 0, -1, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, -2, Direction.NORTH, Direction.SOUTH);
        assertThat(networks.remove(new BlockPos(0, 0, -2))).isEmpty();
        assertRotation(networks, 0, 0, -1, Direction.NORTH);
        assertAbsent(networks, 0, 0, -2);
    }

    /** TFC testRemovingNodeAtBranch: removing a middle node disconnects everything past it. */
    @Test
    void testRemovingNodeAtBranch() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.NORTH);
        add(networks, 0, 0, -1, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, -2, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, -3, Direction.NORTH, Direction.SOUTH);
        remove(networks, 0, 0, -2);
        assertRotation(networks, 0, 0, -1, Direction.NORTH);
        assertAbsent(networks, 0, 0, -3);
    }

    /** TFC testMultipleSources: two sources are two networks, numbered in the order they are added. */
    @Test
    void testMultipleSources() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        addSource(networks, 2, 0, 0, Direction.WEST);
        assertThat(networks.networkOf(new BlockPos(0, 0, 0))).isEqualTo(OptionalLong.of(0));
        assertThat(networks.networkOf(new BlockPos(2, 0, 0))).isEqualTo(OptionalLong.of(1));
    }

    /** TFC testAddingAndRemovingSources: a removed network's id is not reused; the next source gets the next id. */
    @Test
    void testAddingAndRemovingSources() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        remove(networks, 0, 0, 0);
        addSource(networks, 2, 0, 0, Direction.WEST);
        addSource(networks, 0, 0, 0, Direction.EAST);
        remove(networks, 2, 0, 0);
        addSource(networks, 2, 0, 0, Direction.WEST);
        assertThat(networks.networkOf(new BlockPos(0, 0, 0))).isEqualTo(OptionalLong.of(2));
        assertThat(networks.networkOf(new BlockPos(2, 0, 0))).isEqualTo(OptionalLong.of(3));
    }

    /** TFC testConnectingTwoSourcesShouldFail: a node that touches two networks is refused. */
    @Test
    void testConnectingTwoSourcesShouldFail() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        addSource(networks, 2, 0, 0, Direction.WEST);
        RotationNetworks.Outcome outcome = add(networks, 1, 0, 0, Direction.EAST, Direction.WEST);
        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.broken()).containsExactly(new BlockPos(1, 0, 0));
        assertAbsent(networks, 1, 0, 0);
    }

    /** TFC testUpdatingConnectivityToConnectAtLeaf: a disconnected end node that connects on update joins the network. */
    @Test
    void testUpdatingConnectivityToConnectAtLeaf() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        add(networks, 1, 0, 0, Direction.EAST, Direction.WEST);
        add(networks, 2, 0, 0);
        assertThat(update(networks, 2, 0, 0, Direction.WEST).accepted()).isTrue();
        assertRotation(networks, 1, 0, 0, Direction.EAST);
        assertRotation(networks, 2, 0, 0, Direction.EAST);
    }

    /** TFC testUpdatingConnectivityToConnectAtBranch: a middle node that connects on update joins both neighbours. */
    @Test
    void testUpdatingConnectivityToConnectAtBranch() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        add(networks, 1, 0, 0, Direction.EAST, Direction.WEST);
        add(networks, 2, 0, 0, Direction.EAST);
        add(networks, 3, 0, 0, Direction.EAST, Direction.WEST);
        assertThat(update(networks, 2, 0, 0, Direction.WEST, Direction.EAST).accepted()).isTrue();
        assertRotation(networks, 1, 0, 0, Direction.EAST);
        assertRotation(networks, 2, 0, 0, Direction.EAST);
        assertRotation(networks, 3, 0, 0, Direction.EAST);
    }

    /** TFC testUpdatingDownstreamConnectivityToConnectAtBranch: an update that opens a face toward a waiting node pulls it in. */
    @Test
    void testUpdatingDownstreamConnectivityToConnectAtBranch() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        add(networks, 1, 0, 0, Direction.EAST, Direction.WEST);
        add(networks, 2, 0, 0, Direction.WEST);
        add(networks, 3, 0, 0, Direction.EAST, Direction.WEST);
        assertThat(update(networks, 2, 0, 0, Direction.WEST, Direction.EAST).accepted()).isTrue();
        assertRotation(networks, 1, 0, 0, Direction.EAST);
        assertRotation(networks, 2, 0, 0, Direction.EAST);
        assertRotation(networks, 3, 0, 0, Direction.EAST);
    }

    /** TFC testUpdatingConnectivityToDisconnectAtLeaf: an update that clears a leaf's faces disconnects only that leaf. */
    @Test
    void testUpdatingConnectivityToDisconnectAtLeaf() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        add(networks, 1, 0, 0, Direction.EAST, Direction.WEST);
        add(networks, 2, 0, 0, Direction.EAST, Direction.WEST);
        assertThat(update(networks, 2, 0, 0).accepted()).isTrue();
        assertRotation(networks, 1, 0, 0, Direction.EAST);
        assertAbsent(networks, 2, 0, 0);
    }

    /** TFC testUpdatingConnectivityToDisconnectAtBranch: clearing a middle node disconnects the chain beyond it. */
    @Test
    void testUpdatingConnectivityToDisconnectAtBranch() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        add(networks, 1, 0, 0, Direction.EAST, Direction.WEST);
        add(networks, 2, 0, 0, Direction.EAST, Direction.WEST);
        add(networks, 3, 0, 0, Direction.EAST, Direction.WEST);
        assertThat(update(networks, 2, 0, 0).accepted()).isTrue();
        assertRotation(networks, 1, 0, 0, Direction.EAST);
        assertAbsent(networks, 3, 0, 0);
    }

    /**
     * TFC testConnectingTwoSourcesViaUpdate: a node that joins one source, then on update also touches the other, is refused,
     * and the first network keeps only its source.
     */
    @Test
    void testConnectingTwoSourcesViaUpdate() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        addSource(networks, 2, 0, 0, Direction.WEST);
        assertThat(add(networks, 1, 0, 0, Direction.WEST).accepted()).isTrue();
        RotationNetworks.Outcome outcome = update(networks, 1, 0, 0, Direction.WEST, Direction.EAST);
        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.broken()).containsExactly(new BlockPos(1, 0, 0));
        assertAbsent(networks, 1, 0, 0);
        assertThat(networks.networkOf(new BlockPos(0, 0, 0))).isEqualTo(OptionalLong.of(0));
        assertThat(networks.networkOf(new BlockPos(2, 0, 0))).isEqualTo(OptionalLong.of(1));
    }

    /**
     * TFC testConnectingTwoSourcesViaUpdateAndDisconnectingLeaf: the refused update also takes away the node that hung off it.
     */
    @Test
    void testConnectingTwoSourcesViaUpdateAndDisconnectingLeaf() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        addSource(networks, 2, 0, 0, Direction.WEST);
        assertThat(add(networks, 1, 0, 0, Direction.WEST, Direction.UP).accepted()).isTrue();
        assertThat(add(networks, 1, 1, 0, Direction.DOWN).accepted()).isTrue();
        assertThat(update(networks, 1, 0, 0, Direction.WEST, Direction.UP, Direction.EAST).accepted()).isFalse();
        assertAbsent(networks, 1, 0, 0);
        assertAbsent(networks, 1, 1, 0);
        assertThat(networks.networkOf(new BlockPos(0, 0, 0))).isEqualTo(OptionalLong.of(0));
    }

    /** TFC testRemovingSource: removing the source removes its whole network. */
    @Test
    void testRemovingSource() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.EAST);
        add(networks, 1, 0, 0, Direction.WEST, Direction.UP);
        add(networks, 1, 1, 0, Direction.DOWN);
        remove(networks, 0, 0, 0);
        assertThat(networks.networkCount()).isZero();
        assertAbsent(networks, 1, 0, 0);
        assertAbsent(networks, 1, 1, 0);
    }

    /** TFC testExactRightLengthAxleWorks: five axles in a row all connect. */
    @Test
    void testExactRightLengthAxleWorks() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.SOUTH);
        for (int z = 1; z <= 5; z++) {
            RotationNetworks.Outcome outcome = add(networks, 0, 0, z, Direction.NORTH, Direction.SOUTH);
            assertThat(outcome.accepted()).isTrue();
            assertThat(outcome.broken()).isEmpty();
        }
        for (int z = 1; z <= 5; z++) {
            assertRotation(networks, 0, 0, z, Direction.SOUTH);
        }
    }

    /** TFC testTooLongAxleFailsToAdd: the sixth axle in a row is refused and its block breaks. */
    @Test
    void testTooLongAxleFailsToAdd() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.SOUTH);
        for (int z = 1; z <= 5; z++) {
            add(networks, 0, 0, z, Direction.NORTH, Direction.SOUTH);
        }
        RotationNetworks.Outcome sixth = add(networks, 0, 0, 6, Direction.NORTH, Direction.SOUTH);
        assertThat(sixth.accepted()).isFalse();
        assertThat(sixth.broken()).containsExactly(new BlockPos(0, 0, 6));
        assertAbsent(networks, 0, 0, 6);
        assertRotation(networks, 0, 0, 5, Direction.SOUTH);
    }

    /**
     * TFC testTooLongAxleFailsWhenAddedInMiddle: a gap filled last pulls the disconnected axles on; the sixth fails in that pass.
     */
    @Test
    void testTooLongAxleFailsWhenAddedInMiddle() {
        RotationNetworks networks = new RotationNetworks();
        addSource(networks, 0, 0, 0, Direction.SOUTH);
        add(networks, 0, 0, 1, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, 2, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, 4, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, 5, Direction.NORTH, Direction.SOUTH);
        add(networks, 0, 0, 6, Direction.NORTH, Direction.SOUTH);
        RotationNetworks.Outcome middle = add(networks, 0, 0, 3, Direction.NORTH, Direction.SOUTH);

        assertThat(middle.accepted()).isTrue();
        assertThat(middle.broken()).containsExactly(new BlockPos(0, 0, 6));
        for (int z = 1; z <= 5; z++) {
            assertRotation(networks, 0, 0, z, Direction.SOUTH);
        }
        assertAbsent(networks, 0, 0, 6);
        assertThat(networks.networkCount()).isEqualTo(1);
    }
}
