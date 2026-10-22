/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkerThreadsTest {
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();
    private final WorkerThreads w = new WorkerThreads(2, errors::add);

    @AfterEach
    void stop() {
        w.shutdown(1000);
    }

    @Test
    void cpuThreadsAreNamedBoundedAndRecognised() throws Exception {
        assertThat(w.cpuThreads()).isEqualTo(2);
        String name = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), w.cpu).get(5, TimeUnit.SECONDS);
        assertThat(name).startsWith("tfp-cpu-");
        assertThat(CompletableFuture.supplyAsync(() -> WorkerThreads.isWorker(Thread.currentThread()), w.cpu).get())
                .isTrue();
        assertThat(WorkerThreads.isWorker(Thread.currentThread())).isFalse();
    }

    @Test
    void ioRunsOnVirtualThreads() throws Exception {
        Thread t = CompletableFuture.supplyAsync(Thread::currentThread, w.io).get(5, TimeUnit.SECONDS);
        assertThat(t.isVirtual()).isTrue();
        assertThat(t.getName()).startsWith("tfp-io-");
    }

    @Test
    void taskExceptionsAreReportedAndWorkersSurvive() throws Exception {
        // One worker, so both tasks run on it in order: no race between the report and the next task.
        List<Throwable> seen = new CopyOnWriteArrayList<>();
        WorkerThreads single = new WorkerThreads(1, seen::add);
        try {
            CompletableFuture<String> failedOn = new CompletableFuture<>();
            single.cpu.execute(() -> {
                failedOn.complete(Thread.currentThread().getName());
                throw new IllegalStateException("boom");
            });
            CompletableFuture<String> after = CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(),
                    single.cpu);
            assertThat(after.get(5, TimeUnit.SECONDS)).isEqualTo(failedOn.get(5, TimeUnit.SECONDS)); // same worker
            assertThat(seen).singleElement().satisfies(e -> assertThat(e).hasMessage("boom")); // reported once
        } finally {
            single.shutdown(1000);
        }
    }

    @Test
    void exceptionIsReportedBeforeAnyTaskQueuedBehindIt() throws Exception {
        CountDownLatch reported = new CountDownLatch(1);
        WorkerThreads single = new WorkerThreads(1, e -> reported.countDown());
        try {
            single.cpu.execute(() -> {
                throw new IllegalStateException("boom");
            });
            CompletableFuture<Long> seenAfter = CompletableFuture.supplyAsync(reported::getCount, single.cpu);
            assertThat(seenAfter.get(5, TimeUnit.SECONDS)).isZero();
        } finally {
            single.shutdown(1000);
        }
    }

    @Test
    void parallelWorkersEachReportTheirOwnFailureOnce() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            String msg = "boom" + i;
            w.cpu.execute(() -> {
                bothRunning.countDown();
                throw new IllegalStateException(msg);
            });
        }
        // Each worker reports before it takes another task, so a barrier task per worker fences both reports.
        CountDownLatch fence = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < 2; i++) {
            w.cpu.execute(() -> {
                fence.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertThat(fence.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(bothRunning.getCount()).isZero();
        assertThat(errors).extracting(Throwable::getMessage).containsExactlyInAnyOrder("boom0", "boom1");
    }
}
