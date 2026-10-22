/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import org.bukkit.World;

/** {@link RegionExecutor} of one world on top of our {@link Scheduler}, for module pipelines. */
final class SchedulerRegions implements RegionExecutor {
    private final World world;
    private final Scheduler scheduler;

    SchedulerRegions(World world, Scheduler scheduler) {
        this.world = world;
        this.scheduler = scheduler;
    }

    @Override
    public void run(int chunkX, int chunkZ, Runnable task) {
        scheduler.region(world, chunkX, chunkZ, task);
    }

    @Override
    public void runNextTick(int chunkX, int chunkZ, Runnable task) {
        scheduler.regionDelayed(world, chunkX, chunkZ, 1, task);
    }

    @Override
    public boolean isOwner(int chunkX, int chunkZ) {
        return scheduler.isOwner(world, chunkX, chunkZ);
    }
}
