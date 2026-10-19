/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft) @ c3b4d9536. Golden values are derived by hand
 * from the TFC source named in each test's Javadoc; TFC has no data file for these numbers.
 */
package dev.tfp.core.power;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.rotation.NodeKind;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.rotation.RotationNetworks;
import dev.tfp.core.rotation.RotationNode;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransmissionTest {

    private static final float SPEED = 0.05f;

    /**
     * TFC {@code Node.ofAxis} (Node.java lines 28-34): an X shaft connects on EAST and WEST, a Z shaft on NORTH and SOUTH. The positive
     * face is {@code Direction.fromAxisAndDirection(axis, POSITIVE)}, which is EAST for X and SOUTH for Z (the water wheel and windmill
     * constructors, lines 190 and 139).
     */
    @Test
    void shaftFacesAndPositiveFaceMatchTfc() {
        assertThat(ShaftAxis.X.faces()).containsExactlyInAnyOrder(Direction.EAST, Direction.WEST);
        assertThat(ShaftAxis.Z.faces()).containsExactlyInAnyOrder(Direction.NORTH, Direction.SOUTH);
        assertThat(ShaftAxis.X.positiveFace()).isEqualTo(Direction.EAST);
        assertThat(ShaftAxis.Z.positiveFace()).isEqualTo(Direction.SOUTH);
    }

    /**
     * TFC {@code ClutchBlockEntity.updateConnections} (lines 45-57): unpowered, the clutch connects on both faces of its axis; powered,
     * it clears its connections, which cuts the shaft.
     */
    @Test
    void clutchConnectsOnlyWhenUnpowered() {
        assertThat(Transmission.clutchConnections(ShaftAxis.X, false)).containsExactlyInAnyOrder(Direction.EAST, Direction.WEST);
        assertThat(Transmission.clutchConnections(ShaftAxis.Z, true)).isEmpty();
    }

    /**
     * A water wheel or windmill at the origin on the Z axis is a SOURCE on NORTH and SOUTH, emitting SOUTH at the given speed (the
     * positive face, TFC constructor lines 190 and 139).
     */
    @Test
    void sourceNodeEmitsOnThePositiveFace() {
        final RotationNode node = Transmission.sourceNode(new BlockPos(0, 0, 0), ShaftAxis.Z, SPEED);
        assertThat(node.kind()).isEqualTo(NodeKind.SOURCE);
        assertThat(node.connections()).containsExactlyInAnyOrder(Direction.NORTH, Direction.SOUTH);
        assertThat(node.sourceRotation()).isEqualTo(new Rotation(Direction.SOUTH, SPEED, 0));
    }

    /**
     * The facts plug into the existing solver: an X windmill at the origin, an unpowered clutch at x=1 (an AXLE with both X faces),
     * and a sink at x=2 on WEST. The sink reads EAST at SPEED with one axle counted, as in RotationGoldenTest.
     */
    @Test
    void unpoweredClutchPassesPowerToTheSink() {
        final RotationNetworks networks = new RotationNetworks();
        networks.addSource(Transmission.sourceNode(new BlockPos(0, 0, 0), ShaftAxis.X, SPEED));
        networks.add(RotationNode.of(new BlockPos(1, 0, 0), NodeKind.AXLE,
            Transmission.clutchConnections(ShaftAxis.X, false)));
        networks.add(RotationNode.of(new BlockPos(2, 0, 0), NodeKind.SINK, Set.of(Direction.WEST)));

        assertThat(networks.incoming(new BlockPos(2, 0, 0))).contains(new Rotation(Direction.EAST, SPEED, 1));
    }

    /**
     * The same layout with the clutch powered: its connections are empty, so the sink at x=2 is not reached and has no incoming
     * rotation (TFC clutch clears its connections, lines 50-53).
     */
    @Test
    void poweredClutchCutsThePowerAtTheSink() {
        final RotationNetworks networks = new RotationNetworks();
        networks.addSource(Transmission.sourceNode(new BlockPos(0, 0, 0), ShaftAxis.X, SPEED));
        networks.add(RotationNode.of(new BlockPos(1, 0, 0), NodeKind.AXLE,
            Transmission.clutchConnections(ShaftAxis.X, true)));
        networks.add(RotationNode.of(new BlockPos(2, 0, 0), NodeKind.SINK, Set.of(Direction.WEST)));

        assertThat(networks.incoming(new BlockPos(2, 0, 0))).isEmpty();
    }
}
