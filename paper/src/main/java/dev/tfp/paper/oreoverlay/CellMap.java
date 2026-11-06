/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import dev.tfp.core.oreoverlay.CellLookup;
import dev.tfp.core.oreoverlay.OverlayKeys;
import java.util.HashMap;
import java.util.Map;

/**
 * Cells read on their owners' region threads (code and column surface per block key), handed to the CPU pool as an
 * immutable {@link CellLookup}. {@link #pack}/{@link #codeOf}/{@link #surfaceOf} encode one read as a long.
 */
final class CellMap implements CellLookup {
    static final CellMap EMPTY = new CellMap(Map.of(), Map.of());

    private final Map<Long, Integer> codes;
    private final Map<Long, Integer> surfaces;

    private CellMap(Map<Long, Integer> codes, Map<Long, Integer> surfaces) {
        this.codes = codes;
        this.surfaces = surfaces;
    }

    static long pack(int code, int surface) {
        return ((long) surface << 32) | (code & 0xFFFFFFFFL);
    }

    static int codeOf(long cell) {
        return (int) cell;
    }

    static int surfaceOf(long cell) {
        return (int) (cell >> 32);
    }

    @Override
    public int code(int x, int y, int z) {
        Integer c = codes.get(OverlayKeys.block(x, y, z));
        return c == null ? UNKNOWN : c;
    }

    @Override
    public int surface(int x, int z) {
        Integer s = surfaces.get(OverlayKeys.chunk(x, z));
        return s == null ? NO_SURFACE : s;
    }

    int size() {
        return codes.size();
    }

    /** Collects reads; not thread-safe (one region task fills one builder). */
    static final class Builder {
        private final Map<Long, Integer> codes = new HashMap<>();
        private final Map<Long, Integer> surfaces = new HashMap<>();

        Builder put(long block, long cell) {
            codes.put(block, codeOf(cell));
            int s = surfaceOf(cell);
            if (s != NO_SURFACE) {
                surfaces.put(OverlayKeys.chunk(OverlayKeys.blockX(block), OverlayKeys.blockZ(block)), s);
            }
            return this;
        }

        boolean has(long block) {
            return codes.containsKey(block);
        }

        Builder putAll(CellMap other) {
            codes.putAll(other.codes);
            surfaces.putAll(other.surfaces);
            return this;
        }

        CellMap build() {
            return new CellMap(Map.copyOf(codes), Map.copyOf(surfaces));
        }
    }
}
