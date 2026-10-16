/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HudSnapshotCacheTest {
    @Test
    void defaultUntilPublishedAndAfterRemove() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> false);
        UUID p = UUID.randomUUID();
        assertThat(cache.snapshot(p)).isEqualTo(HudSnapshot.DEFAULT);
        cache.put(p, HudSnapshot.DEFAULT.withThirst(42));
        assertThat(cache.snapshot(p).thirst()).isEqualTo(42);
        cache.remove(p);
        assertThat(cache.snapshot(p)).isEqualTo(HudSnapshot.DEFAULT);
        assertThat(cache.size()).isZero();
    }

    @Test
    void activeFollowsSupplier() {
        AtomicBoolean on = new AtomicBoolean();
        HudSnapshotCache cache = new HudSnapshotCache(on::get);
        assertThat(cache.isActive()).isFalse();
        on.set(true);
        assertThat(cache.isActive()).isTrue();
    }

    /** Readers on other threads only ever see complete snapshots (each one internally consistent). */
    @Test
    void concurrentReadersSeeWholeSnapshots() throws Exception {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        UUID p = UUID.randomUUID();
        AtomicBoolean torn = new AtomicBoolean();
        AtomicBoolean stop = new AtomicBoolean();
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread t = Thread.ofPlatform().start(() -> {
                while (!stop.get()) {
                    HudSnapshot s = cache.snapshot(p);
                    if (s != HudSnapshot.DEFAULT && s.thirst() != s.hunger()) {
                        torn.set(true);
                    }
                }
            });
            readers.add(t);
        }
        for (int v = 0; v < 200_000; v++) {
            cache.put(p, new HudSnapshot(v, v, v, v, v));
        }
        stop.set(true);
        for (Thread t : readers) {
            t.join();
        }
        assertThat(torn).isFalse();
    }
}
