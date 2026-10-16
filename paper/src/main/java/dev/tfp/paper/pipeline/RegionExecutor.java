/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

/** Runs work on the thread that owns a chunk of one world. Backed by our Scheduler; faked in tests. */
public interface RegionExecutor {
    void run(int chunkX, int chunkZ, Runnable task);

    void runNextTick(int chunkX, int chunkZ, Runnable task);

    boolean isOwner(int chunkX, int chunkZ);
}
