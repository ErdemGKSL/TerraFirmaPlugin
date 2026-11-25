/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Region tasks queue until {@link #drain()} (so a re-queued slice is visible); entity, global and CPU tasks run at once. */
final class QueueScheduler implements Scheduler {
    final Queue<Runnable> regionTasks = new ArrayDeque<>();
    int regionRuns;

    void drain() {
        Runnable r;
        while ((r = regionTasks.poll()) != null) {
            regionRuns++;
            r.run();
        }
    }

    private static TaskHandle handle() {
        return new TaskHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    @Override
    public void region(Location location, Runnable task) {
        regionTasks.add(task);
    }

    @Override
    public void region(World world, int chunkX, int chunkZ, Runnable task) {
        regionTasks.add(task);
    }

    @Override
    public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
        regionTasks.add(task);
        return handle();
    }

    @Override
    public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
            Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean entity(Entity entity, Runnable task, Runnable retired) {
        task.run();
        return true;
    }

    @Override
    public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
        task.run();
        return true;
    }

    @Override
    public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
            Runnable retired) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void global(Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cpu(Runnable task) {
        task.run();
    }

    @Override
    public Executor cpuExecutor() {
        return Runnable::run;
    }

    @Override
    public CompletableFuture<Void> io(Runnable task) {
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Executor ioExecutor() {
        return Runnable::run;
    }

    @Override
    public boolean isOwner(World world, int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void shutdown() {
    }
}
