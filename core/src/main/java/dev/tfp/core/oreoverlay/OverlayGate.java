/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import dev.tfp.core.visuals.OverlayFace;
import java.util.ArrayList;
import java.util.List;

/**
 * Distance gating of the overlays (PAR-INFRA-16, design §3.4), from {@code ore-overlay.cutoff-blocks} and
 * {@code ore-overlay.surface-outcrops-full-range}. The client draws a display entity only within
 * {@code 64 * view_range} blocks (a sphere, per entity, per frame), so the visible edge is that sphere; the server sends a
 * chunk's faces when the chunk is within {@link #radiusChunks()} (Chebyshev) of the viewer's chunk, which covers the
 * sphere. Surface outcrops are sent in every chunk the viewer has, with the full-range view range. Pure.
 *
 * @param cutoffBlocks      the client cutoff in blocks; 0 sends no cave overlays at all
 * @param outcropsFullRange whether surface outcrops are sent beyond the cutoff
 */
public record OverlayGate(int cutoffBlocks, boolean outcropsFullRange) {
    public OverlayGate {
        if (cutoffBlocks < 0) {
            throw new IllegalArgumentException("cutoff must be >= 0");
        }
    }

    /** {@code ceil(cutoff / 16) + 1}: 4 chunks (81 chunks) for the default 48 m. */
    public int radiusChunks() {
        return cutoffBlocks == 0 ? -1 : (cutoffBlocks + 15) / 16 + 1;
    }

    /** The {@code view_range} of a cave overlay: {@code cutoff / 64} (0.75 for 48 m, at entity distance 100 %). */
    public float viewRange() {
        return cutoffBlocks / 64f;
    }

    /** The {@code view_range} of an outcrop overlay for a server view distance of {@code viewDistanceChunks}. */
    public static float fullViewRange(int viewDistanceChunks) {
        return Math.max(1, viewDistanceChunks) * 16 / 64f;
    }

    /** True if a chunk is within the cave-overlay radius of the viewer's chunk. */
    public boolean near(int viewerChunkX, int viewerChunkZ, int chunkX, int chunkZ) {
        return Math.max(Math.abs(chunkX - viewerChunkX), Math.abs(chunkZ - viewerChunkZ)) <= radiusChunks();
    }

    /**
     * The faces of {@code table} a viewer at {@code (vx, vy, vz)} should have: all of them when the chunk is
     * {@code near}, else only the surface outcrops (when enabled).
     */
    public List<ViewerSet.Wanted> wanted(OreOverlayTable table, boolean near, double vx, double vy, double vz) {
        if (!near && !outcropsFullRange) {
            return List.of();
        }
        List<ViewerSet.Wanted> out = new ArrayList<>(near ? table.faceCount() : 8);
        for (int i = 0; i < table.size(); i++) {
            int mask = table.mask(i);
            if (mask == 0) {
                continue;
            }
            int outcrop = table.outcrop(i);
            if (!near && outcrop == 0) {
                continue;
            }
            double dx = table.x(i) + 0.5 - vx;
            double dy = table.y(i) + 0.5 - vy;
            double dz = table.z(i) + 0.5 - vz;
            double d = dx * dx + dy * dy + dz * dz;
            for (OverlayFace f : OverlayFace.values()) {
                int bit = 1 << f.ordinal();
                if ((mask & bit) == 0) {
                    continue;
                }
                boolean o = (outcrop & bit) != 0;
                if (!near && !o) {
                    continue;
                }
                // An outcrop face carries the full range wherever it is sent, so it does not respawn at the radius.
                out.add(new ViewerSet.Wanted(OverlayKeys.face(table.local(i), f.ordinal()), table.kind(i),
                        o && outcropsFullRange, d));
            }
        }
        return out;
    }
}
