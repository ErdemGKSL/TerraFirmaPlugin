/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Our own executors: a bounded CPU pool and virtual-thread IO. Platform-independent, unit-testable. */
public final class WorkerThreads {
    static final String CPU_PREFIX = "tfp-cpu-";
    static final String IO_PREFIX = "tfp-io-";

    final ThreadPoolExecutor cpu;
    final ExecutorService io;

    /**
     * @param cpuThreads worker count; {@code <= 0} means {@code max(1, cores - 1)}
     * @param onError    receives exceptions thrown by tasks (they never kill a worker silently)
     */
    public WorkerThreads(int cpuThreads, Consumer<Throwable> onError) {
        int n = cpuThreads > 0 ? cpuThreads : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        AtomicInteger id = new AtomicInteger();
        ThreadFactory cpuFactory = r -> {
            Thread t = new Thread(r, CPU_PREFIX + id.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // tick threads win under contention
            t.setUncaughtExceptionHandler((th, e) -> onError.accept(e));
            return t;
        };
        this.cpu = new ThreadPoolExecutor(n, n, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), cpuFactory) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                if (t != null) {
                    onError.accept(t);
                }
            }
        };
        this.io = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(IO_PREFIX, 0).factory());
    }

    public static boolean isWorker(Thread t) {
        String n = t.getName();
        return n.startsWith(CPU_PREFIX) || n.startsWith(IO_PREFIX);
    }

    public int cpuThreads() {
        return cpu.getCorePoolSize();
    }

    public int cpuQueueSize() {
        return cpu.getQueue().size();
    }

    public void shutdown(long timeoutMillis) {
        cpu.shutdown();
        io.shutdown();
        try {
            if (!cpu.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                cpu.shutdownNow();
            }
            if (!io.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            cpu.shutdownNow();
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
