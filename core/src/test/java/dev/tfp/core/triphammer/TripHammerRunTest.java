/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.triphammer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.machine.RotationAngles;
import dev.tfp.core.machine.TripHammerRules;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** PAR-MECH-07: the trip hammer's batched ticks against TFC's per-tick serverTick. */
class TripHammerRunTest {
    /** A world with an item of {@code durability} points and an anvil that takes every hit; records the strike ticks. */
    static class FakeWorld implements TripHammerRun.World {
        int durability;
        boolean anvil = true;
        long tick;
        final List<Long> hits = new ArrayList<>();

        FakeWorld(int durability) {
            this.durability = durability;
        }

        @Override
        public boolean hasItem() {
            return durability > 0;
        }

        @Override
        public Optional<BooleanSupplier> anvil() {
            return anvil ? Optional.of(() -> {
                hits.add(tick);
                return true;
            }) : Optional.empty();
        }

        @Override
        public void damageItem() {
            durability--;
        }
    }

    /** TFC's serverTick one tick at a time, with the source's angle ticked first (RotationNetworkManager before the block entity). */
    private static List<Long> stepped(Rotation rotation, Direction facing, int ticks, FakeWorld world) {
        TripHammerRules.State state = TripHammerRules.State.NEW;
        float angle = 0f;
        for (int t = 1; t <= ticks; t++) {
            world.tick = t;
            angle = RotationAngles.advance(angle, rotation.speed());
            TripHammerRules.Tick tick = TripHammerRules.tick(state, rotation, angle, world.hasItem(), facing, world.anvil());
            state = tick.state();
            if (tick.itemDamaged()) {
                world.damageItem();
            }
        }
        return world.hits;
    }

    @Test
    void batchesStrikeOnTheSameTicksAsSteppedTicks() {
        for (float speed : new float[] {0.05f, 0.1f, (float) (Math.PI * 2) / 40f, -0.1f}) {
            for (Direction hand : new Direction[] {Direction.EAST, Direction.WEST}) {
                Rotation rotation = Rotation.of(hand, speed);
                // the facing whose clockwise side is the rotation's positive direction never breaks
                Direction positive = speed < 0 ? hand.opposite() : hand;
                Direction facing = positive == Direction.EAST ? Direction.NORTH : Direction.SOUTH;
                List<Long> expected = stepped(rotation, facing, 400, new FakeWorld(1000));
                assertThat(expected).as("speed %s hand %s", speed, hand).isNotEmpty();
                FakeWorld world = new FakeWorld(1000);
                TripHammerRun.Drive drive = TripHammerRun.Drive.FRESH;
                int strikes = 0;
                for (int batch = 0; batch < 20; batch++) {
                    // the fake world's clock counts the ticks the batch runs through its hits
                    long start = batch * 20L;
                    FakeWorld timed = new FakeWorld(world.durability) {
                        long n;

                        @Override
                        public Optional<BooleanSupplier> anvil() {
                            return Optional.of(() -> {
                                world.hits.add(start + n);
                                return true;
                            });
                        }

                        @Override
                        public boolean hasItem() {
                            n++;
                            return super.hasItem();
                        }
                    };
                    TripHammerRun.Result r = TripHammerRun.run(drive, 20, rotation, facing, timed);
                    world.durability = timed.durability;
                    assertThat(r.broke()).isFalse();
                    drive = r.drive();
                    strikes += r.strikes();
                }
                assertThat(world.hits).as("speed %s hand %s", speed, hand).isEqualTo(expected);
                assertThat(strikes).isEqualTo(expected.size());
                assertThat(world.durability).isEqualTo(1000 - expected.size());
            }
        }
    }

    /** At 0.1 rad/t TFC strikes once per 52 ticks after the start cooldown (TripHammerGoldenTest's cadence). */
    @Test
    void strikesAboutOncePerTurn() {
        FakeWorld world = new FakeWorld(1000);
        TripHammerRun.Result r = TripHammerRun.run(TripHammerRun.Drive.FRESH, 1000, Rotation.of(Direction.EAST, 0.1f),
            Direction.NORTH, world);
        // one turn is 2 pi / 0.1 = 62.8 ticks; the 0.8-turn cooldown never skips a turn
        assertThat(r.strikes()).isBetween(15, 16);
        assertThat(world.durability).isEqualTo(1000 - r.strikes());
    }

    @Test
    void theItemBreaksAndTheStrikesStop() {
        FakeWorld world = new FakeWorld(2);
        TripHammerRun.Result r = TripHammerRun.run(TripHammerRun.Drive.FRESH, 1000, Rotation.of(Direction.EAST, 0.1f),
            Direction.NORTH, world);
        assertThat(r.strikes()).isEqualTo(2);
        assertThat(world.hasItem()).isFalse();
    }

    @Test
    void theWrongHandBreaksTheHammer() {
        FakeWorld world = new FakeWorld(10);
        TripHammerRun.Result r = TripHammerRun.run(TripHammerRun.Drive.FRESH, 1000, Rotation.of(Direction.EAST, 0.1f),
            Direction.SOUTH, world);
        assertThat(r.broke()).isTrue();
        assertThat(r.strikes()).isZero();
        assertThat(world.durability).isEqualTo(10);
    }

    @Test
    void noRotationNoStrikeAndNoAnvilNoDamage() {
        FakeWorld world = new FakeWorld(10);
        TripHammerRun.Result idle = TripHammerRun.run(TripHammerRun.Drive.FRESH, 500, null, Direction.NORTH, world);
        assertThat(idle.strikes()).isZero();
        assertThat(idle.drive().state().lastAngleDegrees()).isEqualTo(Float.NEGATIVE_INFINITY);
        world.anvil = false;
        TripHammerRun.Result none = TripHammerRun.run(TripHammerRun.Drive.FRESH, 500, Rotation.of(Direction.EAST, 0.1f),
            Direction.NORTH, world);
        assertThat(none.strikes()).isZero();
        assertThat(world.durability).isEqualTo(10);
    }
}
