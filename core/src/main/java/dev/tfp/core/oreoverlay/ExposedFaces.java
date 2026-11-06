/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import dev.tfp.core.oreoverlay.census.ExposureRule;
import dev.tfp.core.visuals.OverlayFace;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which faces of a chunk's ores are exposed (PAR-INFRA-16, design {@code docs/research/ore-entity-renderer-design.md}
 * §3.3). TFC's ore models ({@code assets/tfc/models/block/ore.json}, {@code ore_column.json} @ c3b4d9536) give every
 * overlay face a {@code cullface}, so the overlay is drawn exactly where vanilla culling keeps the face, i.e. where the
 * neighbour does not occlude it ({@link ExposureRule}); this decides the same thing for our overlay entities. Pure.
 *
 * <ul>
 *   <li>{@link #compute}: the whole table of one chunk from its {@link OreSnapshot} plus the 1-block border, which the
 *       owners of the neighbour chunks read ({@link #borderNeeds} lists the cells);</li>
 *   <li>{@link #patch}: the table after some cells changed, from the changed cells and (for cells that became ores) their
 *       six neighbours; converges to {@link #compute} of the final world.</li>
 * </ul>
 */
public final class ExposedFaces {
    /** A face whose open cell lies at most this far below the column's surface is a surface outcrop. */
    public static final int OUTCROP_DEPTH = 8;

    private static final OverlayFace[] FACES = OverlayFace.values();

    private ExposedFaces() {
    }

    /** The opposite face (DOWN/UP, NORTH/SOUTH, WEST/EAST pair up in {@link OverlayFace}'s order). */
    public static OverlayFace opposite(OverlayFace f) {
        return FACES[f.ordinal() ^ 1];
    }

    /** True if an open cell at {@code y} under a column with heightmap {@code surface} counts as a surface outcrop. */
    public static boolean outcrop(int y, int surface) {
        return surface != CellLookup.NO_SURFACE && y >= surface - OUTCROP_DEPTH;
    }

    /** Block keys ({@link OverlayKeys#block}) of the cells outside the chunk next to its ores, for the border read. */
    public static long[] borderNeeds(OreSnapshot s) {
        TreeSet<Long> out = new TreeSet<>();
        int minY = s.minY();
        for (int local : s.ores()) {
            int x = (s.chunkX() << 4) | OverlayKeys.localX(local);
            int y = OverlayKeys.localY(local, minY);
            int z = (s.chunkZ() << 4) | OverlayKeys.localZ(local);
            for (OverlayFace f : FACES) {
                int nx = x + f.dx();
                int nz = z + f.dz();
                if (!s.inChunk(nx, nz)) {
                    out.add(OverlayKeys.block(nx, y, nz));
                }
            }
        }
        long[] a = new long[out.size()];
        int i = 0;
        for (long k : out) {
            a[i++] = k;
        }
        return a;
    }

    /** The table of the snapshot's chunk; border cells come from {@code border} ({@link CellLookup#UNKNOWN} = hidden). */
    public static OreOverlayTable compute(OreSnapshot s, CellLookup border) {
        int[] ores = s.ores();
        int n = ores.length;
        short[] kind = new short[n];
        byte[] mask = new byte[n];
        byte[] outcrop = new byte[n];
        int minY = s.minY();
        for (int i = 0; i < n; i++) {
            int x = (s.chunkX() << 4) | OverlayKeys.localX(ores[i]);
            int y = OverlayKeys.localY(ores[i], minY);
            int z = (s.chunkZ() << 4) | OverlayKeys.localZ(ores[i]);
            kind[i] = (short) s.code(x, y, z);
            int m = 0;
            int o = 0;
            for (OverlayFace f : FACES) {
                int nx = x + f.dx();
                int ny = y + f.dy();
                int nz = z + f.dz();
                CellLookup from = s.inChunk(nx, nz) || ny < minY || ny >= s.maxY() ? s : border;
                if (ExposureRule.exposes(from.code(nx, ny, nz))) {
                    m |= 1 << f.ordinal();
                    if (outcrop(ny, from.surface(nx, nz))) {
                        o |= 1 << f.ordinal();
                    }
                }
            }
            mask[i] = (byte) m;
            outcrop[i] = (byte) o;
        }
        return new OreOverlayTable(s.chunkX(), s.chunkZ(), minY, ores, kind, mask, outcrop);
    }

    private static final class Entry {
        final int kind;
        int mask;
        int outcrop;
        final boolean known;

        Entry(int kind, int mask, int outcrop, boolean known) {
            this.kind = kind;
            this.mask = mask;
            this.outcrop = outcrop;
            this.known = known;
        }
    }

    /**
     * {@code table} after the cells {@code changed} (block keys, any chunk) changed. {@code cells} must know the new code
     * (and surface) of every changed cell and the six neighbours of every changed cell that is an ore now; a neighbour it
     * does not know keeps its old face bit, or reads as an ore when the table holds one there. Height limits:
     * {@code maxY} is the world's height limit.
     */
    public static OreOverlayTable patch(OreOverlayTable table, int maxY, CellLookup cells, long[] changed) {
        int cx = table.chunkX();
        int cz = table.chunkZ();
        int minY = table.minY();
        TreeMap<Integer, Entry> entries = new TreeMap<>();
        for (int i = 0; i < table.size(); i++) {
            entries.put(table.local(i), new Entry(table.kind(i), table.mask(i), table.outcrop(i), true));
        }
        TreeSet<Integer> affected = new TreeSet<>();
        for (long key : changed) {
            int x = OverlayKeys.blockX(key);
            int y = OverlayKeys.blockY(key);
            int z = OverlayKeys.blockZ(key);
            if ((x >> 4) == cx && (z >> 4) == cz && y >= minY && y < maxY) {
                int code = cells.code(x, y, z);
                int local = OverlayKeys.local(x, y, z, minY);
                if (code >= 0) {
                    Entry old = entries.get(local);
                    if (old == null || old.kind != code) {
                        entries.put(local, old == null ? new Entry(code, 0, 0, false)
                                : new Entry(code, old.mask, old.outcrop, old.known));
                    }
                    affected.add(local);
                } else if (code != CellLookup.UNKNOWN) {
                    entries.remove(local);
                }
            }
            for (OverlayFace f : FACES) {
                int nx = x + f.dx();
                int ny = y + f.dy();
                int nz = z + f.dz();
                if ((nx >> 4) == cx && (nz >> 4) == cz && ny >= minY && ny < maxY) {
                    int nl = OverlayKeys.local(nx, ny, nz, minY);
                    if (entries.containsKey(nl)) {
                        affected.add(nl);
                    }
                }
            }
        }
        for (int local : affected) {
            Entry e = entries.get(local);
            if (e == null) {
                continue;
            }
            int x = (cx << 4) | OverlayKeys.localX(local);
            int y = OverlayKeys.localY(local, minY);
            int z = (cz << 4) | OverlayKeys.localZ(local);
            int m = 0;
            int o = 0;
            for (OverlayFace f : FACES) {
                int bit = 1 << f.ordinal();
                int nx = x + f.dx();
                int ny = y + f.dy();
                int nz = z + f.dz();
                int code;
                int surface;
                if (ny < minY) {
                    code = ExposureRule.SOLID;
                    surface = CellLookup.NO_SURFACE;
                } else if (ny >= maxY) {
                    code = ExposureRule.OPEN;
                    surface = cells.surface(nx, nz);
                } else {
                    code = cells.code(nx, ny, nz);
                    surface = cells.surface(nx, nz);
                    if (code == CellLookup.UNKNOWN && (nx >> 4) == cx && (nz >> 4) == cz
                            && entries.containsKey(OverlayKeys.local(nx, ny, nz, minY))) {
                        code = 0; // an ore of this table: occluding
                    }
                }
                if (code == CellLookup.UNKNOWN) {
                    m |= e.mask & bit;
                    o |= e.outcrop & bit;
                } else if (ExposureRule.exposes(code)) {
                    m |= bit;
                    if (surface == CellLookup.NO_SURFACE) {
                        o |= e.outcrop & bit;
                    } else if (outcrop(ny, surface)) {
                        o |= bit;
                    }
                }
            }
            e.mask = m;
            e.outcrop = o;
        }
        int n = entries.size();
        int[] local = new int[n];
        short[] kind = new short[n];
        byte[] mask = new byte[n];
        byte[] outcrop = new byte[n];
        int i = 0;
        for (Map.Entry<Integer, Entry> me : entries.entrySet()) {
            local[i] = me.getKey();
            kind[i] = (short) me.getValue().kind;
            mask[i] = (byte) me.getValue().mask;
            outcrop[i] = (byte) me.getValue().outcrop;
            i++;
        }
        return new OreOverlayTable(cx, cz, minY, local, kind, mask, outcrop);
    }

    /** The block keys of the six neighbours of a cell (what {@link #patch} needs for a cell that became an ore). */
    public static long[] neighbours(int x, int y, int z) {
        long[] n = new long[FACES.length];
        for (OverlayFace f : FACES) {
            n[f.ordinal()] = OverlayKeys.block(x + f.dx(), y + f.dy(), z + f.dz());
        }
        return n;
    }
}
