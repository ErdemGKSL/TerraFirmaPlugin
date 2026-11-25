/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Optional;

/**
 * PAR-FARM-29: a copy of the blocks around one plant (x and z within 2, y within 1) that the survival rules read, taken on the
 * region thread and read on any thread ("snapshot, decide async, apply sync"). A position outside the cube reads as an unloaded
 * nothing. Read-only.
 */
public final class CubeSnapshot implements GrowthWorld {
    static final int RX = 2;
    static final int RY = 1;
    private static final int W = 2 * RX + 1;
    private static final int H = 2 * RY + 1;
    private static final int N = W * H * W;

    private final int cx;
    private final int cy;
    private final int cz;
    private final int minY;
    private final int maxY;
    private final BlockStateRef[] custom = new BlockStateRef[N];
    private final boolean[] air = new boolean[N];
    private final boolean[] water = new boolean[N];
    private final boolean[] loaded = new boolean[N];
    private final byte[] sturdy = new byte[N];
    private final long[] tags = new long[N];
    private final long[] keys = new long[N];

    private CubeSnapshot(int cx, int cy, int cz, int minY, int maxY) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.minY = minY;
        this.maxY = maxY;
    }

    /** Region thread: copies the cube around {@code (x, y, z)} from the live world. */
    public static CubeSnapshot capture(GrowthWorld live, int x, int y, int z) {
        CubeSnapshot s = new CubeSnapshot(x, y, z, live.minY(), live.maxY());
        List<String> tagList = ShapeSurvival.TAGS;
        List<String> keyList = ShapeSurvival.KEYS;
        for (int dx = -RX; dx <= RX; dx++) {
            for (int dy = -RY; dy <= RY; dy++) {
                for (int dz = -RX; dz <= RX; dz++) {
                    int i = s.index(x + dx, y + dy, z + dz);
                    int px = x + dx;
                    int py = y + dy;
                    int pz = z + dz;
                    s.loaded[i] = live.loaded(px, py, pz);
                    if (!s.loaded[i]) {
                        continue;
                    }
                    s.custom[i] = live.custom(px, py, pz).orElse(null);
                    s.air[i] = live.air(px, py, pz);
                    s.water[i] = live.water(px, py, pz);
                    for (Face face : Face.values()) {
                        if (live.sturdy(px, py, pz, face)) {
                            s.sturdy[i] |= (byte) (1 << face.ordinal());
                        }
                    }
                    for (int t = 0; t < tagList.size(); t++) {
                        if (live.tagged(px, py, pz, tagList.get(t))) {
                            s.tags[i] |= 1L << t;
                        }
                    }
                    for (int k = 0; k < keyList.size(); k++) {
                        if (live.is(px, py, pz, keyList.get(k))) {
                            s.keys[i] |= 1L << k;
                        }
                    }
                }
            }
        }
        return s;
    }

    private int index(int x, int y, int z) {
        int dx = x - cx + RX;
        int dy = y - cy + RY;
        int dz = z - cz + RX;
        if (dx < 0 || dx >= W || dy < 0 || dy >= H || dz < 0 || dz >= W) {
            return -1;
        }
        return (dy * W + dz) * W + dx;
    }

    private boolean in(int x, int y, int z) {
        int i = index(x, y, z);
        return i >= 0 && loaded[i];
    }

    @Override
    public boolean loaded(int x, int y, int z) {
        return in(x, y, z);
    }

    @Override
    public Optional<BlockStateRef> custom(int x, int y, int z) {
        return in(x, y, z) ? Optional.ofNullable(custom[index(x, y, z)]) : Optional.empty();
    }

    @Override
    public boolean air(int x, int y, int z) {
        return in(x, y, z) && air[index(x, y, z)];
    }

    @Override
    public boolean water(int x, int y, int z) {
        return in(x, y, z) && water[index(x, y, z)];
    }

    @Override
    public boolean sturdy(int x, int y, int z, Face face) {
        return in(x, y, z) && (sturdy[index(x, y, z)] & (1 << face.ordinal())) != 0;
    }

    @Override
    public boolean tagged(int x, int y, int z, String tag) {
        int t = ShapeSurvival.TAGS.indexOf(tag);
        if (t < 0) {
            throw new IllegalArgumentException("tag not captured: " + tag);
        }
        return in(x, y, z) && (tags[index(x, y, z)] & (1L << t)) != 0;
    }

    @Override
    public boolean is(int x, int y, int z, String key) {
        int k = ShapeSurvival.KEYS.indexOf(key);
        if (k < 0) {
            throw new IllegalArgumentException("key not captured: " + key);
        }
        return in(x, y, z) && (keys[index(x, y, z)] & (1L << k)) != 0;
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
    public boolean set(int x, int y, int z, BlockStateRef state) {
        throw new UnsupportedOperationException("a snapshot is read-only");
    }
}
