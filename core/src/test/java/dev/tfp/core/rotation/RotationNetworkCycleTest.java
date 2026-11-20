/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/util/RotationNetworkTest.java @ c3b4d9536 (testSourceConnectedToCycle,
 *       testRemovingBranchInCycle, testConnectTwoDifferentHandRotationsInCycle,
 *       testConnectTwoDifferentHandRotationsInCycleFromUpdate, MockNode, InvertNode)
 *
 * TFC's MockNode and InvertNode are test nodes: they pass the rotation on with the hand kept (or inverted) on the exit
 * axis, speed 1.0. They are placed here through the package-private transfer seam of RotationNetworks. TFC's toString
 * lines "rotation=[<source face>, Rotation[direction=<hand>, speed=1.0]]" are checked as parentOf and incoming.
 */
package dev.tfp.core.rotation;

import static dev.tfp.core.world.Direction.DOWN;
import static dev.tfp.core.world.Direction.EAST;
import static dev.tfp.core.world.Direction.NORTH;
import static dev.tfp.core.world.Direction.SOUTH;
import static dev.tfp.core.world.Direction.UP;
import static dev.tfp.core.world.Direction.WEST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RotationNetworkCycleTest {
    /** TFC MockNode: the source hand's axis direction, carried onto the exit axis. */
    static final RotationNetworks.Transfer MOCK = (in, src, exit) ->
        Rotation.of(RotationAxis.of(exit).direction(RotationAxis.isPositive(in.direction())), 1.0f);
    /** TFC InvertNode: the opposite of MockNode on every exit. */
    static final RotationNetworks.Transfer INVERT = (in, src, exit) ->
        Rotation.of(RotationAxis.of(exit).direction(RotationAxis.isPositive(in.direction())).opposite(), 1.0f);

    /** The cycle layouts: the mock positions with their rule, then one RotationNetworks over them. */
    private static RotationNetworks mock(Map<BlockPos, RotationNetworks.Transfer> nodes) {
        return new RotationNetworks(nodes);
    }

    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private static boolean add(RotationNetworks n, BlockPos pos, Direction... faces) {
        return n.add(RotationNode.of(pos, NodeKind.GEAR_BOX, Set.of(faces))).accepted();
    }

    private static boolean source(RotationNetworks n, BlockPos pos, Direction dir) {
        return n.addSource(RotationNode.source(pos, EnumSet.of(dir), dir, 1.0f)).accepted();
    }

    private static void assertNode(RotationNetworks n, BlockPos pos, Direction parent, Direction hand) {
        assertThat(n.networkOf(pos)).as("network of %s", pos).hasValue(0);
        assertThat(n.parentOf(pos)).as("source face of %s", pos).contains(parent);
        assertThat(n.incoming(pos).orElseThrow().direction()).as("hand at %s", pos).isEqualTo(hand);
        assertThat(n.incoming(pos).orElseThrow().speed()).isEqualTo(1.0f);
    }

    private static Map<BlockPos, RotationNetworks.Transfer> square() {
        Map<BlockPos, RotationNetworks.Transfer> m = new HashMap<>();
        for (BlockPos pos : new BlockPos[] {p(0, 1, 0), p(1, 1, 0), p(0, 1, 1), p(1, 1, 1)}) {
            m.put(pos, MOCK);
        }
        return m;
    }

    private static RotationNetworks squareCycle() {
        RotationNetworks n = mock(square());
        assertThat(source(n, p(0, 0, 0), UP)).isTrue();
        assertThat(add(n, p(0, 1, 0), DOWN, NORTH, EAST, SOUTH, WEST)).isTrue();
        assertThat(add(n, p(1, 1, 0), NORTH, EAST, SOUTH, WEST)).isTrue();
        assertThat(add(n, p(0, 1, 1), NORTH, EAST, SOUTH, WEST)).isTrue();
        assertThat(add(n, p(1, 1, 1), NORTH, EAST, SOUTH, WEST)).isTrue();
        return n;
    }

    /** TFC testSourceConnectedToCycle. */
    @Test
    void testSourceConnectedToCycle() {
        RotationNetworks n = squareCycle();
        assertThat(n.networkOf(p(0, 0, 0))).hasValue(0);
        assertNode(n, p(0, 1, 0), DOWN, UP);
        assertNode(n, p(1, 1, 0), WEST, EAST);
        assertNode(n, p(0, 1, 1), NORTH, SOUTH);
        assertNode(n, p(1, 1, 1), NORTH, SOUTH);
        assertThat(n.networkCount()).isEqualTo(1);
    }

    /** TFC testRemovingBranchInCycle: the far corner re-parents through the other branch. */
    @Test
    void testRemovingBranchInCycle() {
        RotationNetworks n = squareCycle();
        assertThat(n.remove(p(1, 1, 0))).isEmpty();
        assertNode(n, p(0, 1, 0), DOWN, UP);
        assertNode(n, p(0, 1, 1), NORTH, SOUTH);
        assertNode(n, p(1, 1, 1), WEST, EAST);
        assertThat(n.networkOf(p(1, 1, 0))).isEmpty();
    }

    private static RotationNetworks handLoop() {
        Map<BlockPos, RotationNetworks.Transfer> m = new HashMap<>();
        m.put(p(0, 1, 0), MOCK);
        m.put(p(1, 1, 0), MOCK);
        m.put(p(1, 2, 0), INVERT);
        m.put(p(1, 3, 0), MOCK);
        m.put(p(0, 2, 0), MOCK);
        RotationNetworks n = mock(m);
        assertThat(source(n, p(0, 0, 0), UP)).isTrue();
        assertThat(add(n, p(0, 1, 0), EAST, DOWN, UP)).isTrue();
        assertThat(add(n, p(1, 1, 0), WEST, UP)).isTrue();
        assertThat(add(n, p(1, 2, 0), WEST, DOWN, UP)).isTrue();
        assertThat(add(n, p(1, 3, 0), DOWN)).isTrue();
        return n;
    }

    private static void assertHandLoop(RotationNetworks n) {
        assertNode(n, p(0, 1, 0), DOWN, UP);
        assertNode(n, p(1, 1, 0), WEST, EAST);
        assertNode(n, p(1, 2, 0), DOWN, UP);
        assertNode(n, p(1, 3, 0), DOWN, DOWN);
        assertThat(n.networkOf(p(0, 2, 0))).isEmpty();
    }

    /** TFC testConnectTwoDifferentHandRotationsInCycle: closing the loop with opposite hands is refused. */
    @Test
    void testConnectTwoDifferentHandRotationsInCycle() {
        RotationNetworks n = handLoop();
        RotationNetworks.Outcome closing = n.add(RotationNode.of(p(0, 2, 0), NodeKind.GEAR_BOX, Set.of(EAST, DOWN)));
        assertThat(closing.accepted()).isFalse();
        assertThat(closing.broken()).containsExactly(p(0, 2, 0));
        assertHandLoop(n);
    }

    /** TFC testConnectTwoDifferentHandRotationsInCycleFromUpdate: the same refusal when an update adds the face. */
    @Test
    void testConnectTwoDifferentHandRotationsInCycleFromUpdate() {
        RotationNetworks n = handLoop();
        assertThat(add(n, p(0, 2, 0), DOWN)).isTrue();
        assertThat(n.networkOf(p(0, 2, 0))).hasValue(0);
        RotationNetworks.Outcome update = n.update(RotationNode.of(p(0, 2, 0), NodeKind.GEAR_BOX, Set.of(DOWN, EAST)));
        assertThat(update.accepted()).isFalse();
        assertThat(update.broken()).containsExactly(p(0, 2, 0));
        assertHandLoop(n);
    }
}
