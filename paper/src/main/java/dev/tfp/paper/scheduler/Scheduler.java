/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * The only way our code schedules work (never {@code BukkitScheduler}). One implementation serves
 * Paper and Folia, because Paper also implements Folia's region/entity/global/async schedulers.
 *
 * <ul>
 *   <li><b>region</b>: the thread that owns a block/chunk. World reads and writes for that chunk go here.</li>
 *   <li><b>entity</b>: follows the entity across regions; {@code retired} runs instead if the entity is
 *       removed before the task could run.</li>
 *   <li><b>global</b>: world-independent state (time, weather). Rare.</li>
 *   <li><b>cpu</b>: our bounded worker pool for pure computation. Never touches Bukkit world state.</li>
 *   <li><b>io</b>: virtual threads for disk/network.</li>
 * </ul>
 * Tick arguments are server ticks (50 ms). Delays &lt; 1 tick run on the next tick.
 */
public interface Scheduler {
    void region(Location location, Runnable task);

    void region(World world, int chunkX, int chunkZ, Runnable task);

    TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task);

    TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
            Runnable task);

    /** @return false if the entity was already removed (the task will not run, {@code retired} neither). */
    boolean entity(Entity entity, Runnable task, Runnable retired);

    boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired);

    void global(Runnable task);

    TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task);

    void cpu(Runnable task);

    /** For {@link CompletableFuture} stages that compute. */
    Executor cpuExecutor();

    CompletableFuture<Void> io(Runnable task);

    Executor ioExecutor();

    /** True if the current thread owns the chunk (always true on the main thread of Paper). */
    boolean isOwner(World world, int chunkX, int chunkZ);

    /** Stops our pools. Region/global tasks are cancelled by the server when the plugin disables. */
    void shutdown();

    /** A cancellable scheduled task. */
    interface TaskHandle {
        void cancel();

        boolean isCancelled();
    }
}
