/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.carver;

import java.util.BitSet;

/**
 * The positions of one chunk that lie inside a cave tunnel, a cave room or a canyon: the role of Minecraft's
 * {@code CarvingMask}. A position in the mask is offered to the carve decision ({@link CaveCarver#carve}); the mask says
 * nothing about what the position becomes. The bit of a position is {@code x | z << 4 | (y - minY) << 8} with chunk-local
 * x and z, the layout of {@code CarvingMask}.
 *
 * <p>Written by one thread while {@link TunnelCarvers#mask} builds it, and only read after that.
 */
public final class TunnelMask {
    private final int minY;
    private final int height;
    private final BitSet bits;

    TunnelMask(int minY, int height) {
        this.minY = minY;
        this.height = height;
        this.bits = new BitSet(256 * height);
    }

    /** The lowest block y of the world the mask was built for. */
    public int minY() {
        return minY;
    }

    /** The number of block layers of the world the mask was built for. */
    public int height() {
        return height;
    }

    /**
     * @param localX the chunk-local x, 0 to 15
     * @param y      the block y; a y outside the world is never in a tunnel
     * @param localZ the chunk-local z, 0 to 15
     * @return whether the position is inside a tunnel shape
     */
    public boolean contains(int localX, int y, int localZ) {
        final int layer = y - minY;
        return layer >= 0 && layer < height && bits.get(index(localX, layer, localZ));
    }

    /** Whether no position of the chunk is inside a tunnel shape. */
    public boolean isEmpty() {
        return bits.isEmpty();
    }

    /** The number of positions inside a tunnel shape. */
    public int count() {
        return bits.cardinality();
    }

    /** The mask as longs, bit {@code x | z << 4 | (y - minY) << 8}, without trailing zero words (for tests and hashing). */
    public long[] toLongArray() {
        return bits.toLongArray();
    }

    /** Marks a position; {@code y} must be inside the world. */
    void add(int localX, int y, int localZ) {
        bits.set(index(localX, y - minY, localZ));
    }

    private static int index(int localX, int layer, int localZ) {
        return localX & 15 | (localZ & 15) << 4 | layer << 8;
    }
}
