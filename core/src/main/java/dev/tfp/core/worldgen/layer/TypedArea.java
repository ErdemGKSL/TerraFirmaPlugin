/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/TypedArea.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

/**
 * {@link Area} for object-valued layers (for example region points). Same direct-mapped cache, not synchronized.
 */
public final class TypedArea<A> {
    private final TypedAreaSource<A> factory;
    private final long[] keys;
    private final Object[] values;
    private final int mask;

    public TypedArea(TypedAreaSource<A> factory, int maxCacheSize) {
        maxCacheSize = LayerMath.smallestEncompassingPowerOfTwo(maxCacheSize);
        this.factory = factory;
        this.keys = new long[maxCacheSize];
        this.values = new Object[maxCacheSize];
        this.mask = maxCacheSize - 1;
        java.util.Arrays.fill(this.keys, Long.MIN_VALUE);
    }

    @SuppressWarnings("unchecked")
    public A get(int x, int z) {
        final long key = LayerMath.packChunkPos(x, z);
        final int index = (int) LayerMath.mix(key) & mask;
        if (keys[index] == key) {
            return (A) values[index];
        }
        final A value = factory.apply(x, z);
        values[index] = value;
        keys[index] = key;
        return value;
    }
}
