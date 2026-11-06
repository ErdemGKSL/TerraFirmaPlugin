/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import dev.tfp.core.visuals.OverlayFace;
import java.util.Arrays;

/**
 * The ore overlays of one chunk (PAR-INFRA-16): every ore block (local index, ascending), its overlay kind, the mask of
 * its exposed faces (bit {@code 1 << OverlayFace.ordinal()}) and the mask of the exposed faces that are surface outcrops
 * (the open cell is at most {@value ExposedFaces#OUTCROP_DEPTH} blocks below the column's surface; sent within the full
 * view distance). Ores without an exposed face are kept, so the fast path can tell an ore from rock. Immutable: built by
 * {@link ExposedFaces}, published by an atomic swap, read by packet handlers on any thread.
 */
public final class OreOverlayTable {
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int[] local;
    private final short[] kind;
    private final byte[] mask;
    private final byte[] outcrop;
    private final int faces;

    OreOverlayTable(int chunkX, int chunkZ, int minY, int[] local, short[] kind, byte[] mask, byte[] outcrop) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.local = local;
        this.kind = kind;
        this.mask = mask;
        this.outcrop = outcrop;
        int f = 0;
        for (byte m : mask) {
            f += Integer.bitCount(m & 0x3F);
        }
        this.faces = f;
    }

    /** A chunk without ores. */
    public static OreOverlayTable empty(int chunkX, int chunkZ, int minY) {
        return new OreOverlayTable(chunkX, chunkZ, minY, new int[0], new short[0], new byte[0], new byte[0]);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long chunkKey() {
        return OverlayKeys.chunk(chunkX, chunkZ);
    }

    public int minY() {
        return minY;
    }

    /** Ore blocks in the chunk, buried ones included. */
    public int size() {
        return local.length;
    }

    /** Ore blocks with at least one exposed face. */
    public int exposedOres() {
        int n = 0;
        for (byte m : mask) {
            if (m != 0) {
                n++;
            }
        }
        return n;
    }

    /** Exposed faces, i.e. overlay entities a viewer in range gets. */
    public int faceCount() {
        return faces;
    }

    /** Exposed faces that are surface outcrops. */
    public int outcropFaceCount() {
        int n = 0;
        for (int i = 0; i < mask.length; i++) {
            n += Integer.bitCount(mask[i] & outcrop[i] & 0x3F);
        }
        return n;
    }

    /** The entry of a world position, or -1 if it is not an ore of this chunk. */
    public int find(int x, int y, int z) {
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ || y < minY) {
            return -1;
        }
        int i = Arrays.binarySearch(local, OverlayKeys.local(x, y, z, minY));
        return i < 0 ? -1 : i;
    }

    public int local(int i) {
        return local[i];
    }

    public int x(int i) {
        return (chunkX << 4) | OverlayKeys.localX(local[i]);
    }

    public int y(int i) {
        return OverlayKeys.localY(local[i], minY);
    }

    public int z(int i) {
        return (chunkZ << 4) | OverlayKeys.localZ(local[i]);
    }

    public int kind(int i) {
        return kind[i];
    }

    /** The exposed faces of entry {@code i} (bit per {@link OverlayFace#ordinal()}). */
    public int mask(int i) {
        return mask[i] & 0x3F;
    }

    /** The exposed faces of entry {@code i} that are surface outcrops. */
    public int outcrop(int i) {
        return outcrop[i] & mask[i] & 0x3F;
    }

    public boolean exposed(int i, OverlayFace face) {
        return (mask[i] & (1 << face.ordinal())) != 0;
    }

    /** Same ores, kinds and masks (the whole table, as {@link ExposedFaces} would build it). */
    public boolean sameAs(OreOverlayTable o) {
        return chunkX == o.chunkX && chunkZ == o.chunkZ && minY == o.minY && Arrays.equals(local, o.local)
                && Arrays.equals(kind, o.kind) && Arrays.equals(maskedOutcrop(), o.maskedOutcrop())
                && Arrays.equals(mask, o.mask);
    }

    private byte[] maskedOutcrop() {
        byte[] b = new byte[outcrop.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (outcrop[i] & mask[i]);
        }
        return b;
    }

    @Override
    public String toString() {
        return "OreOverlayTable[" + chunkX + "," + chunkZ + ": " + size() + " ores, " + exposedOres() + " exposed, "
                + faces + " faces]";
    }
}
