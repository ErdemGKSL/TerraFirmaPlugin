/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-MECH-01: the nodes of TFC's five blocks, the gearbox hammer, the creative motor and the state-driven world. */
class MechanicalBlockTest {
    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Test
    void nodesFollowTfcBlockEntities() {
        assertThat(MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X).node(p(0, 0, 0)).connections())
            .containsExactlyInAnyOrder(WEST, EAST);
        assertThat(MechanicalBlock.State.axle(MechanicalBlock.ENCASED_AXLE, RotationAxis.Y).node(p(0, 0, 0)).kind())
            .isEqualTo(NodeKind.AXLE);
        assertThat(MechanicalBlock.State.clutch(RotationAxis.Z, false).node(p(0, 0, 0)).connections())
            .containsExactlyInAnyOrder(NORTH, SOUTH);
        assertThat(MechanicalBlock.State.clutch(RotationAxis.Z, true).node(p(0, 0, 0)).connections()).isEmpty();
        RotationNode motor = MechanicalBlock.State.motor(RotationAxis.X, 8).node(p(0, 0, 0));
        assertThat(motor.kind()).isEqualTo(NodeKind.SOURCE);
        assertThat(motor.sourceRotation().direction()).isEqualTo(EAST);
        assertThat(motor.sourceRotation().speed()).isEqualTo(CreativeMotor.MAX_SPEED);
    }

    /** Golden values of CreativeRotationBlockEntity: 30 RPM at step 8, eight steps each way. */
    @Test
    void creativeMotorGolden() {
        assertThat(CreativeMotor.MAX_SPEED).isEqualTo((float) (Math.PI * 2) / 40f);
        assertThat(CreativeMotor.rpm(CreativeMotor.MAX_SPEED)).isCloseTo(30f, org.assertj.core.data.Offset.offset(1e-4f));
        assertThat(CreativeMotor.speed(4)).isEqualTo(4 * CreativeMotor.LERP_SPEED);
        assertThat(CreativeMotor.speed(-12)).isEqualTo(-CreativeMotor.MAX_SPEED);
        // x motor: the interactable axis is z; south increments, north decrements, the rest pass.
        assertThat(CreativeMotor.stepChange(RotationAxis.X, SOUTH)).isEqualTo(1);
        assertThat(CreativeMotor.stepChange(RotationAxis.X, NORTH)).isEqualTo(-1);
        assertThat(CreativeMotor.stepChange(RotationAxis.X, EAST)).isZero();
        assertThat(CreativeMotor.stepChange(RotationAxis.Y, EAST)).isEqualTo(1);
        assertThat(CreativeMotor.stepChange(RotationAxis.Z, WEST)).isEqualTo(-1);
        assertThat(CreativeMotor.stepChange(RotationAxis.Z, UP)).isZero();
        assertThat(CreativeMotor.click(8, RotationAxis.Z, EAST)).isEqualTo(8);
    }

    /** GearBoxBlock.canEnable and useItemOn: one axis must stay free; sneaking toggles the opposite face. */
    @Test
    void gearBoxHammer() {
        assertThat(GearBoxRules.canEnable(EnumSet.of(NORTH, EAST), UP)).isFalse();
        assertThat(GearBoxRules.canEnable(EnumSet.of(NORTH, SOUTH, EAST), WEST)).isTrue();
        GearBoxRules.Toggle t = GearBoxRules.toggle(Set.of(), NORTH, true);
        assertThat(t.outcome()).isEqualTo(GearBoxRules.Outcome.ENABLED);
        assertThat(t.faces()).containsExactly(SOUTH);
        assertThat(GearBoxRules.toggle(Set.of(SOUTH, EAST), UP, false).outcome()).isEqualTo(GearBoxRules.Outcome.REFUSED);
        assertThat(GearBoxRules.toggle(Set.of(SOUTH, EAST), EAST, false).faces()).containsExactly(SOUTH);
    }

    /** The done criterion's layout: motor, five axles, gearbox turning the line 90 degrees, all at the motor's speed. */
    @Test
    void motorDrivesAxleLineAndGearbox() {
        RotationWorld w = new RotationWorld();
        assertThat(w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.X, 0))).isEmpty();
        for (int x = 1; x <= 5; x++) {
            assertThat(w.put(p(x, 0, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X))).isEmpty();
        }
        assertThat(w.put(p(6, 0, 0), MechanicalBlock.State.gearBox(Set.of(WEST, SOUTH)))).isEmpty();
        assertThat(w.put(p(6, 0, 1), MechanicalBlock.State.axle(MechanicalBlock.ENCASED_AXLE, RotationAxis.Z))).isEmpty();
        assertThat(w.readout(p(6, 0, 1)).orElseThrow().rpm()).isZero();
        assertThat(w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.X, 8))).isEmpty();
        for (BlockPos pos : List.of(p(1, 0, 0), p(5, 0, 0), p(6, 0, 0), p(6, 0, 1))) {
            RotationWorld.Readout r = w.readout(pos).orElseThrow();
            assertThat(r.network()).hasValue(0);
            assertThat(r.rpm()).isCloseTo(30f, org.assertj.core.data.Offset.offset(1e-4f));
        }
        // GearBoxBlockEntity: west source face, east hand, exit south is perpendicular -> south kept (source face != hand).
        assertThat(w.readout(p(6, 0, 1)).orElseThrow().incoming().orElseThrow().direction()).isEqualTo(SOUTH);
        assertThat(w.networkCount()).isEqualTo(1);
    }

    /** AxleNode.MAX_AXLE_LENGTH: the sixth axle (encased counts) breaks; removing it leaves no invalid block. */
    @Test
    void sixthAxleBreaks() {
        RotationWorld w = new RotationWorld();
        w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.X, 8));
        for (int x = 1; x <= 5; x++) {
            w.put(p(x, 0, 0), MechanicalBlock.State.axle(x == 3 ? MechanicalBlock.ENCASED_AXLE : MechanicalBlock.AXLE,
                RotationAxis.X));
        }
        assertThat(w.put(p(6, 0, 0), MechanicalBlock.State.clutch(RotationAxis.X, false))).containsExactly(p(6, 0, 0));
        assertThat(w.isInvalid(p(6, 0, 0))).isTrue();
        // TFC performNetworkAction: an invalid block does nothing until it is broken.
        assertThat(w.put(p(6, 0, 0), MechanicalBlock.State.clutch(RotationAxis.X, true))).isEmpty();
        w.remove(p(6, 0, 0));
        assertThat(w.isInvalid(p(6, 0, 0))).isFalse();
        assertThat(w.readout(p(6, 0, 0))).isEmpty();
    }

    /** ClutchBlockEntity.updateConnections: powering the clutch cuts the line behind it; unpowering joins it again. */
    @Test
    void clutchCutsAndRejoins() {
        RotationWorld w = new RotationWorld();
        w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.Z, 8));
        w.put(p(0, 0, 1), MechanicalBlock.State.clutch(RotationAxis.Z, false));
        w.put(p(0, 0, 2), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Z));
        assertThat(w.readout(p(0, 0, 2)).orElseThrow().network()).hasValue(0);
        assertThat(w.put(p(0, 0, 1), MechanicalBlock.State.clutch(RotationAxis.Z, true))).isEmpty();
        assertThat(w.readout(p(0, 0, 2)).orElseThrow().network()).isEmpty();
        assertThat(w.readout(p(0, 0, 1)).orElseThrow().network()).isEmpty();
        w.put(p(0, 0, 1), MechanicalBlock.State.clutch(RotationAxis.Z, false));
        assertThat(w.readout(p(0, 0, 2)).orElseThrow().network()).hasValue(0);
    }

    /** Unloading the motor's chunk disconnects the line; loading it again reconnects (TFC REMOVE then ADD_SOURCE). */
    @Test
    void unloadAndReload() {
        RotationWorld w = new RotationWorld();
        w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.Y, 3));
        w.put(p(0, 1, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Y));
        w.remove(p(0, 0, 0));
        assertThat(w.readout(p(0, 1, 0)).orElseThrow().network()).isEmpty();
        w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.Y, 3));
        assertThat(w.readout(p(0, 1, 0)).orElseThrow().incoming().orElseThrow().direction()).isEqualTo(UP);
        assertThat(w.readout(p(0, 1, 0)).orElseThrow().parent()).contains(DOWN);
    }

    /** PAR-MECH-20: BladedAxleBlockEntity is an AxleBlockEntity: the axle's node, turning with the line, counted in its length. */
    @Test
    void bladedAxleIsAnAxle() {
        assertThat(MechanicalBlock.BLADED_AXLE.kind()).isEqualTo(NodeKind.AXLE);
        assertThat(MechanicalBlock.State.axle(MechanicalBlock.BLADED_AXLE, RotationAxis.X).node(p(0, 0, 0)))
            .isEqualTo(MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X).node(p(0, 0, 0)));
        RotationWorld world = new RotationWorld();
        world.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.X, 8));
        world.put(p(1, 0, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        assertThat(world.put(p(2, 0, 0), MechanicalBlock.State.axle(MechanicalBlock.BLADED_AXLE, RotationAxis.X))).isEmpty();
        RotationWorld.Readout r = world.readout(p(2, 0, 0)).orElseThrow();
        assertThat(r.block()).isEqualTo(MechanicalBlock.BLADED_AXLE);
        assertThat(r.rpm()).isCloseTo(30f, org.assertj.core.data.Offset.offset(1e-4f));
        assertThat(r.incoming().orElseThrow().direction()).isEqualTo(EAST);
        // three more plain axles make six in a row: the sixth breaks, the bladed one counted
        for (int x = 3; x <= 5; x++) {
            assertThat(world.put(p(x, 0, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X))).isEmpty();
        }
        assertThat(world.put(p(6, 0, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X))).containsExactly(p(6, 0, 0));
    }
}
