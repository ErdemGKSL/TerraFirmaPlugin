/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Test scheduler: entity and region tasks run now, delayed ones wait for {@link #runDelayed}, repeating ones for
 * {@link #tick}; {@link #owner} answers {@link #isOwner} (false: the Folia case of a viewer on another region).
 */
final class BarrelTestScheduler implements Scheduler {
    final List<Runnable> delayed = new ArrayList<>();
    final List<Handle> repeating = new ArrayList<>();
    boolean owner = true;

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

    void runDelayed() {
        List<Runnable> now = new ArrayList<>(delayed);
        delayed.clear();
        now.forEach(Runnable::run);
    }

    void tick() {
        for (Handle h : new ArrayList<>(repeating)) {
            if (!h.cancelled) {
                h.task.run();
            }
        }
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
        Handle h = new Handle(task);
        delayed.add(() -> {
            if (!h.cancelled) {
                task.run();
            }
        });
        return h;
    }

    @Override
    public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
            Runnable task) {
        Handle h = new Handle(task);
        repeating.add(h);
        return h;
    }

    @Override
    public boolean entity(Entity entity, Runnable task, Runnable retired) {
        task.run();
        return true;
    }

    @Override
    public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
        delayed.add(task);
        return true;
    }

    @Override
    public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
            Runnable retired) {
        Handle h = new Handle(task);
        repeating.add(h);
        return h;
    }

    @Override
    public void global(Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        Handle h = new Handle(task);
        repeating.add(h);
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
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Executor ioExecutor() {
        return Runnable::run;
    }

    @Override
    public boolean isOwner(World world, int chunkX, int chunkZ) {
        return owner;
    }

    @Override
    public void shutdown() {
    }
}
