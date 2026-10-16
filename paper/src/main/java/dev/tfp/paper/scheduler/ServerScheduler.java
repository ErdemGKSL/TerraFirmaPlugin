/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** {@link Scheduler} on top of the Folia scheduler API, which Paper implements as well. */
public final class ServerScheduler implements Scheduler {
    private final Plugin plugin;
    private final WorkerThreads workers;

    public ServerScheduler(Plugin plugin, int cpuThreads) {
        this.plugin = plugin;
        this.workers = new WorkerThreads(cpuThreads,
                e -> plugin.getLogger().log(Level.SEVERE, "Uncaught exception in a tfp worker", e));
    }

    @Override
    public void region(Location location, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, location, guarded(task));
    }

    @Override
    public void region(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, guarded(task));
    }

    @Override
    public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
        Runnable g = guarded(task);
        return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, world, chunkX, chunkZ, t -> g.run(),
                Math.max(1, delayTicks)));
    }

    @Override
    public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
            Runnable task) {
        Runnable g = guarded(task);
        return wrap(Bukkit.getRegionScheduler().runAtFixedRate(plugin, world, chunkX, chunkZ, t -> g.run(),
                Math.max(1, initialDelayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public boolean entity(Entity entity, Runnable task, Runnable retired) {
        return entity.getScheduler().execute(plugin, guarded(task), retired == null ? null : guarded(retired), 1);
    }

    @Override
    public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
        return entity.getScheduler().execute(plugin, guarded(task), retired == null ? null : guarded(retired),
                Math.max(1, delayTicks));
    }

    @Override
    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, guarded(task));
    }

    @Override
    public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        Runnable g = guarded(task);
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> g.run(),
                Math.max(1, initialDelayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public void cpu(Runnable task) {
        workers.cpu.execute(task);
    }

    @Override
    public Executor cpuExecutor() {
        return workers.cpu;
    }

    @Override
    public CompletableFuture<Void> io(Runnable task) {
        return CompletableFuture.runAsync(task, workers.io);
    }

    @Override
    public Executor ioExecutor() {
        return workers.io;
    }

    @Override
    public boolean isOwner(World world, int chunkX, int chunkZ) {
        return Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    @Override
    public void shutdown() {
        workers.shutdown(5_000);
    }

    /** Exceptions in server-thread tasks are logged with our plugin name instead of vanishing. */
    private Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Uncaught exception in a scheduled task", t);
                if (t instanceof Error e && !(t instanceof AssertionError)) {
                    throw e;
                }
            }
        };
    }

    private static TaskHandle wrap(ScheduledTask task) {
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
