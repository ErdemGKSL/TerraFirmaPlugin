/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.world;

/** Chunk coordinates. */
public record ChunkPos(int x, int z) {
    public long key() {
        return key(x, z);
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public static ChunkPos fromKey(long key) {
        return new ChunkPos((int) (key >> 32), (int) key);
    }
}
