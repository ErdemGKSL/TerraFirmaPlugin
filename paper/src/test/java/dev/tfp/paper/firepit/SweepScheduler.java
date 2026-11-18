/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * PAR-HEAT-33 test scheduler: region tasks wait for {@link #runRegion}, delayed region tasks for {@link #runDelayed},
 * global repeating tasks for {@link #tick} (one period).
 */
final class SweepScheduler implements Scheduler {
    record Delayed(long ticks, Runnable task) {
    }

    static final class Handle implements TaskHandle {
        final Runnable task;
        boolean cancelled;

        Handle(Runnable task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    final List<Delayed> delayed = new ArrayList<>();
    final List<Runnable> regionTasks = new ArrayList<>();
    final List<Handle> global = new ArrayList<>();

    void runDelayed() {
        List<Delayed> now = new ArrayList<>(delayed);
        delayed.clear();
        now.forEach(d -> d.task().run());
    }

    void runRegion() {
        while (!regionTasks.isEmpty()) {
            regionTasks.removeFirst().run();
        }
    }

    /** One period of every live global repeating task, then the region tasks it queued. */
    void tick() {
        for (Handle h : new ArrayList<>(global)) {
            if (!h.cancelled) {
                h.task.run();
            }
        }
        runRegion();
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
        delayed.add(new Delayed(delayTicks, task));
        return new Handle(task);
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
        throw new UnsupportedOperationException();
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
        Handle h = new Handle(task);
        global.add(h);
        return h;
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
        throw new UnsupportedOperationException();
    }

    @Override
    public Executor ioExecutor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOwner(World world, int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void shutdown() {
    }
}
