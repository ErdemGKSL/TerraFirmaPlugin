/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.machine.RotationAnglesSpin;
import dev.tfp.core.machine.RotationAnglesSpin.Track;
import dev.tfp.core.rotation.CreativeMotor;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** PAR-MECH-26: the crankshafts' displays follow the network rotation and TFC's shaft movement. */
class SpinCranksTest {
    static final UUID WORLD = new UUID(5, 5);
    static final UUID BOT = new UUID(6, 6);
    static final BlockPos BASE = new BlockPos(3, 64, 3);
    static final float FULL = CreativeMotor.speed(8);
    static final String OAK = "tfp:render/rotation/wood/axle/oak";

    record Sent(String kind, UUID viewer, int id, BlockPos pos, String item, CrankParts.Pose pose, int duration) {
    }

    static final class Wire implements CrankWire {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public void spawnPart(UUID viewer, int entityId, BlockPos pos, String item, CrankParts.Pose pose) {
            sent.add(new Sent("spawn", viewer, entityId, pos, item, pose, 0));
        }

        @Override
        public void movePart(UUID viewer, int entityId, CrankParts.Pose pose, int duration) {
            sent.add(new Sent("move", viewer, entityId, null, null, pose, duration));
        }

        @Override
        public void removeParts(UUID viewer, int... entityIds) {
            for (int id : entityIds) {
                sent.add(new Sent("remove", viewer, id, null, null, null, 0));
            }
        }

        List<Sent> of(String kind) {
            return sent.stream().filter(s -> s.kind.equals(kind)).toList();
        }
    }

    final Wire wire = new Wire();
    final AtomicInteger ids = new AtomicInteger(500);

    SpinCranks cranks(SpinCranks.Phases phases) {
        SpinCranks c = new SpinCranks(wire, ids::incrementAndGet, phases);
        c.viewerWorld(BOT, WORLD);
        c.chunkSent(BOT, BASE.chunkX(), BASE.chunkZ());
        return c;
    }

    static CrankReport turning(Direction facing, boolean shaft, float speed, String stub) {
        return new CrankReport(BASE, facing, shaft, false, true, Direction.WEST, speed, stub);
    }

    static void ticks(SpinCranks c, int n) {
        for (int i = 0; i < n; i++) {
            c.tick();
        }
    }

    @Test
    void theWheelAngleFollowsTheRotation() {
        SpinCranks c = cranks(SpinCranks.Phases.NONE);
        c.crank(WORLD, turning(Direction.NORTH, false, FULL, null));
        double unit = RotationAnglesSpin.rate(Direction.WEST, 1f);
        assertThat(c.wheelAngle(WORLD, BASE)).hasValueSatisfying(a -> assertThat(a).isCloseTo(0, within(1e-9)));
        ticks(c, 25);
        assertThat(c.wheelAngle(WORLD, BASE)).hasValueSatisfying(a -> assertThat(a).isCloseTo(unit * FULL * 25, within(1e-6)));
        // the spawn showed angle 0; the first segment (one tick later) targets the planner's angle for the wheel's line
        Sent spawn = wire.of("spawn").getFirst();
        assertThat(spawn.item()).isEqualTo(CrankParts.WHEEL_ITEM);
        assertThat(spawn.pos()).isEqualTo(BASE);
        Track wheel = new Track(0, 0, unit * FULL);
        RotationAnglesSpin.Update first = RotationAnglesSpin.plan(wheel, 0);
        Sent target = wire.of("move").getFirst();
        assertThat(target.duration()).isEqualTo(first.duration() - 1);
        assertThat(target.pose().left()).containsExactly(CrankParts.wheel(Direction.NORTH, first.halfAngle()).left());
        // the next segment goes out when the first ends, toward the angle of its own end
        ticks(c, first.duration());
        RotationAnglesSpin.Update second = RotationAnglesSpin.plan(wheel, first.nextTick());
        assertThat(wire.of("move").stream().anyMatch(s -> s.duration() == second.duration()
            && java.util.Arrays.equals(s.pose().right(), CrankParts.wheel(Direction.NORTH, second.halfAngle()).right()))).isTrue();
        // a speed change keeps the angle and turns on at the new rate
        double before = c.wheelAngle(WORLD, BASE).orElseThrow();
        c.crank(WORLD, turning(Direction.NORTH, false, FULL / 2, null));
        ticks(c, 10);
        assertThat(c.wheelAngle(WORLD, BASE).orElseThrow()).isCloseTo(before + unit * FULL / 2 * 10, within(1e-6));
    }

    @Test
    void theWheelTakesTheNetworkPhaseOfTheAxleOnItsSinkFace() {
        BlockPos sink = BASE.offset(CrankshaftRules.sinkFace(Direction.EAST));
        Track phase = new Track(0, 2.5, FULL);
        SpinCranks c = cranks((w, pos) -> pos.equals(sink) ? Optional.of(phase) : Optional.empty());
        ticks(c, 4);
        c.crank(WORLD, turning(Direction.EAST, false, FULL, OAK));
        assertThat(c.sourceAngle(WORLD, BASE)).hasValueSatisfying(a -> assertThat(a).isCloseTo(phase.phiAt(4), within(1e-9)));
        // the stub is spawned with the axle's item, turning with the wheel
        Sent stub = wire.of("spawn").stream().filter(s -> s.item().equals(OAK)).findFirst().orElseThrow();
        double half = c.wheelAngle(WORLD, BASE).orElseThrow() / 2;
        assertThat(stub.pose().left()).containsExactly(CrankParts.stub(Direction.EAST, half).left());
    }

