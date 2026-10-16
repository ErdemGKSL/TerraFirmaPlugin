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
        w.cpu.execute(() -> {
            throw new IllegalStateException("boom");
        });
        CountDownLatch after = new CountDownLatch(1);
        w.cpu.execute(after::countDown);
        assertThat(after.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).anySatisfy(e -> assertThat(e).hasMessage("boom"));
    }
}
