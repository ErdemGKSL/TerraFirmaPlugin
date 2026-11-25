/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.rotation.CreativeMotor;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** PAR-MECH-12: the spinning shafts' packets, viewers, phases and timing against the PAR-MECH-02 planner. */
class SpinShaftsTest {
    static final UUID WORLD = new UUID(1, 1);
    static final UUID BOT = new UUID(2, 2);
    static final UUID OTHER = new UUID(3, 3);
    static final BlockPos A = new BlockPos(1, 64, 1);
    static final BlockPos B = new BlockPos(2, 64, 1);
    static final float FULL = CreativeMotor.speed(8);

    record Sent(String kind, UUID viewer, int id, double half, int duration) {
    }

    static final class Wire implements SpinWire {
        final List<Sent> sent = new ArrayList<>();
        final List<String> items = new ArrayList<>();

        @Override
        public void spawn(UUID viewer, int entityId, BlockPos pos, String item, RotationAxis axis, double halfAngle) {
            sent.add(new Sent("spawn", viewer, entityId, halfAngle, 0));
            items.add(item);
        }

        @Override
        public void turn(UUID viewer, int entityId, RotationAxis axis, double halfAngle, int duration) {
            sent.add(new Sent("turn", viewer, entityId, halfAngle, duration));
        }

        @Override
        public void remove(UUID viewer, int... entityIds) {
            for (int id : entityIds) {
                sent.add(new Sent("remove", viewer, id, 0, 0));
            }
        }

        List<Sent> of(String kind) {
            return sent.stream().filter(s -> s.kind.equals(kind)).toList();
        }
    }

    final Wire wire = new Wire();
    final AtomicInteger ids = new AtomicInteger(100);
    final Map<BlockPos, Consumer<Optional<String>>> waiting = new HashMap<>();

    SpinShafts immediate() {
        return new SpinShafts(wire, ids::incrementAndGet, (w, pos, done) -> done.accept(Optional.of("tfp:render/rotation/wood/axle/oak")));
    }

    static ShaftReport turning(BlockPos pos, Direction hand, float speed) {
        return new ShaftReport(pos, RotationAxis.X, hand, speed, 7L);
    }

    void ticks(SpinShafts s, int n) {
        for (int i = 0; i < n; i++) {
            s.tick();
        }
    }

    @Test
    void aShaftAtRestSpawnsOnceWithItsWoodAndNoSegment() {
        SpinShafts s = immediate();
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        s.shafts(WORLD, List.of(ShaftReport.resting(A, RotationAxis.X)), List.of());
        assertThat(wire.of("spawn")).hasSize(1);
        assertThat(wire.sent.getFirst().half()).isZero();
        assertThat(wire.items).containsExactly("tfp:render/rotation/wood/axle/oak");
        ticks(s, 400);
        assertThat(wire.sent).hasSize(1);
    }

    @Test
    void aTurningShaftSpawnsThenFollowsWithTheSegmentAndRefreshesOnTheDueTick() {
        SpinShafts s = immediate();
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        s.shafts(WORLD, List.of(turning(A, Direction.EAST, FULL)), List.of());
        RotationAnglesSpin.Update first = RotationAnglesSpin.plan(new RotationAnglesSpin.Track(0, 0, RotationAnglesSpin.rate(Direction.EAST, FULL)), 0);
        assertThat(first.duration()).isEqualTo(37);
        assertThat(wire.of("spawn")).singleElement().satisfies(x -> assertThat(x.half()).isZero());
        s.tick(); // the display has its render state now: the rest of the first segment
        assertThat(wire.of("turn")).singleElement().satisfies(x -> {
            assertThat(x.duration()).isEqualTo(36);
            assertThat(x.half()).isCloseTo(first.halfAngle(), within(1e-9));
        });
        ticks(s, 35);
        assertThat(wire.of("turn")).hasSize(1);
        s.tick(); // tick 37: the next segment
        assertThat(wire.of("turn")).hasSize(2);
        assertThat(wire.of("turn").get(1).duration()).isEqualTo(37);
        // 200 ticks of 30 rpm: the planner's segment count (the first segment's packet goes one tick after the spawn)
        ticks(s, 200 - 37);
        assertThat(wire.of("turn")).hasSize(RotationAnglesSpin.packetsFor(RotationAnglesSpin.rate(Direction.EAST, FULL), 200));
    }

    @Test
    void aJoiningPlayerSeesTheShaftAlreadyTurning() {
        SpinShafts s = immediate();
        s.shafts(WORLD, List.of(turning(A, Direction.EAST, FULL)), List.of());
        ticks(s, 100);
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        double expected = RotationAnglesSpin.rate(Direction.EAST, FULL) * 100;
        assertThat(wire.of("spawn")).singleElement().satisfies(x -> assertThat(x.half() * 2).isCloseTo(expected, within(1e-6)));
        assertThat(s.angle(WORLD, A)).hasValueSatisfying(a -> assertThat(a).isCloseTo(expected, within(1e-9)));
    }

