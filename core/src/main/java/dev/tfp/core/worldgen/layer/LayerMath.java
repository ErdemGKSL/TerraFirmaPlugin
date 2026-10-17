/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/Area.java @ c3b4d9536
 * The other helpers it stands for: fastutil's HashCommon (murmurHash3, mix; Apache-2.0), Helpers.packChunkPos
 * (ChunkPos.asLong) and Mth.smallestEncompassingPowerOfTwo, all used by the same TFC framework.
 */
package dev.tfp.core.worldgen.layer;

/**
 * Hashing and bit helpers of the layer framework. Pure functions.
 */
final class LayerMath {
    private LayerMath() {}

    /** {@code HashCommon.murmurHash3(long)}: the 64-bit finaliser of MurmurHash3. */
    static long murmurHash3(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    /** {@code HashCommon.mix(long)}: fastutil's Fibonacci-style mixer, used to index the area caches. */
    static long mix(long x) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }

    /** {@code Helpers.packChunkPos}: {@code ChunkPos.asLong(x, z)}, the two ints packed into one long. */
    static long packChunkPos(int x, int z) {
        return (long) x & 0xFFFF_FFFFL | ((long) z & 0xFFFF_FFFFL) << 32;
    }

    /** {@code Mth.smallestEncompassingPowerOfTwo}: the smallest power of two that is at least {@code value}. */
    static int smallestEncompassingPowerOfTwo(int value) {
        int i = value - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }
}
