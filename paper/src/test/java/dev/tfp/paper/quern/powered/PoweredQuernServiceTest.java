/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern.powered;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PoweredQuernServiceTest {
    private static final float RPM30 = (float) (2 * Math.PI * 30 / 1200);
    private static final PoweredQuernAccess.Key KEY = new PoweredQuernAccess.Key(new UUID(1, 1), 5, 64, 5);

    /** A quern, a clock, a speed above, and the manual quern's settle rule, with a tick-ordered task queue. */
    private static final class Fake implements PoweredQuernAccess {
        long now;
        boolean exists = true;
        boolean started;
        long finish;
        boolean handstone = true;
        int input;
        int output;
        Optional<Float> speed = Optional.empty();
        boolean tracked;
        int settles;
        int setFinishes;
        private long seq;
        private final PriorityQueue<long[]> order = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        private final java.util.Map<Long, Runnable> tasks = new java.util.HashMap<>();

        @Override
        public Optional<View> view(Key key) {
            return exists ? Optional.of(new View(now, started, finish, handstone, input > 0)) : Optional.empty();
        }

        @Override
        public CompletableFuture<Optional<Float>> speed(Key key) {
            return CompletableFuture.completedFuture(speed);
        }

        @Override
        public void settle(Key key) {
            settles++;
            if (started && now >= finish) { // QuernDevice.settle: the result, one input used
                output++;
                input--;
                started = false;
            }
        }

        @Override
        public void setFinish(Key key, long finishTick) {
            setFinishes++;
            started = true;
            finish = finishTick;
        }

        @Override
        public void later(Key key, long delayTicks, Runnable task) {
            long id = seq++;
            order.add(new long[] {now + delayTicks, id});
            tasks.put(id, task);
        }

        @Override
        public void onRegion(Key key, Runnable task) {
            task.run();
        }

        @Override
        public void track(Key key) {
            tracked = true;
        }

        @Override
        public void untrack(Key key) {
            tracked = false;
        }

        void advanceTo(long tick) {
            while (!order.isEmpty() && order.peek()[0] <= tick) {
                long[] next = order.poll();
                now = next[0];
                tasks.remove(next[1]).run();
            }
            now = tick;
        }
    }

    private Fake world;
    private PoweredQuernService service;

    @BeforeEach
    void setUp() {
        world = new Fake();
        service = new PoweredQuernService(world, Logger.getLogger("test"));
    }

    @Test
    void anAxleDrivenQuernGrindsItsInputAtTheNetworkSpeed() {
        world.input = 5;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        int ticks = PoweredQuern.grindTicks(RPM30);
        world.advanceTo(1L + 3L * ticks + PoweredQuern.CHECK_PERIOD);
        assertThat(world.output).isEqualTo(3);
        assertThat(world.input).isEqualTo(2);
    }

    @Test
    void throughputDoesNotDependOnHowOftenItIsLookedAt() {
        // a grind of 37 ticks is never aligned with the 10-tick looks: the next grind still starts at the planned finish tick
        float speed = speedForTicks(37);
        assertThat(PoweredQuern.grindTicks(speed)).isEqualTo(37);
        world.input = 50;
        world.speed = Optional.of(speed);
        service.watch(KEY);
        world.advanceTo(1 + 37L * 10 + 9);
        assertThat(world.output).isEqualTo(10);
    }

    @Test
    void aLongGapFinishesTheDueGrindOnceAndRestartsFromNow() {
        world.input = 5;
        world.speed = Optional.of(RPM30);
        world.started = true;
        world.finish = 100;
        world.now = 5000; // the chunk was unloaded for a long time; nothing was ticked
        service.watch(KEY);
        world.advanceTo(5001);
        assertThat(world.output).isEqualTo(1);
        assertThat(world.finish).isEqualTo(5001 + PoweredQuern.grindTicks(RPM30));
    }

    @Test
    void aSpeedChangeReplansTheRestOfTheGrind() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        world.advanceTo(1);
        long planned = world.finish; // about 41
        world.advanceTo(21);
        world.speed = Optional.of(2 * RPM30);
        world.advanceTo(31);
        // 20 ticks at 2.25 left about 45 units; at 4.5 per tick about 10 more ticks from the look at tick 31 (or earlier)
        assertThat(world.finish).isLessThan(planned);
        assertThat(world.finish).isBetween(30L, 38L);
        world.advanceTo(45);
        assertThat(world.output).isEqualTo(1);
    }

    @Test
    void aGrindThatLostItsAxleCarriesOnByHand() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        world.advanceTo(21);
        long before = world.finish;
        world.speed = Optional.empty();
        world.advanceTo(31);
        // about 90 - 30 * 2.25 = 22 units left at 1 per tick
        assertThat(world.finish).isGreaterThan(before);
        assertThat(world.finish).isBetween(50L, 56L);
        assertThat(service.isWatched(KEY)).isTrue();
        world.advanceTo(80);
        assertThat(world.output).isEqualTo(1);
        // then idle with no axle: no longer watched
        world.advanceTo(100);
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(world.tracked).isFalse();
    }

    @Test
    void aStandingNetworkWaitsAndResumesWithTheTimerKept() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        world.advanceTo(21);
        world.speed = Optional.of(0f);
        world.advanceTo(300);
        assertThat(world.output).isZero();
        assertThat(world.finish).isGreaterThan(5000L); // parked
        world.speed = Optional.of(RPM30);
        world.advanceTo(310);
        assertThat(world.output).isZero(); // about 22 units were left when it parked (it parks at the look after the stop): 10 ticks, not a fresh 40
        assertThat(world.finish).isBetween(305L, 320L);
        world.advanceTo(345);
        assertThat(world.output).isEqualTo(1);
    }

    @Test
    void anIdleQuernWithNoAxleIsNotWatched() {
        world.input = 3;
        service.watch(KEY);
        assertThat(world.tracked).isTrue();
        world.advanceTo(5);
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(world.tracked).isFalse();
        assertThat(world.setFinishes).isZero();
    }

    @Test
    void withoutAHandstoneItDoesNotStartButKeepsWatchingTheAxle() {
        world.input = 3;
        world.handstone = false;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        world.advanceTo(100);
        assertThat(world.setFinishes).isZero();
        assertThat(service.isWatched(KEY)).isTrue();
        world.handstone = true;
        world.advanceTo(110 + 2L * PoweredQuern.grindTicks(RPM30));
        assertThat(world.output).isGreaterThanOrEqualTo(1);
    }

    @Test
    void whenTheInputRunsOutItStopsAndStartsAgainWhenFed() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        world.advanceTo(200);
        assertThat(world.output).isEqualTo(1);
        assertThat(world.started).isFalse();
        world.input = 2; // a hopper fed it
        world.advanceTo(200 + 2L * PoweredQuern.grindTicks(RPM30) + 2 * PoweredQuern.CHECK_PERIOD);
        assertThat(world.output).isEqualTo(3);
    }

    @Test
    void aBrokenQuernIsForgotten() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        world.exists = false;
        world.advanceTo(50);
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(world.tracked).isFalse();
    }

    @Test
    void watchingTwiceSchedulesOnce() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        service.watch(KEY);
        assertThat(service.watchedCount()).isEqualTo(1);
        world.advanceTo(10);
        assertThat(world.setFinishes).isEqualTo(1);
    }

    @Test
    void droppedQuernsStopLooking() {
        world.input = 1;
        world.speed = Optional.of(RPM30);
        service.watch(KEY);
        service.drop(KEY);
        world.advanceTo(100);
        assertThat(world.setFinishes).isZero();
        assertThat(world.tracked).isTrue(); // still listed: the chunk load picks it up again
    }

    /** A network speed at which a grind takes exactly {@code ticks} ticks. */
    private static float speedForTicks(int ticks) {
        float perTick = 90f / ticks + 0.001f;
        return perTick / PoweredQuern.PER_SPEED;
    }
}
