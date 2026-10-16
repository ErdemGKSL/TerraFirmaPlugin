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
import org.junit.jupiter.api.Test;

/**
 * Publication tests for {@link Snapshot} (plain JUnit + latches; jcstress is not wired yet).
 * They check what readers rely on: a published value is seen fully built, versions only grow,
 * and an older version never overwrites a newer one.
 */
class SnapshotTest {
    /** Deliberately mutable with plain fields: safe publication must still show them filled. */
    static final class Payload {
        long version;
        long[] data;

        Payload(long version, int size) {
            this.version = version;
            this.data = new long[size];
            for (int i = 0; i < size; i++) {
                data[i] = version;
            }
        }

        boolean consistent() {
            for (long d : data) {
                if (d != version) {
                    return false;
                }
            }
            return true;
        }
    }

    @Test
    void readerAfterLatchSeesFullyBuiltValue() throws Exception {
        for (int round = 0; round < 200; round++) {
            Snapshot<Payload> snap = new Snapshot<>(new Payload(0, 64));
            CountDownLatch published = new CountDownLatch(1);
            Thread writer = new Thread(() -> {
                snap.publish(new Payload(7, 64));
                published.countDown();
            });
            AtomicBoolean ok = new AtomicBoolean();
            Thread reader = new Thread(() -> {
                try {
                    published.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Payload p = snap.get();
                ok.set(p.version == 7 && p.consistent() && snap.version() == 1);
            });
            reader.start();
            writer.start();
            writer.join();
            reader.join();
            assertThat(ok).as("round %d", round).isTrue();
        }
    }

    @Test
    void concurrentReadersNeverSeeTornOrOlderValues() throws Exception {
        Snapshot<Payload> snap = new Snapshot<>(new Payload(0, 32));
        int readers = 4;
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < readers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                long last = -1;
                while (!stop.get()) {
                    Snapshot.Versioned<Payload> v = snap.current();
                    if (!v.value().consistent() || v.value().version != v.version() || v.version() < last) {
                        return false;
                    }
                    last = v.version();
                }
                return true;
            }));
        }
        start.countDown();
        for (long v = 1; v <= 20_000; v++) {
            assertThat(snap.publish(new Payload(v, 32))).isEqualTo(v);
        }
        stop.set(true);
        for (Future<Boolean> f : results) {
            assertThat(f.get(10, TimeUnit.SECONDS)).isTrue();
        }
        pool.shutdown();
    }

    @Test
    void publishIfNewerKeepsTheHighestVersionUnderRaces() throws Exception {
        Snapshot<Long> snap = new Snapshot<>(0L);
        int threads = 8;
        int perThread = 2_000;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int offset = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    long version = (long) i * threads + offset + 1;
                    snap.publishIfNewer(version, version);
                    Snapshot.Versioned<Long> seen = snap.current();
                    if (seen.version() != seen.value()) {
                        throw new AssertionError("value/version mismatch " + seen);
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        long max = (long) (perThread - 1) * threads + threads;
        assertThat(snap.version()).isEqualTo(max);
        assertThat(snap.get()).isEqualTo(max);
        assertThat(snap.publishIfNewer(max, -1L)).isFalse();
        assertThat(snap.publishIfNewer(max - 5, -1L)).isFalse();
        assertThat(snap.get()).isEqualTo(max);
    }
}
