/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powerloom;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.powerloom.PowerLoomIndex.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** PAR-MECH-08: the watch and look cycle of {@link PowerLoomService} on a fake server. */
class PowerLoomServiceTest {
    private static final Key KEY = new Key(UUID.randomUUID(), 3, 64, 5);

    /** A scripted server: the stored loom, the speed answer, and the queue of delayed tasks. */
    private static final class Fake implements PowerLoomAccess {
        boolean present = true;
        boolean active;
        /** A step waits: the loom stays active at speed 0. */
        boolean waiting;
        int pushes;
        Optional<Float> speed = Optional.empty();
        boolean speedFails;
        /** Pushes the next {@link #apply} makes (the catch-up inside the edit). */
        int pushesOnApply;
        final List<Float> applied = new ArrayList<>();
        int weaves;
        int tracked;
        int untracked;
        final List<Runnable> delayed = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        @Override
        public Optional<View> view(Key key) {
            return present ? Optional.of(new View(active, pushes)) : Optional.empty();
        }

        @Override
        public CompletableFuture<Optional<Float>> speed(Key key) {
            return speedFails ? CompletableFuture.failedFuture(new IllegalStateException("boom")) : CompletableFuture.completedFuture(speed);
        }

        @Override
        public void apply(Key key, float s) {
            applied.add(s);
            pushes += pushesOnApply;
            active = s != 0f || waiting;
        }

        @Override
        public void weave(Key key) {
            weaves++;
        }

        @Override
        public void later(Key key, long delayTicks, Runnable task) {
            delays.add(delayTicks);
            delayed.add(task);
        }

        @Override
        public void onRegion(Key key, Runnable task) {
            task.run();
        }

        @Override
        public void track(Key key) {
            tracked++;
        }

        @Override
        public void untrack(Key key) {
            untracked++;
        }

        void runNext() {
            delayed.remove(0).run();
        }
    }

    private final Fake fake = new Fake();
    private final PowerLoomService service = new PowerLoomService(fake, Logger.getLogger("test"));

    @Test
    void watchingTracksTheLoomAndLooksNextTick() {
        service.watch(KEY);
        assertThat(service.isWatched(KEY)).isTrue();
        assertThat(fake.tracked).isEqualTo(1);
        assertThat(fake.delays).containsExactly(1L);
        service.watch(KEY); // a second watch of the same loom changes nothing
        assertThat(fake.tracked).isEqualTo(1);
        assertThat(fake.delays).hasSize(1);
    }

    @Test
    void aLoomWithNothingToDoStopsBeingWatched() {
        service.watch(KEY);
        fake.runNext();
        assertThat(fake.applied).containsExactly(0f);
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(fake.untracked).isEqualTo(1);
    }

    @Test
    void aConnectedLoomIsLookedAtEveryTenTicksEvenAtSpeedZero() {
        fake.speed = Optional.of(0f);
        service.watch(KEY);
        fake.runNext();
        assertThat(service.isWatched(KEY)).isTrue();
        assertThat(fake.delays).containsExactly(1L, PowerLoomService.LOOK_PERIOD);
        fake.runNext();
        assertThat(fake.delays).hasSize(3);
    }

    @Test
    void theSpeedOfTheLookIsStoredAndPushesPlayTheWeaveSound() {
        fake.speed = Optional.of(0.1f);
        fake.pushesOnApply = 2;
        service.watch(KEY);
        fake.runNext();
        assertThat(fake.applied).containsExactly(0.1f);
        assertThat(fake.weaves).isEqualTo(1); // one sound however many pushes the gap held
        fake.pushesOnApply = 0;
        fake.runNext();
        assertThat(fake.weaves).isEqualTo(1);
    }

    @Test
    void aLostConnectionStopsTheLoomOnceNoStepWaits() {
        fake.speed = Optional.of(0.1f);
        service.watch(KEY);
        fake.runNext();
        assertThat(service.isWatched(KEY)).isTrue();
        fake.speed = Optional.empty();
        fake.waiting = true; // a pushed step still waits to be counted: keep looking
        fake.runNext();
        assertThat(service.isWatched(KEY)).isTrue();
        fake.waiting = false;
        fake.runNext();
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(fake.untracked).isEqualTo(1);
    }

    @Test
    void aBrokenLoomIsForgotten() {
        service.watch(KEY);
        fake.present = false;
        fake.runNext();
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(fake.untracked).isEqualTo(1);
        assertThat(fake.applied).isEmpty();
    }

    @Test
    void aFailedSpeedAnswerCountsAsNoRotation() {
        fake.speedFails = true;
        service.watch(KEY);
        fake.runNext();
        assertThat(fake.applied).containsExactly(0f);
    }

    @Test
    void droppingAChunkKeepsTheTrackedList() {
        service.watch(KEY);
        service.drop(KEY);
        assertThat(service.isWatched(KEY)).isFalse();
        assertThat(fake.untracked).isZero();
        fake.runNext(); // the look of a dropped loom does nothing
        assertThat(fake.applied).isEmpty();
    }

    @Test
    void countsWatchedLooms() {
        service.watch(KEY);
        service.watch(new Key(KEY.world(), 4, 64, 5));
        assertThat(service.watchedCount()).isEqualTo(2);
    }
}