    @Test
    void theShaftFollowsTfcsShaftMovement() {
        SpinCranks c = cranks(SpinCranks.Phases.NONE);
        c.crank(WORLD, turning(Direction.SOUTH, true, FULL, null));
        int step = SpinCranks.shaftStep(FULL);
        assertThat(step).isEqualTo(3); // 30 rpm: a turn in 40 ticks, 16 keyframes
        BlockPos shaft = BASE.offset(Direction.SOUTH);
        List<Sent> spawns = wire.of("spawn");
        assertThat(spawns).extracting(Sent::item).containsExactly(CrankParts.WHEEL_ITEM, CrankParts.SLIDER_ITEM,
            CrankParts.ARM_ITEM);
        assertThat(spawns.get(1).pos()).isEqualTo(shaft);
        CrankshaftRules.ShaftMovement rest = CrankParts.movement(Direction.WEST, 0, Direction.SOUTH);
        assertThat(spawns.get(1).pose().translation()).containsExactly(CrankParts.slider(Direction.SOUTH, rest).translation());
        int slider = spawns.get(1).id();
        int arm = spawns.get(2).id();
        for (int keyframe = 1; keyframe <= 5; keyframe++) {
            ticks(c, step);
            double source = FULL * step * (keyframe + 1); // each keyframe targets the angle one step ahead
            CrankshaftRules.ShaftMovement m = CrankshaftRules.shaftMovement(CrankshaftRules.realRotationAngle(
                CrankParts.nodeAngle(Direction.WEST, source), Direction.SOUTH));
            Sent s = last(slider);
            Sent a = last(arm);
            assertThat(s.duration()).isEqualTo(step);
            assertThat(s.pose().translation()).containsExactly(CrankParts.slider(Direction.SOUTH, m).translation());
            assertThat(a.pose().left()).containsExactly(CrankParts.arm(Direction.SOUTH, m).left());
        }
        assertThat(c.movement(WORLD, BASE)).hasValue(CrankParts.movement(Direction.WEST, FULL * c.now(), Direction.SOUTH));
    }

    @Test
    void outOfTheNetworkTheCrankRestsAtTfcsAngleZero() {
        SpinCranks c = cranks(SpinCranks.Phases.NONE);
        c.crank(WORLD, turning(Direction.WEST, true, FULL, OAK));
        ticks(c, 13);
        c.crank(WORLD, CrankReport.resting(BASE, Direction.WEST, true, false));
        assertThat(c.wheelAngle(WORLD, BASE).orElseThrow()).isCloseTo(0.0, within(1e-12));
        // the stub went (respawn without it) and nothing more goes out
        assertThat(wire.of("remove")).isNotEmpty();
        long packets = c.packets();
        ticks(c, 100);
        assertThat(c.packets()).isEqualTo(packets);
        assertThat(wire.sent.stream().skip(wire.sent.size() - 3).map(Sent::item)).containsExactly(CrankParts.WHEEL_ITEM,
            CrankParts.SLIDER_ITEM, CrankParts.ARM_ITEM);
    }

    @Test
    void viewersComeWithTheChunkAndTheBaseGoes() {
        SpinCranks c = new SpinCranks(wire, ids::incrementAndGet, SpinCranks.Phases.NONE);
        c.crank(WORLD, turning(Direction.NORTH, false, FULL, null));
        assertThat(wire.sent).isEmpty();
        c.viewerWorld(BOT, WORLD);
        c.chunkSent(BOT, BASE.chunkX(), BASE.chunkZ());
        assertThat(c.viewers(WORLD, BASE)).containsExactly(BOT);
        c.chunkUnloaded(BOT, BASE.chunkX(), BASE.chunkZ());
        assertThat(c.viewers(WORLD, BASE)).isEmpty();
        c.chunkSent(BOT, BASE.chunkX(), BASE.chunkZ());
        int removedBefore = wire.of("remove").size();
        c.remove(WORLD, BASE);
        assertThat(wire.of("remove")).hasSize(removedBefore + 5);
        assertThat(c.cranks()).isZero();
    }

    @Test
    void aPumpInFrontAddsTfcsAttachmentMovingWithTheSlider() {
        SpinCranks c = cranks(SpinCranks.Phases.NONE);
        c.crank(WORLD, new CrankReport(BASE, Direction.EAST, true, true, true, Direction.WEST, FULL, null));
        List<Sent> spawns = wire.of("spawn");
        assertThat(spawns).extracting(Sent::item).containsExactly(CrankParts.WHEEL_ITEM, CrankParts.SLIDER_ITEM,
            CrankParts.ARM_ITEM, CrankParts.PUMP_ITEM);
        assertThat(spawns.get(3).pose().translation()).containsExactly(spawns.get(1).pose().translation());
        ticks(c, SpinCranks.shaftStep(FULL));
        assertThat(last(spawns.get(3).id()).pose().translation()).containsExactly(last(spawns.get(1).id()).pose().translation());
        // no pump without the shaft
        assertThat(new CrankReport(BASE, Direction.EAST, false, true, true, Direction.WEST, FULL, null).pump()).isFalse();
    }

    @Test
    void theStubItemIsThePlainAxleOfTheWood() {
        assertThat(CrankFeed.stubItem(OAK)).hasValue(OAK);
        assertThat(CrankFeed.stubItem("tfp:render/rotation/wood/bladed_axle/oak")).hasValue(OAK);
        assertThat(CrankFeed.stubItem("tfp:render/other")).isEmpty();
        assertThat(CrankFeed.stubItem(null)).isEmpty();
    }

    private Sent last(int id) {
        List<Sent> of = wire.sent.stream().filter(s -> s.id() == id && s.kind().equals("move")).toList();
        return of.getLast();
    }
}
