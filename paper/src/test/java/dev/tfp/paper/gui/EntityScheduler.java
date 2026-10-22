/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Test scheduler: entity tasks run at once (the test thread is the player's thread); delayed ones wait to be run. */
final class EntityScheduler implements Scheduler {
    record Delayed(long ticks, Runnable task) {
    }

    final List<Delayed> delayed = new ArrayList<>();
    int entityTasks;

    /** Runs and forgets the delayed tasks queued so far. */
    void runDelayed() {
        List<Delayed> now = new ArrayList<>(delayed);
        delayed.clear();
        now.forEach(d -> d.task().run());
    }

    @Override
    public void region(Location location, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void region(World world, int chunkX, int chunkZ, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
            Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean entity(Entity entity, Runnable task, Runnable retired) {
        entityTasks++;
        task.run();
        return true;
    }

    @Override
    public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
        delayed.add(new Delayed(delayTicks, task));
        return true;
    }

    @Override
    public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
            Runnable retired) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void global(Runnable task) {
        throw new UnsupportedOperationException();
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
