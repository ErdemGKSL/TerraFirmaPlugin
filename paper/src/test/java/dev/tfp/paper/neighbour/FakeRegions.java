/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import dev.tfp.paper.pipeline.RegionExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Two "regions" with their own threads, split at chunkX = 0 (like a Folia region border).
 * A "tick" is {@code tickMillis} long. (Copy of the pipeline tests' fake, which is package-private there.)
 */
final class FakeRegions implements RegionExecutor, AutoCloseable {
    private final ScheduledExecutorService west = thread("region-west");
    private final ScheduledExecutorService east = thread("region-east");
    private volatile Thread westThread;
    private volatile Thread eastThread;
    private final long tickMillis;

    FakeRegions(long tickMillis) {
        this.tickMillis = tickMillis;
        try {
            westThread = west.submit(Thread::currentThread).get();
            eastThread = east.submit(Thread::currentThread).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ScheduledExecutorService thread(String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    private ScheduledExecutorService of(int chunkX) {
        return chunkX < 0 ? west : east;
    }

    @Override
    public void run(int chunkX, int chunkZ, Runnable task) {
        of(chunkX).execute(task);
    }

    @Override
    public void runNextTick(int chunkX, int chunkZ, Runnable task) {
        of(chunkX).schedule(task, tickMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isOwner(int chunkX, int chunkZ) {
        return Thread.currentThread() == (chunkX < 0 ? westThread : eastThread);
    }

    /** Runs on the owner and waits (test thread only). */
    void await(int chunkX, Runnable task) throws Exception {
        of(chunkX).submit(task).get(60, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        west.shutdownNow();
        east.shutdownNow();
    }
}