    @Test
    void theShaftsOfOneNetworkShareThePhaseAndOppositeHandsTurnOppositeWays() {
        SpinShafts s = immediate();
        s.shafts(WORLD, List.of(turning(A, Direction.EAST, FULL)), List.of());
        ticks(s, 50);
        // B joins the same network later, in the other hand (the other side of a gearbox)
        s.shafts(WORLD, List.of(turning(B, Direction.WEST, FULL)), List.of());
        ticks(s, 20);
        double a = s.angle(WORLD, A).orElseThrow();
        double b = s.angle(WORLD, B).orElseThrow();
        assertThat(a).isCloseTo(-b, within(1e-9));
        assertThat(a).isCloseTo(RotationAnglesSpin.rate(Direction.EAST, FULL) * 70, within(1e-6));
        assertThat(s.networks()).isEqualTo(1);
    }

    @Test
    void stepZeroStopsTheShaftWhereItIs() {
        SpinShafts s = immediate();
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        s.shafts(WORLD, List.of(turning(A, Direction.EAST, FULL)), List.of());
        ticks(s, 10);
        double at = s.angle(WORLD, A).orElseThrow();
        wire.sent.clear();
        s.shafts(WORLD, List.of(turning(A, Direction.EAST, 0f)), List.of());
        assertThat(wire.of("turn")).singleElement().satisfies(x -> {
            assertThat(x.duration()).isZero();
            assertThat(x.half() * 2).isCloseTo(at, within(1e-9));
        });
        ticks(s, 300);
        assertThat(wire.sent).hasSize(1);
        assertThat(s.angle(WORLD, A)).hasValueSatisfying(v -> assertThat(v).isCloseTo(at, within(1e-9)));
    }

    @Test
    void viewersFollowTheChunkPacketsAndTheWorld() {
        SpinShafts s = immediate();
        s.shafts(WORLD, List.of(ShaftReport.resting(A, RotationAxis.X), ShaftReport.resting(new BlockPos(40, 64, 1), RotationAxis.Z)),
            List.of());
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        assertThat(s.viewers(WORLD, A)).containsExactly(BOT);
        assertThat(s.viewers(WORLD, new BlockPos(40, 64, 1))).isEmpty();
        s.chunkUnloaded(BOT, 0, 0);
        assertThat(s.viewers(WORLD, A)).isEmpty();
        assertThat(wire.of("remove")).hasSize(1);
        s.chunkSent(BOT, 0, 0);
        s.viewerWorld(BOT, new UUID(9, 9)); // respawn in another world: the client dropped everything
        assertThat(s.viewers(WORLD, A)).isEmpty();
        s.chunkSent(BOT, 0, 0); // the other world's chunk 0,0 has no shaft
        assertThat(wire.of("spawn")).hasSize(2);
    }

    @Test
    void aShaftPlacedInASentChunkAppearsForItsViewersAndBreakingRemovesIt() {
        SpinShafts s = immediate();
        s.viewerWorld(BOT, WORLD);
        s.viewerWorld(OTHER, WORLD);
        s.chunkSent(BOT, 0, 0);
        s.shafts(WORLD, List.of(ShaftReport.resting(A, RotationAxis.Y)), List.of());
        assertThat(s.viewers(WORLD, A)).containsExactly(BOT);
        s.shafts(WORLD, List.of(), List.of(A));
        assertThat(wire.of("remove")).singleElement().satisfies(x -> assertThat(x.viewer()).isEqualTo(BOT));
        assertThat(s.shafts()).isZero();
    }

    @Test
    void theShaftWaitsForItsItemAndAQuitSendsNothing() {
        SpinShafts s = new SpinShafts(wire, ids::incrementAndGet, (w, pos, done) -> waiting.put(pos, done));
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        s.shafts(WORLD, List.of(turning(A, Direction.EAST, FULL)), List.of());
        assertThat(wire.sent).isEmpty();
        ticks(s, 5);
        waiting.get(A).accept(Optional.of("tfp:render/rotation/wood/axle/ash"));
        assertThat(wire.of("spawn")).singleElement()
            .satisfies(x -> assertThat(x.half() * 2).isCloseTo(RotationAnglesSpin.rate(Direction.EAST, FULL) * 5, within(1e-6)));
        assertThat(wire.items).containsExactly("tfp:render/rotation/wood/axle/ash");
        int before = wire.sent.size();
        s.quit(BOT);
        assertThat(wire.sent).hasSize(before);
        assertThat(s.viewers(WORLD, A)).isEmpty();
        ticks(s, 100);
        assertThat(wire.sent).hasSize(before);
    }

    @Test
    void aShaftThatIsGoneBeforeItsItemIsReadStaysHidden() {
        SpinShafts s = new SpinShafts(wire, ids::incrementAndGet, (w, pos, done) -> waiting.put(pos, done));
        s.viewerWorld(BOT, WORLD);
        s.chunkSent(BOT, 0, 0);
        s.shafts(WORLD, List.of(ShaftReport.resting(A, RotationAxis.X)), List.of());
        s.shafts(WORLD, List.of(), List.of(A));
        waiting.get(A).accept(Optional.of("tfp:render/rotation/wood/axle/oak"));
        assertThat(wire.sent).isEmpty();
        assertThat(s.networks()).isZero();
    }
}
