/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/Area.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

import java.util.Arrays;

/**
 * A caching wrapper around an {@link AreaSource}, created from the result of a stack of layers. The cache is a direct-mapped
 * table and is not synchronized: use one {@code Area} per thread (see {@link ConcurrentArea}).
 */
public final class Area {
    private final AreaSource source;
    private final long[] keys;
    private final int[] values;
    private final int mask;

    public Area(AreaSource source, int maxCacheSize) {
        maxCacheSize = LayerMath.smallestEncompassingPowerOfTwo(maxCacheSize);
        this.source = source;
        this.keys = new long[maxCacheSize];
        this.values = new int[maxCacheSize];
        this.mask = maxCacheSize - 1;
        Arrays.fill(this.keys, Long.MIN_VALUE);
    }

    public int get(int x, int z) {
        final long key = LayerMath.packChunkPos(x, z);
        final int index = (int) LayerMath.mix(key) & mask;
        if (keys[index] == key) {
            return values[index];
        }
        final int value = source.apply(x, z);
        values[index] = value;
        keys[index] = key;
        return value;
    }
}
