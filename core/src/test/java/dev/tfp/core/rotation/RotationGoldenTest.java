/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/RotationNetwork.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/util/rotation/RotationNetworkManager.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/util/rotation/AxleNode.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/GearBoxBlockEntity.java @ c3b4d9536
 *
 * Golden values are derived by hand from the TFC algorithm line by line; TFC has no data file for the solver. Each test's
 * Javadoc names the TFC lines it relies on. TFC has no torque, stall or gear ratio at this commit, so the cases are about
 * handedness, axle length and connectivity, and the speed passes through unchanged.
 */
package dev.tfp.core.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RotationGoldenTest {

    private static final float SPEED = 0.1f;

    private static RotationNode source(BlockPos pos, Set<Direction> faces, Direction direction, float speed) {
        return RotationNode.source(pos, faces, direction, speed);
    }

    private static RotationNode node(BlockPos pos, NodeKind kind, Direction... faces) {
        return RotationNode.of(pos, kind, Set.of(faces));
    }

    /**
     * A source at the origin on EAST, emitting UP at {@link #SPEED}; an axle at x=1 on WEST/EAST; a sink at x=2 on WEST.
     * TFC: {@code RotationNetwork.updateAfterAdd} (BFS from the source, lines 140-185) passes the source rotation to the axle
     * ({@code AxleNode.rotation} lines 29-37 counts one axle), and the axle's rotation to the sink. The sink reads the axle's
     * emitted rotation. The speed is unchanged, the hand is unchanged, and the axle count is 1. Setting the source speed changes
     * the sink's speed at once, as TFC's live {@code SourceNode} rotation does.
     */
    @Test
    void shaftSourceToSinkCarriesSpeedAndDirection() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        RotationNetworks.Outcome axle = networks.add(node(new BlockPos(1, 0, 0), NodeKind.AXLE, Direction.WEST, Direction.EAST));
        RotationNetworks.Outcome sink = networks.add(node(new BlockPos(2, 0, 0), NodeKind.SINK, Direction.WEST));

        assertThat(axle.accepted()).isTrue();
        assertThat(sink.accepted()).isTrue();
        assertThat(networks.incoming(new BlockPos(1, 0, 0))).contains(new Rotation(Direction.UP, SPEED, 0));
        assertThat(networks.incoming(new BlockPos(2, 0, 0))).contains(new Rotation(Direction.UP, SPEED, 1));
        assertThat(networks.networkOf(new BlockPos(2, 0, 0))).isEqualTo(networks.networkOf(new BlockPos(0, 0, 0)));

        networks.setSourceSpeed(new BlockPos(0, 0, 0), 0.5f);
        assertThat(networks.incoming(new BlockPos(2, 0, 0)).orElseThrow().speed()).isEqualTo(0.5f);
    }

    /**
     * A source at the origin on EAST, emitting UP; a gearbox at x=1 on WEST/EAST (same axis as its source face); a sink at x=2.
     * TFC: {@code GearBoxBlockEntity} rotation lines 60-62: the exit is on the same axis as the source face, so the hand is
     * reversed with {@code Rotation.of(sourceRotation, direction.getOpposite())}. Speed is unchanged, and the axle count resets
     * to 0 because {@code Rotation.of} is a plain rotation.
     */
    @Test
    void gearboxSameAxisFlipsHandedness() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        networks.add(node(new BlockPos(1, 0, 0), NodeKind.GEAR_BOX, Direction.WEST, Direction.EAST));
        networks.add(node(new BlockPos(2, 0, 0), NodeKind.SINK, Direction.WEST));

        assertThat(networks.incoming(new BlockPos(2, 0, 0))).contains(new Rotation(Direction.DOWN, SPEED, 0));
    }

    /**
     * A source at the origin on EAST, emitting UP (rotation direction UP); a gearbox at (1,0,0) on WEST/NORTH; a sink at
     * (1,0,-1) on SOUTH. The gearbox's source face is WEST, which is not the rotation direction UP.
     * TFC: {@code GearBoxBlockEntity} lines 68-70: the output is the exit face NORTH, as the source face is not the rotation direction.
     */
    @Test
    void gearboxPerpendicularExitKeepsHandednessWhenSourceFaceIsNotRotationAxis() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        networks.add(node(new BlockPos(1, 0, 0), NodeKind.GEAR_BOX, Direction.WEST, Direction.NORTH));
        networks.add(node(new BlockPos(1, 0, -1), NodeKind.SINK, Direction.SOUTH));

        assertThat(networks.incoming(new BlockPos(1, 0, -1))).contains(new Rotation(Direction.NORTH, SPEED, 0));
    }

    /**
     * A source at the origin on UP, emitting DOWN (rotation direction DOWN); a gearbox at (0,1,0) on DOWN/NORTH; a sink at
     * (0,1,-1) on SOUTH. The gearbox's source face is DOWN, which equals the rotation direction DOWN.
     * TFC: {@code GearBoxBlockEntity} lines 68-70: the convention reverses, so the output is the opposite of the exit NORTH, SOUTH.
     */
    @Test
    void gearboxPerpendicularExitReversesWhenSourceFaceIsRotationDirection() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.UP), Direction.DOWN, SPEED));
        networks.add(node(new BlockPos(0, 1, 0), NodeKind.GEAR_BOX, Direction.DOWN, Direction.NORTH));
        networks.add(node(new BlockPos(0, 1, -1), NodeKind.SINK, Direction.SOUTH));

        assertThat(networks.incoming(new BlockPos(0, 1, -1))).contains(new Rotation(Direction.SOUTH, SPEED, 0));
    }

    /**
     * A source at the origin on EAST, emitting UP; three gearboxes at x=1, 2, 3 on WEST/EAST; a sink at x=4 on WEST.
     * TFC: {@code GearBoxBlockEntity} lines 60-62 applied three times: each gearbox receives the hand its parent emitted, and the
     * hand alternates. Gearbox 1 receives UP and emits DOWN; gearbox 3 receives UP and emits DOWN, so the sink reads DOWN.
     */
    @Test
    void gearChainAlternatesHandednessAtUnchangedSpeed() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        for (int x = 1; x <= 3; x++) {
            networks.add(node(new BlockPos(x, 0, 0), NodeKind.GEAR_BOX, Direction.WEST, Direction.EAST));
        }
        networks.add(node(new BlockPos(4, 0, 0), NodeKind.SINK, Direction.WEST));

        assertThat(networks.incoming(new BlockPos(1, 0, 0))).contains(new Rotation(Direction.UP, SPEED, 0));
        assertThat(networks.incoming(new BlockPos(3, 0, 0))).contains(new Rotation(Direction.UP, SPEED, 0));
        assertThat(networks.incoming(new BlockPos(4, 0, 0))).contains(new Rotation(Direction.DOWN, SPEED, 0));
    }

    /**
     * A source at the origin on EAST, and axles at x=1..6 on WEST/EAST. The fifth axle carries length 5, so the sixth fails.
     * TFC: {@code AxleNode.update} lines 42-46 refuse an incoming {@code AxleRotation} of length {@code MAX_AXLE_LENGTH} (5, line 21),
     * so the sixth axle is invalid and is not added. Its block breaks (the reported position), and the five before it stay connected.
     */
    @Test
    void axleChainOfFiveConnectsSixthBreaks() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        RotationNetworks.Outcome last = null;
        for (int x = 1; x <= 6; x++) {
            last = networks.add(node(new BlockPos(x, 0, 0), NodeKind.AXLE, Direction.WEST, Direction.EAST));
        }

        assertThat(last.broken()).containsExactly(new BlockPos(6, 0, 0));
        assertThat(last.accepted()).isFalse();
        assertThat(networks.networkOf(new BlockPos(5, 0, 0))).isPresent();
        assertThat(networks.incoming(new BlockPos(5, 0, 0))).contains(new Rotation(Direction.UP, SPEED, 4));
        assertThat(networks.networkOf(new BlockPos(6, 0, 0))).isEmpty();
    }

    /**
     * Two sources, separated: A at the origin on EAST (UP at {@link #SPEED}) with a sink at x=1; B at x=5 on WEST (DOWN at 0.3f)
     * with a sink at x=4 on EAST. TFC: {@code RotationNetworkManager.addSource} (lines 69-96) creates a new network id for each
     * source, and each network runs its own rotation.
     */
    @Test
    void twoSourcesRunSeparateNetworks() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        networks.add(node(new BlockPos(1, 0, 0), NodeKind.SINK, Direction.WEST));
        networks.addSource(source(new BlockPos(5, 0, 0), Set.of(Direction.WEST), Direction.DOWN, 0.3f));
        networks.add(node(new BlockPos(4, 0, 0), NodeKind.SINK, Direction.EAST));

        assertThat(networks.networkCount()).isEqualTo(2);
        assertThat(networks.networkOf(new BlockPos(1, 0, 0))).isNotEqualTo(networks.networkOf(new BlockPos(4, 0, 0)));
        assertThat(networks.incoming(new BlockPos(1, 0, 0))).contains(new Rotation(Direction.UP, SPEED, 0));
        assertThat(networks.incoming(new BlockPos(4, 0, 0))).contains(new Rotation(Direction.DOWN, 0.3f, 0));
    }

    /**
     * A source at the origin, and a second source at (-1,0,0) on EAST, next to the first. The first source's face WEST
     * connects back toward the new source, so the new source is refused.
     * TFC: {@code RotationNetworkManager.addSource} lines 75-86: an adjacent node already in a network that connects in that
     * direction makes {@code addSource} return false, and the block breaks.
     */
    @Test
    void sourceAdjacentToRunningNetworkIsRejected() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST, Direction.WEST), Direction.UP, SPEED));
        RotationNetworks.Outcome second = networks.addSource(source(new BlockPos(-1, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));

        assertThat(second.accepted()).isFalse();
        assertThat(second.broken()).containsExactly(new BlockPos(-1, 0, 0));
        assertThat(networks.networkOf(new BlockPos(-1, 0, 0))).isEmpty();
        assertThat(networks.networkCount()).isEqualTo(1);
    }

    /**
     * A source at the origin at speed 0 on EAST, with an axle at x=1 and a sink at x=2. The network stays connected while stopped.
     * TFC: {@code Node.rotation()} doc (Node.java, lines 82-86: "It may be stationary, but connected"). A zero-speed
     * source still counts as a network, so the sink is connected with speed 0 and keeps its hand. Setting the source to
     * {@link #SPEED} starts it again.
     */
    @Test
    void stalledNetworkStaysConnectedAtZeroSpeed() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, 0f));
        networks.add(node(new BlockPos(1, 0, 0), NodeKind.AXLE, Direction.WEST, Direction.EAST));
        networks.add(node(new BlockPos(2, 0, 0), NodeKind.SINK, Direction.WEST));

        assertThat(networks.networkOf(new BlockPos(2, 0, 0))).isPresent();
        assertThat(networks.incoming(new BlockPos(2, 0, 0))).contains(new Rotation(Direction.UP, 0f, 1));

        networks.setSourceSpeed(new BlockPos(0, 0, 0), SPEED);
        assertThat(networks.incoming(new BlockPos(2, 0, 0)).orElseThrow().speed()).isEqualTo(SPEED);
    }

    /**
     * A source at the origin on EAST, and an axle at (1,0,0) that connects only UP and DOWN, so it does not face the source. The
     * axle is not connected to the network and is not broken. TFC: {@code RotationNetworkManager.add} lines 136-151: a node with no network connection is stored as disconnected (
     * {@code toAdd.remove()}) and the add succeeds, so the block is kept and nothing breaks.
     */
    @Test
    void disconnectedAxleDoesNotJoinAndDoesNotBreak() {
        RotationNetworks networks = new RotationNetworks();
        networks.addSource(source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, SPEED));
        RotationNetworks.Outcome outcome = networks.add(node(new BlockPos(1, 0, 0), NodeKind.AXLE, Direction.UP, Direction.DOWN));

        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.broken()).isEmpty();
        assertThat(networks.networkOf(new BlockPos(1, 0, 0))).isEmpty();
        assertThat(networks.incoming(new BlockPos(1, 0, 0))).isEmpty();
    }
}
