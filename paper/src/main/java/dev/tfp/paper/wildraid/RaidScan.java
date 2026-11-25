/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCRabbit.java @ c3b4d9536 (RaidGardenGoal extends MoveToBlockGoal),
 *       src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java @ c3b4d9536 (TFCFoxEatBerriesGoal)
 * and net.minecraft.world.entity.ai.goal.MoveToBlockGoal.findNearestBlock (vanilla 1.21.1)
 */
package dev.tfp.paper.wildraid;

import dev.tfp.core.wildraid.RaidSearch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FAUNA-42: one {@code findNearestBlock} search that may run over several calls. The origin (the animal's block when
 * the search starts) and vanilla's order ({@link RaidSearch}) are fixed; each {@link #advance} tests cells in that order
 * until a valid one is found, the order ends, or the call's time slice is spent. The first valid cell in the order is
 * the result, as in one full scan from the origin.
 *
 * <p>A chunk is usable when it is loaded and owned by the calling region thread; this is decided once per chunk and call
 * (a region may change hands between calls, so the cache is cleared at the start of each call). Unusable chunks are
 * skipped without reading a block.
 */
final class RaidScan {
    /** A time slice that never ends: the whole search in one call. */
    static final long UNLIMITED = Long.MAX_VALUE;
    /** Cells tested between two reads of the clock. */
    static final int CHECK_EVERY = 16;

    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final List<RaidSearch.Offset> offsets;
    private final Predicate<Block> valid;
    private final Map<Long, Boolean> usable = new HashMap<>();
    private int next;
    private int reads;
    private Block found;
    private boolean done;

    RaidScan(World world, int x, int y, int z, int range, int vertical, Predicate<Block> valid) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.offsets = RaidSearch.offsets(range, vertical);
        this.valid = valid;
    }

    static RaidScan around(Location at, int range, int vertical, Predicate<Block> valid) {
        return new RaidScan(at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), range, vertical, valid);
    }

    World world() {
        return world;
    }

    /**
     * Tests cells until the search is done or {@code sliceNanos} of {@code clock} have passed (the clock is read every
     * {@value #CHECK_EVERY} cells). Returns true when the search is done; {@link #found} is then the result.
     */
    boolean advance(LongSupplier clock, long sliceNanos) {
        if (done) {
            return true;
        }
        usable.clear();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        long start = sliceNanos == UNLIMITED ? 0 : clock.getAsLong();
        int sinceCheck = 0;
        while (next < offsets.size()) {
            if (sliceNanos != UNLIMITED && ++sinceCheck > CHECK_EVERY) {
                sinceCheck = 1;
                if (clock.getAsLong() - start >= sliceNanos) {
                    return false;
                }
            }
            RaidSearch.Offset o = offsets.get(next++);
            int bx = x + o.dx();
            int by = y + o.dy();
            int bz = z + o.dz();
            if (by < minY || by >= maxY || !usable(bx >> 4, bz >> 4)) {
                continue;
            }
            reads++;
            Block block = world.getBlockAt(bx, by, bz);
            if (valid.test(block)) {
                found = block;
                done = true;
                return true;
            }
        }
        done = true;
        return true;
    }

    private boolean usable(int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Boolean u = usable.get(key);
        if (u == null) {
            u = world.isChunkLoaded(cx, cz) && Bukkit.isOwnedByCurrentRegion(world, cx, cz);
            usable.put(key, u);
        }
        return u;
    }

    /** The first valid cell, or null (only meaningful once {@link #advance} answered true). */
    Block found() {
        return found;
    }

    /** The cells read so far (blocks passed to the test). */
    int reads() {
        return reads;
    }
}
