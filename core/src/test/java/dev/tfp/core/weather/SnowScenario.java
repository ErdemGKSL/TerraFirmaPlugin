/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The random scenarios of {@link SnowRuleGoldenTest}: a 3 x 15 x 3 box of classified blocks around a surface position, the
 * heightmap of its nine columns and the seed of TFC's {@code level.random}. The scratch oracle that produced the golden
 * values generated the same scenarios with this same code (the class was copied from it; only the names changed).
 */
final class SnowScenario {
    static final String[] KINDS = {"AIR", "SNOW", "SNOW_PILE", "SNOW_PILEABLE", "WATER", "ICE_PILEABLE_WATER", "ICE_PILEABLE",
        "WATERLOGGED", "LIQUID", "ICE", "ICE_PILE", "SEA_ICE", "OTHER_ICE", "ICICLE", "OTHER"};

    static final class C {
        String kind = "AIR";
        int layers;
        boolean water, survives, sturdy, holds, sky = true, tip;
        String hidden, hiddenAbove;

        C copy() {
            C c = new C();
            c.kind = kind; c.layers = layers; c.water = water; c.survives = survives; c.sturdy = sturdy; c.holds = holds;
            c.sky = sky; c.tip = tip; c.hidden = hidden; c.hiddenAbove = hiddenAbove;
            return c;
        }

        String describe() {
            return kind + "/" + layers + "/" + tip + "/" + hidden + "/" + hiddenAbove;
        }
    }

    final int sx, sy, sz;
    final Map<Long, C> cells = new HashMap<>();
    final int[] heights = new int[9];
    final long rnd;

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    SnowScenario(long seed) {
        Random r = new Random(seed);
        sx = r.nextInt(2001) - 1000;
        sz = r.nextInt(2001) - 1000;
        sy = r.nextInt(24) - 6;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -5; dy <= 9; dy++) {
                    C c = new C();
                    c.kind = r.nextInt(100) < 35 ? "AIR" : KINDS[1 + r.nextInt(KINDS.length - 1)];
                    String k = c.kind;
                    if (k.equals("SNOW") || k.equals("SNOW_PILE")) {
                        c.layers = r.nextInt(3) == 0 ? 1 + r.nextInt(8) : 1;
                    }
                    if (k.equals("WATER") || k.equals("ICE_PILEABLE_WATER") || k.equals("WATERLOGGED")) {
                        c.water = true;
                    } else if (k.equals("LIQUID")) {
                        c.water = r.nextBoolean();
                    } else {
                        c.water = r.nextInt(8) == 0;
                    }
                    c.survives = r.nextInt(3) != 0;
                    c.sturdy = r.nextInt(3) == 0;
                    c.holds = r.nextBoolean();
                    c.sky = r.nextInt(4) != 0;
                    if (k.equals("ICICLE")) {
                        c.tip = r.nextBoolean();
                    }
                    if (k.equals("SNOW_PILE")) {
                        c.hidden = "SNOW_PILEABLE";
                        c.hiddenAbove = r.nextBoolean() ? "SNOW_PILEABLE" : null;
                    }
                    if (k.equals("ICE_PILE")) {
                        c.hidden = r.nextBoolean() ? "WATER" : "ICE_PILEABLE_WATER";
                        c.hiddenAbove = r.nextBoolean() ? "ICE_PILEABLE" : null;
                    }
                    cells.put(key(sx + dx, sy + dy, sz + dz), c);
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            heights[i] = sy + r.nextInt(16) - 5;
        }
        rnd = r.nextLong();
    }

    C at(int x, int y, int z) {
        C c = cells.get(key(x, y, z));
        return c != null ? c : new C();
    }

    int height(int x, int z) {
        return heights[(x - sx + 1) * 3 + (z - sz + 1)];
    }

    /** FNV-1a 64 of a line, chained. */
    static long fnv(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= '\n';
        h *= 0x100000001b3L;
        return h;
    }

    static final long FNV_START = 0xcbf29ce484222325L;
}
