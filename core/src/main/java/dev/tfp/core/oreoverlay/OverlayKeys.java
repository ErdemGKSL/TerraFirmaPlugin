/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

/**
 * Packed keys of the ore-overlay runtime (PAR-INFRA-16). Pure.
 *
 * <ul>
 *   <li>chunk key: {@code (x << 32) | (z & 0xFFFFFFFF)}, the vanilla {@code ChunkPos.toLong} layout;</li>
 *   <li>block key: vanilla {@code BlockPos.asLong} (26 bits x, 26 bits z, 12 bits y);</li>
 *   <li>local index of a block in its chunk: {@code ((y - minY) << 8) | (z & 15) << 4 | (x & 15)} (20 bits for a
 *       4096-high world);</li>
 *   <li>face key: {@code local << 3 | face ordinal} ({@link dev.tfp.core.visuals.OverlayFace}), unique in its chunk.</li>
 * </ul>
 */
public final class OverlayKeys {
    private OverlayKeys() {
    }

    public static long chunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long chunk) {
        return (int) (chunk >> 32);
    }

    public static int chunkZ(long chunk) {
        return (int) chunk;
    }

    public static long block(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int blockX(long block) {
        return (int) (block >> 38);
    }

    public static int blockY(long block) {
        return (int) (block << 52 >> 52);
    }

    public static int blockZ(long block) {
        return (int) (block << 26 >> 38);
    }

    public static int local(int x, int y, int z, int minY) {
        return ((y - minY) << 8) | ((z & 15) << 4) | (x & 15);
    }

    public static int localX(int local) {
        return local & 15;
    }

    public static int localZ(int local) {
        return (local >> 4) & 15;
    }

    /** The y of a local index, given the world's minimum y. */
    public static int localY(int local, int minY) {
        return (local >>> 8) + minY;
    }

    public static int face(int local, int face) {
        return (local << 3) | face;
    }

    public static int faceLocal(int faceKey) {
        return faceKey >>> 3;
    }

    public static int faceOrdinal(int faceKey) {
        return faceKey & 7;
    }
}
