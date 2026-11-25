/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.wildraid;

import dev.tfp.core.wildraid.RaidSearch;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

/**
 * Vanilla's {@code MoveToBlockGoal.findNearestBlock} over {@link RaidSearch}'s order: the first valid block around the animal.
 * Blocks outside the region this thread owns and in unloaded chunks are skipped (never loaded or touched).
 */
final class RaidFinder {
    private RaidFinder() {
    }

    static Block nearest(Mob mob, int range, int vertical, Predicate<Block> valid) {
        World world = mob.getWorld();
        Location at = mob.getLocation();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        long lastChunk = Long.MIN_VALUE;
        boolean lastUsable = false;
        for (RaidSearch.Offset o : RaidSearch.offsets(range, vertical)) {
            int bx = x + o.dx();
            int by = y + o.dy();
            int bz = z + o.dz();
            if (by < minY || by >= maxY) {
                continue;
            }
            long chunk = ((long) (bx >> 4) << 32) | ((bz >> 4) & 0xFFFFFFFFL);
            if (chunk != lastChunk) { // the usable test is per chunk: loaded, and owned by this region thread
                lastChunk = chunk;
                lastUsable = world.isChunkLoaded(bx >> 4, bz >> 4)
                        && Bukkit.isOwnedByCurrentRegion(new Location(world, bx, by, bz));
            }
            if (!lastUsable) {
                continue;
            }
            Block block = world.getBlockAt(bx, by, bz);
            if (valid.test(block)) {
                return block;
            }
        }
        return null;
    }
}
