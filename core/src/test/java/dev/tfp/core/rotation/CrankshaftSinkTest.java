/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.rotation;

import static dev.tfp.core.world.Direction.EAST;
import static dev.tfp.core.world.Direction.NORTH;
import static dev.tfp.core.world.Direction.SOUTH;
import static dev.tfp.core.world.Direction.WEST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * PAR-MECH-21: a crankshaft base in a rotation network. TFC {@code CrankshaftBlockEntity} adds the base as a {@code SinkNode} on
 * {@code FACING.getCounterClockWise()}; it reads the rotation of the block on that face and passes nothing on.
 */
class CrankshaftSinkTest {
    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** Motor (axis z) at z=0, axles at z=1..2, the crank at z=3 facing east (sink face north): it turns at the motor's speed. */
    private static RotationWorld line(MechanicalBlock.State crank) {
        RotationWorld w = new RotationWorld();
        w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.Z, 8));
        w.put(p(0, 0, 1), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Z));
        w.put(p(0, 0, 2), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Z));
        assertThat(w.put(p(0, 0, 3), crank)).isEmpty();
        return w;
    }

    @Test
    void baseJoinsOnItsCounterClockwiseFace() {
        RotationWorld w = line(MechanicalBlock.State.crankshaft(EAST));
        RotationWorld.Readout r = w.readout(p(0, 0, 3)).orElseThrow();
        assertThat(r.block()).isEqualTo(MechanicalBlock.CRANKSHAFT);
        assertThat(r.network()).isPresent();
        assertThat(r.network()).isEqualTo(w.readout(p(0, 0, 0)).orElseThrow().network());
        assertThat(r.parent()).contains(NORTH);
        assertThat(r.incoming().orElseThrow().direction()).isEqualTo(SOUTH);
        assertThat(r.rpm()).isCloseTo(30f, Offset.offset(1e-4f));
        assertThat(r.invalid()).isFalse();
    }

    @Test
    void otherFacingsDoNotConnect() {
        // west: sink face south (away from the line); north: west; south: east
        for (var facing : new dev.tfp.core.world.Direction[] {WEST, NORTH, SOUTH}) {
            RotationWorld w = line(MechanicalBlock.State.crankshaft(facing));
            RotationWorld.Readout r = w.readout(p(0, 0, 3)).orElseThrow();
            assertThat(r.network()).as(facing.name()).isEmpty();
            assertThat(r.incoming()).as(facing.name()).isEmpty();
        }
    }

    @Test
    void sinkFollowsTheNetworkAndPassesNothingOn() {
        RotationWorld w = line(MechanicalBlock.State.crankshaft(EAST));
        // an axle on the far side of the crank (z=4) gets nothing through the sink
        w.put(p(0, 0, 4), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Z));
        assertThat(w.readout(p(0, 0, 4)).orElseThrow().incoming()).isEmpty();
        // the motor's speed change reaches the crank
        w.put(p(0, 0, 0), MechanicalBlock.State.motor(RotationAxis.Z, 4));
        assertThat(w.readout(p(0, 0, 3)).orElseThrow().incoming().orElseThrow().speed()).isEqualTo(CreativeMotor.speed(4));
        // breaking an axle leaves the crank in no network
        w.remove(p(0, 0, 2));
        assertThat(w.readout(p(0, 0, 3)).orElseThrow().network()).isEmpty();
        assertThat(w.readout(p(0, 0, 3)).orElseThrow().incoming()).isEmpty();
        // the axle back: connected again
        w.put(p(0, 0, 2), MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.Z));
        assertThat(w.readout(p(0, 0, 3)).orElseThrow().incoming()).isPresent();
        // the crank removed: gone from the world
        w.remove(p(0, 0, 3));
        assertThat(w.readout(p(0, 0, 3))).isEmpty();
    }
}
