/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.rotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpinVisualServiceTest {
    private static final float RPM30 = (float) (2 * Math.PI * 30 / 1200);
    private static final UUID WORLD = new UUID(1, 1);
    private static final UUID ALICE = new UUID(2, 1);
    private static final UUID BOB = new UUID(2, 2);
    private static final BlockPos POS = new BlockPos(10, 64, 10);

    private final List<String> log = new ArrayList<>();
    private final List<RotationAnglesSpin.Update> updates = new ArrayList<>();
    private SpinVisualService service;

    @BeforeEach
    void setUp() {
        AtomicInteger ids = new AtomicInteger(1000);
        service = new SpinVisualService(new SpinSink() {
            @Override
            public void spawn(UUID viewer, UUID world, BlockPos pos, RotationAxis axis, int entityId, RotationAnglesSpin.Update first) {
                log.add("spawn " + viewer.getLeastSignificantBits() + " " + entityId + " " + axis);
                updates.add(first);
            }

            @Override
            public void update(UUID viewer, int entityId, RotationAnglesSpin.Update update) {
                log.add("update " + viewer.getLeastSignificantBits() + " " + entityId);
                updates.add(update);
            }

            @Override
            public void remove(UUID viewer, int entityId) {
                log.add("remove " + viewer.getLeastSignificantBits() + " " + entityId);
            }
        }, ids::getAndIncrement);
    }

    private void run(long from, long to) {
        for (long t = from; t <= to; t++) {
            service.tick(t);
        }
    }

    @Test
    void oneSpeedChangeSendsOnePacketThenOnePerSegmentNotPerTick() {
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.EAST, 0f, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        assertThat(service.packets()).isEqualTo(1); // the spawn of a resting shaft
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.EAST, RPM30, 0, 10);
        assertThat(service.packets()).isEqualTo(2); // the speed change: exactly one packet
        run(10, 209);
        // 200 ticks at 30 rpm: segments of 37 ticks, so 5 refreshes inside the window (47, 84, 121, 158, 195)
        assertThat(service.packets()).isEqualTo(2 + 5);
    }

    @Test
    void packetsScaleWithViewersNotWithTicks() {
        service.setRotation(WORLD, POS, RotationAxis.Z, Direction.SOUTH, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        service.addViewer(WORLD, POS, BOB, 0);
        long before = service.packets();
        run(0, 369);
        assertThat(service.packets() - before).isEqualTo(2L * 9);
    }

    @Test
    void stoppingSnapsOnceAndStopsRefreshing() {
        service.setRotation(WORLD, POS, RotationAxis.Y, Direction.UP, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        service.setRotation(WORLD, POS, RotationAxis.Y, Direction.UP, 0f, RPM30 * 20, 20);
        long at = service.packets();
        assertThat(updates.getLast().duration()).isZero();
        run(20, 2000);
        assertThat(service.packets()).isEqualTo(at); // no refresh of a resting shaft
    }

    @Test
    void speedChangeKeepsTheAngleContinuous() {
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.WEST, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        // the source angle at tick 20 is 20 * RPM30; the speed doubles there
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.WEST, 2 * RPM30, 20 * RPM30, 20);
        RotationAnglesSpin.Update u = updates.getLast();
        double expectedStart = RotationAnglesSpin.rate(Direction.WEST, 1f) * 20 * RPM30;
        double expectedEnd = expectedStart + RotationAnglesSpin.rate(Direction.WEST, 2 * RPM30) * u.duration();
        assertThat(u.phi()).isCloseTo(expectedEnd, within(1e-6));
    }

    @Test
    void directionOfTurnFollowsTheHandedness() {
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.EAST, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        double eastPhi = updates.getLast().phi();
        service.removeShaft(WORLD, POS);
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.WEST, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        assertThat(updates.getLast().phi()).isCloseTo(-eastPhi, within(1e-9));
    }

    @Test
    void lateViewerSpawnsAtTheCurrentAngle() {
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.WEST, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 1000);
        RotationAnglesSpin.Update u = updates.getLast();
        long d = u.duration();
        assertThat(u.phi()).isCloseTo((double) RPM30 * (1000 + d), within(1e-6));
    }

    @Test
    void removingTheShaftOrViewerRemovesTheDisplayAndEndsRefreshes() {
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.WEST, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        service.addViewer(WORLD, POS, BOB, 0);
        service.removeViewer(WORLD, POS, BOB);
        assertThat(log).anyMatch(l -> l.startsWith("remove 2 "));
        service.removeShaft(WORLD, POS);
        assertThat(log).filteredOn(l -> l.startsWith("remove")).hasSize(2);
        long at = service.packets();
        run(0, 1000);
        assertThat(service.packets()).isEqualTo(at);
        assertThat(service.shafts()).isZero();
    }

    @Test
    void forgetViewerSendsNothing() {
        service.setRotation(WORLD, POS, RotationAxis.X, Direction.WEST, RPM30, 0, 0);
        service.addViewer(WORLD, POS, ALICE, 0);
        service.forgetViewer(ALICE);
        log.clear();
        run(0, 500);
        assertThat(log).isEmpty();
        assertThat(service.viewers(WORLD, POS)).isEmpty();
    }
}
