/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import org.bukkit.World;

/** {@link RegionExecutor} for one world, on top of our {@link Scheduler} (region tasks, owner checks). */
final class WorldRegions implements RegionExecutor {
    private final World world;
    private final Scheduler scheduler;

    WorldRegions(World world, Scheduler scheduler) {
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
