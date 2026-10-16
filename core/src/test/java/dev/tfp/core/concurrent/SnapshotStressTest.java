/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.RepeatedTest;

/**
 * M1-THR-04: repeated JUnit stress runs for {@link Snapshot} (jcstress is not wired into the build).
 * Writers race with {@link Snapshot#update}; readers check that the version and the value they get from
 * {@link Snapshot#current()} belong to the same publication and never go backwards.
 */
class SnapshotStressTest {
    /** Immutable pair whose fields must match: a torn read would break {@code b == 2a}. */
    record Pair(long a, long b) {
        Pair next() {
            return new Pair(a + 1, (a + 1) * 2);
        }
    }

    @RepeatedTest(10)
    void concurrentUpdatesLoseNothingAndReadersSeeWholeValues() throws Exception {
        Snapshot<Pair> snap = new Snapshot<>(new Pair(0, 0));
        int writers = 3;
        int each = 20_000;
        int readers = 3;
        AtomicBoolean done = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
        try {
            List<Future<?>> ws = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                ws.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < each; i++) {
                        snap.update(Pair::next);
                    }
                    return null;
                }));
            }
            List<Future<long[]>> rs = new ArrayList<>();
            for (int r = 0; r < readers; r++) {
                rs.add(pool.submit(() -> {
                    start.await();
                    long bad = 0;
                    long backwards = 0;
                    long last = -1;
                    while (!done.get()) {
                        Snapshot.Versioned<Pair> v = snap.current();
                        // update() publishes value n with version n: version and value must agree.
                        if (v.value().b() != v.value().a() * 2 || v.version() != v.value().a()) {
                            bad++;
                        }
                        if (v.version() < last) {
                            backwards++;
                        }
                        last = v.version();
                    }
                    return new long[] {bad, backwards};
                }));
            }
            start.countDown();
            for (Future<?> f : ws) {
                f.get(30, TimeUnit.SECONDS);
            }
            done.set(true);
            for (Future<long[]> f : rs) {
                long[] r = f.get(30, TimeUnit.SECONDS);
                assertThat(r[0]).as("torn reads").isZero();
                assertThat(r[1]).as("version went backwards").isZero();
            }
            long total = (long) writers * each;
            assertThat(snap.version()).isEqualTo(total);
            assertThat(snap.get()).isEqualTo(new Pair(total, total * 2));
        } finally {
            pool.shutdownNow();
        }
    }
}
