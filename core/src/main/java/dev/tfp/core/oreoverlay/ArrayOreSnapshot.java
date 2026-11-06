/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.util.Arrays;

/**
 * An {@link OreSnapshot} over plain arrays (one byte code per cell, one surface per column): what tests build, and what
 * a caller fills when it has the chunk's cells at hand. {@link Builder} is not thread-safe; the snapshot it builds is
 * immutable.
 */
public final class ArrayOreSnapshot implements OreSnapshot {
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxY;
    private final byte[] codes;
    private final int[] surface;
    private final int[] ores;

    private ArrayOreSnapshot(Builder b) {
        this.chunkX = b.chunkX;
        this.chunkZ = b.chunkZ;
        this.minY = b.minY;
        this.maxY = b.maxY;
        this.codes = b.codes.clone();
        this.surface = b.surface.clone();
        int n = 0;
        for (byte c : codes) {
            if (c >= 0) {
                n++;
            }
        }
        int[] o = new int[n];
        int k = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i] >= 0) {
                o[k++] = i;
            }
        }
        this.ores = o;
    }

    /** A chunk filled with {@code fill} (a {@link CellLookup} code) and no heightmap. */
    public static Builder builder(int chunkX, int chunkZ, int minY, int maxY, int fill) {
        return new Builder(chunkX, chunkZ, minY, maxY, fill);
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
        return ores.clone();
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
        return codes[OverlayKeys.local(x, y, z, minY)];
    }

    @Override
    public int surface(int x, int z) {
        return inChunk(x, z) ? surface[((z & 15) << 4) | (x & 15)] : NO_SURFACE;
    }

    /** Fills an {@link ArrayOreSnapshot}. */
    public static final class Builder {
        private final int chunkX;
        private final int chunkZ;
        private final int minY;
        private final int maxY;
        private final byte[] codes;
        private final int[] surface = new int[256];

        private Builder(int chunkX, int chunkZ, int minY, int maxY, int fill) {
            if (maxY <= minY || ((maxY - minY) & 15) != 0) {
                throw new IllegalArgumentException("height must be a positive multiple of 16: " + minY + ".." + maxY);
            }
            if (fill < ExposureRule.OPEN || fill > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("fill must be SOLID, OPEN or an ore kind: " + fill);
            }
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minY = minY;
            this.maxY = maxY;
            this.codes = new byte[(maxY - minY) << 8];
            Arrays.fill(codes, (byte) fill);
            Arrays.fill(surface, NO_SURFACE);
        }

        /** Sets the code of a world position in this chunk. */
        public Builder set(int x, int y, int z, int code) {
            if ((x >> 4) != chunkX || (z >> 4) != chunkZ || y < minY || y >= maxY) {
                throw new IllegalArgumentException("outside the chunk: " + x + " " + y + " " + z);
            }
            if (code < ExposureRule.OPEN || code > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("code must be SOLID, OPEN or an ore kind: " + code);
            }
            codes[OverlayKeys.local(x, y, z, minY)] = (byte) code;
            return this;
        }

        /** Sets the {@code WORLD_SURFACE} heightmap of a column in this chunk. */
        public Builder surface(int x, int z, int y) {
            surface[((z & 15) << 4) | (x & 15)] = y;
            return this;
        }

        public ArrayOreSnapshot build() {
            return new ArrayOreSnapshot(this);
        }
    }
}
