/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import dev.tfp.core.oreoverlay.CellLookup;
import dev.tfp.core.oreoverlay.OreSnapshot;
import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * The server's {@link CellReader}: custom blocks from the {@link OreBlockSource} (CraftEngine's chunk storage), every
 * other block from Bukkit (a {@link ChunkSnapshot} for full builds, single reads for patches). A vanilla block exposes an
 * ore face when its material does not occlude ({@link Material#isOccluding()}: air, fluids, glass, leaves, slabs...).
 */
public final class BukkitCellReader implements CellReader {
    private final OreBlockSource source;
    private final ToIntFunction<String> classify;

    public BukkitCellReader(OreBlockSource source, OreOverlayData data) {
        this.source = source;
        this.classify = data::code;
    }

    private static World world(UUID id) {
        World w = Bukkit.getWorld(id);
        if (w == null) {
            throw new IllegalStateException("world " + id + " is not loaded");
        }
        return w;
    }

    @Override
    public boolean loaded(UUID world, int chunkX, int chunkZ) {
        World w = Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public OreSnapshot snapshot(UUID world, int chunkX, int chunkZ) {
        World w = world(world);
        OreBlockSource.ChunkCells cells = source.copy(w, chunkX, chunkZ, classify);
        ChunkSnapshot snap = w.getChunkAt(chunkX, chunkZ).getChunkSnapshot(true, false, false);
        return new ChunkOreSnapshot(chunkX, chunkZ, w.getMinHeight(), w.getMaxHeight(), cells, snap);
    }

    @Override
    public long cell(UUID world, int x, int y, int z) {
        World w = world(world);
        int code = source.code(w, x, y, z, classify);
        if (code == CellLookup.UNKNOWN) {
            code = vanilla(w.getBlockAt(x, y, z).getType());
        }
        int surface = w.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1;
        return CellMap.pack(code, surface);
    }

    @Override
    public int minY(UUID world) {
        World w = Bukkit.getWorld(world);
        return w == null ? -64 : w.getMinHeight();
    }

    @Override
    public int maxY(UUID world) {
        World w = Bukkit.getWorld(world);
        return w == null ? 320 : w.getMaxHeight();
    }

    /** The cell code of a vanilla block. */
    static int vanilla(Material m) {
        return m.isOccluding() ? ExposureRule.SOLID : ExposureRule.OPEN;
    }

    /** A chunk copy: custom cells first, the vanilla snapshot under them. Immutable; decoded on the CPU pool. */
    static final class ChunkOreSnapshot implements OreSnapshot {
        private final int chunkX;
        private final int chunkZ;
        private final int minY;
        private final int maxY;
        private final OreBlockSource.ChunkCells cells;
        private final ChunkSnapshot snap;

        ChunkOreSnapshot(int chunkX, int chunkZ, int minY, int maxY, OreBlockSource.ChunkCells cells, ChunkSnapshot snap) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minY = minY;
            this.maxY = maxY;
            this.cells = cells;
            this.snap = snap;
        }

        @Override
        public int chunkX() {
            return chunkX;
        }

        @Override
        public int chunkZ() {
            return chunkZ;
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public int maxY() {
            return maxY;
        }

        @Override
        public int[] ores() {
            return cells.ores();
        }

        @Override
        public int code(int x, int y, int z) {
            if (y < minY) {
                return ExposureRule.SOLID;
            }
            if (y >= maxY) {
                return ExposureRule.OPEN;
            }
            if (!inChunk(x, z)) {
                return UNKNOWN;
            }
            int c = cells.code(x & 15, y, z & 15);
            return c != UNKNOWN ? c : vanilla(snap.getBlockType(x & 15, y, z & 15));
        }

        @Override
        public int surface(int x, int z) {
            return inChunk(x, z) ? snap.getHighestBlockYAt(x & 15, z & 15) + 1 : NO_SURFACE;
        }
    }
}
