/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/FastConcurrentCache.java @ c3b4d9536
 */
package dev.tfp.core.worldgen;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

/**
 * A direct-mapped cache keyed by a pair of ints, safe for concurrent readers and writers. A read takes an optimistic
 * stamp and the write lock guards updates. Entries are overwritten on collision, so a miss only costs a recomputation.
 */
public final class FastConcurrentCache<T> {
    private final StampedLock lock = new StampedLock();

    private final long[] keys;
    private final Object[] values;
    private final int mask;

    public FastConcurrentCache(int size) {
        size = smallestEncompassingPowerOfTwo(size);

        this.mask = size - 1;
        this.keys = new long[size];
        this.values = new Object[size];

        Arrays.fill(this.keys, Long.MIN_VALUE);
    }

    /** @return The cached value, or null if the slot holds another key. */
    @SuppressWarnings("unchecked")
    public T getIfPresent(int x, int z) {
        final long key = packChunkPos(x, z);
        final int index = (int) mix(key) & mask;
        final long stamp = lock.readLock();

        T t = null;
        if (keys[index] == key) {
            t = (T) values[index];
        }

        lock.unlockRead(stamp);
        return t;
    }

    public void set(int x, int z, T value) {
        final long key = packChunkPos(x, z);
        final int index = (int) mix(key) & mask;
        final long stamp = lock.writeLock();

        keys[index] = key;
        values[index] = value;

        lock.unlockWrite(stamp);
    }

    /** {@code Helpers.packChunkPos}: {@code ChunkPos.asLong(x, z)}. */
    private static long packChunkPos(int x, int z) {
        return (long) x & 0xFFFF_FFFFL | ((long) z & 0xFFFF_FFFFL) << 32;
    }

    /** {@code HashCommon.mix(long)}, as in {@code dev.tfp.core.worldgen.layer.LayerMath}. */
    private static long mix(long x) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }

    /** {@code Mth.smallestEncompassingPowerOfTwo}. */
    private static int smallestEncompassingPowerOfTwo(int value) {
        int i = value - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }
}
