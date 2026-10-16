/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.world;

/** Immutable block position. Platform-free; adapters convert to/from Bukkit types. */
public record BlockPos(int x, int y, int z) {
    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** Key of the containing chunk, see {@link ChunkPos#key(int, int)}. */
    public long chunkKey() {
        return ChunkPos.key(chunkX(), chunkZ());
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos offset(Direction d) {
        return offset(d.dx(), d.dy(), d.dz());
    }

    public int manhattan(BlockPos o) {
        return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z);
    }
}
