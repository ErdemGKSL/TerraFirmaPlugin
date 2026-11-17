/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** A scheduler for the light tests: delayed region tasks wait for {@link #tick}, everything else runs inline. */
final class LightTestScheduler implements Scheduler {
    record Delayed(long delay, Runnable task) {
    }

    final List<Delayed> delayed = new ArrayList<>();
    final List<Runnable> repeating = new ArrayList<>();

    /** Runs every delayed region task (and records none of the ones they queue until the next call). */
    void tick() {
        List<Delayed> now = new ArrayList<>(delayed);
        delayed.clear();
        now.forEach(d -> d.task().run());
    }

    private static TaskHandle handle() {
        return new TaskHandle() {
            boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }

    @Override
    public void region(Location location, Runnable task) {
        task.run();
    }

    @Override
    public void region(World world, int chunkX, int chunkZ, Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
        delayed.add(new Delayed(delayTicks, task));
        return handle();
    }

    @Override
    public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
            Runnable task) {
        repeating.add(task);
        return handle();
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
        repeating.add(task);
        return handle();
    }

    @Override
    public void global(Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        repeating.add(task);
        return handle();
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
