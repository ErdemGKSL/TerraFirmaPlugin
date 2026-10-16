/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * M1-THR-04: publication of the global clock without tearing. jcstress is not wired into the build (no
 * Gradle plugin in the catalog yet), so this is a repeated JUnit stress test: one writer publishes
 * readings whose fields are all derived from one counter, several readers check that every snapshot
 * they see is internally consistent and that {@code sequence} never goes backwards for a reader.
 */
class GlobalClockHolderTest {
    static final int WRITES = 50_000;
    static final int READERS = 4;

    /** Fields derived from n: a torn read (fields from two publications) breaks these relations. */
    static void publish(GlobalClockHolder h, long n) {
        h.publish(n, n * 3 + 1, (int) (n % 97) + 1, (n & 1) == 0);
    }

    static boolean consistent(ClockSnapshot s) {
        if (!s.isSet()) {
            return s.equals(ClockSnapshot.UNSET);
        }
        long n = s.playerTicks();
        return s.sequence() == n && s.calendarTicks() == n * 3 + 1 && s.daysInMonth() == (int) (n % 97) + 1
                && s.running() == ((n & 1) == 0);
    }

    @RepeatedTest(10)
    void readersNeverSeeATornOrOlderSnapshot() throws Exception {
        GlobalClockHolder holder = new GlobalClockHolder();
        AtomicBoolean done = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch readersRunning = new CountDownLatch(READERS);
        ExecutorService pool = Executors.newFixedThreadPool(READERS + 1);
        try {
            List<Future<long[]>> readers = new ArrayList<>();
            for (int r = 0; r < READERS; r++) {
                readers.add(pool.submit(() -> {
                    start.await();
                    long reads = 0;
                    long bad = 0;
                    long backwards = 0;
                    long last = 0;
                    readersRunning.countDown();
                    do {
                        ClockSnapshot s = holder.get();
                        reads++;
                        if (!consistent(s)) {
                            bad++;
                        }
                        if (s.sequence() < last) {
                            backwards++;
                        }
                        last = s.sequence();
                    } while (!done.get());
                    return new long[] {reads, bad, backwards, last};
                }));
            }
            Future<?> writer = pool.submit(() -> {
                start.await();
                readersRunning.await();
                for (long n = 1; n <= WRITES; n++) {
                    publish(holder, n);
                }
                done.set(true);
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            for (Future<long[]> f : readers) {
                long[] r = f.get(30, TimeUnit.SECONDS);
                assertThat(r[0]).as("reads").isPositive();
                assertThat(r[1]).as("torn snapshots").isZero();
                assertThat(r[2]).as("sequence went backwards").isZero();
            }
            assertThat(holder.get().sequence()).isEqualTo(WRITES);
            assertThat(consistent(holder.get())).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void concurrentWritersKeepSequenceUnique() throws Exception {
        GlobalClockHolder holder = new GlobalClockHolder();
        int writers = 4;
        int each = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<List<Long>>> results = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int w = 0; w < writers; w++) {
                results.add(pool.submit(() -> {
                    start.await();
                    List<Long> seqs = new ArrayList<>(each);
                    for (int i = 0; i < each; i++) {
                        seqs.add(holder.publish(i, i, 8, true).sequence());
                    }
                    return seqs;
                }));
            }
            start.countDown();
            java.util.Set<Long> all = new java.util.HashSet<>();
            for (Future<List<Long>> f : results) {
                all.addAll(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(all).hasSize(writers * each);
            assertThat(holder.get().sequence()).isEqualTo((long) writers * each);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void startsUnsetAndValidates() {
        GlobalClockHolder holder = new GlobalClockHolder();
        assertThat(holder.get()).isSameAs(ClockSnapshot.UNSET);
        assertThat(holder.get().isSet()).isFalse();
        ClockSnapshot s = holder.publish(10, 20, 8, true);
        assertThat(s.isSet()).isTrue();
        assertThat(holder.get()).isSameAs(s);
        assertThatThrownBy(() -> holder.publish(1, 1, 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(holder.get()).isSameAs(s);
    }
}
