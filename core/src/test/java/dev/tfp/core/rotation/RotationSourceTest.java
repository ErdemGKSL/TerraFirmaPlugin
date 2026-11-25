/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.rotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.Transmission;
import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** PAR-MECH-17: the water wheel and windmill as sources of the state-driven rotation world. */
class RotationSourceTest {
    private static final Offset<Float> EPS = Offset.offset(1e-5f);

    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** The source state's node is TFC's SourceNode, the same as Transmission.sourceNode. */
    @Test
    void sourceNodeMatchesTransmission() {
        RotationNode wheel = MechanicalBlock.State.source(MechanicalBlock.WATER_WHEEL, RotationAxis.Z, -0.05f).node(p(1, 2, 3));
        assertThat(wheel).isEqualTo(Transmission.sourceNode(p(1, 2, 3), ShaftAxis.Z, -0.05f));
        assertThat(MechanicalBlock.WINDMILL.kind()).isEqualTo(NodeKind.SOURCE);
        assertThatThrownBy(() -> MechanicalBlock.State.source(MechanicalBlock.WINDMILL, RotationAxis.Y, 0f))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MechanicalBlock.State.source(MechanicalBlock.AXLE, RotationAxis.X, 0f))
            .isInstanceOf(IllegalArgumentException.class);
        // other blocks carry no speed
        assertThat(new MechanicalBlock.State(MechanicalBlock.AXLE, RotationAxis.X, false, Set.of(), 0, 3f).speed()).isZero();
    }

    /** A water wheel at -MAX_SPEED drives an axle line: every axle gets the wheel's hand (east) and speed (-15 rpm). */
    @Test
    void waterWheelDrivesAxleLine() {
        RotationWorld world = new RotationWorld();
        assertThat(world.put(p(0, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WATER_WHEEL, RotationAxis.X,
            -WaterWheel.MAX_SPEED))).isEmpty();
        for (int x = 1; x <= 4; x++) {
            assertThat(world.put(p(x, 64, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X))).isEmpty();
        }
        long id = world.readout(p(0, 64, 0)).orElseThrow().network().getAsLong();
        assertThat(world.members(id)).hasSize(5);
        RotationWorld.Readout last = world.readout(p(4, 64, 0)).orElseThrow();
        Rotation in = last.incoming().orElseThrow();
        assertThat(in.direction()).isEqualTo(Direction.EAST);
        assertThat(in.speed()).isEqualTo(-WaterWheel.MAX_SPEED);
        assertThat(last.rpm()).isCloseTo(-15f, EPS);
        assertThat(last.parent()).contains(Direction.WEST);
    }

    /** A windmill at 2 pi / 80 drives a gearbox and the axle behind it; a speed change keeps the network id. */
    @Test
    void windmillDrivesGearBoxAndSpeedChangeKeepsNetwork() {
        float speed = (float) (2 * Math.PI) / 80f;
        RotationWorld world = new RotationWorld();
        world.put(p(0, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WINDMILL, RotationAxis.Z, speed));
        world.put(p(0, 64, 1), MechanicalBlock.State.gearBox(Set.of(Direction.NORTH, Direction.EAST)));
        world.put(p(1, 64, 1), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        RotationWorld.Readout axle = world.readout(p(1, 64, 1)).orElseThrow();
        assertThat(axle.network()).isPresent();
        assertThat(Math.abs(axle.incoming().orElseThrow().speed())).isEqualTo(speed);
        assertThat(axle.rpm()).isNotZero();
        long id = axle.network().getAsLong();

        world.put(p(0, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WINDMILL, RotationAxis.Z, speed / 2));
        RotationWorld.Readout after = world.readout(p(1, 64, 1)).orElseThrow();
        assertThat(after.network()).hasValue(id);
        assertThat(Math.abs(after.incoming().orElseThrow().speed())).isEqualTo(speed / 2);
        assertThat(world.networkCount()).isEqualTo(1);

        // the windmill goes back to its axle (last blade out): the line becomes a plain disconnected axle line
        world.remove(p(0, 64, 0));
        world.put(p(0, 64, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Z));
        assertThat(world.readout(p(1, 64, 1)).orElseThrow().network()).isEmpty();
        assertThat(world.networkCount()).isZero();
    }

    /** An axle replaced by a windmill at the same position (a blade used on it) becomes the source. */
    @Test
    void axleTurnsIntoWindmill() {
        RotationWorld world = new RotationWorld();
        world.put(p(0, 64, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        world.put(p(1, 64, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        assertThat(world.put(p(0, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WINDMILL, RotationAxis.X, 0.01f)))
            .isEmpty();
        assertThat(world.readout(p(1, 64, 0)).orElseThrow().incoming().orElseThrow().speed()).isEqualTo(0.01f);
    }

    /** Two sources facing each other: the second is rejected and must break (TFC ADD_SOURCE fails, destroyIfInvalid). */
    @Test
    void secondSourceOnALineIsRejected() {
        RotationWorld world = new RotationWorld();
        world.put(p(0, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WATER_WHEEL, RotationAxis.X, 0.02f));
        world.put(p(1, 64, 0), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        assertThat(world.put(p(2, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WINDMILL, RotationAxis.X, 0.01f)))
            .containsExactly(p(2, 64, 0));
        assertThat(world.isInvalid(p(2, 64, 0))).isTrue();
        // its later reports do nothing until it is removed
        assertThat(world.put(p(2, 64, 0), MechanicalBlock.State.source(MechanicalBlock.WINDMILL, RotationAxis.X, 0.03f)))
            .isEmpty();
        assertThat(world.readout(p(1, 64, 0)).orElseThrow().incoming().orElseThrow().speed()).isEqualTo(0.02f);
    }
}
