/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Cheap ownership assertions, enabled with {@code -Dtfp.assertThreads=true} (tests, CI, self-tests).
 * They throw {@link IllegalStateException}, which the self-test harness treats as a failure.
 */
public final class ThreadAsserts {
    public static final boolean ENABLED = Boolean.getBoolean("tfp.assertThreads");

    private ThreadAsserts() {
    }

    public static void owned(Location location) {
        if (ENABLED && !Bukkit.isOwnedByCurrentRegion(location)) {
            throw new IllegalStateException("not region owner of " + location + " on " + Thread.currentThread().getName());
        }
    }

    public static void owned(World world, int chunkX, int chunkZ) {
        if (ENABLED && !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            throw new IllegalStateException("not region owner of chunk " + world.getName() + " " + chunkX + "," + chunkZ
                    + " on " + Thread.currentThread().getName());
        }
    }

    public static void owned(Entity entity) {
        if (ENABLED && !Bukkit.isOwnedByCurrentRegion(entity)) {
            throw new IllegalStateException("not owner of entity " + entity.getUniqueId() + " on "
                    + Thread.currentThread().getName());
        }
    }

    /** Blocking or heavy work must never run on a tick thread. */
    public static void notTickThread() {
        if (ENABLED && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("blocking/heavy work on tick thread " + Thread.currentThread().getName());
        }
    }

    /** Pure/async code paths: must run on one of our worker threads. */
    public static void workerThread() {
        if (ENABLED && !WorkerThreads.isWorker(Thread.currentThread())) {
            throw new IllegalStateException("expected a tfp worker thread, got " + Thread.currentThread().getName());
        }
    }
}
