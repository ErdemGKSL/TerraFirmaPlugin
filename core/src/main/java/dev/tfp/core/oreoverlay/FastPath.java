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
 * The provisional overlay change for one block change a viewer is about to see (PAR-INFRA-16, design §3.5): decided on
 * the Netty thread from the published tables and the new state of the changed cell, so that the overlay spawn or removal
 * travels with the block change and shows in the same frame. The authoritative {@link ExposedFaces#patch} one or more
 * ticks later corrects a wrong guess (a concurrent change in the same neighbourhood, an ore placed). Pure, O(7) lookups.
 */
public final class FastPath {
    /** The new state is known not to be an ore. */
    public static final int NOT_ORE = -1;
    /** Whether the new state is an ore is not known (the packet carries the host rock's visual). */
    public static final int UNKNOWN_ORE = -2;

    private static final OverlayFace[] FACES = OverlayFace.values();

    private FastPath() {
    }

    /** The published tables by chunk; null where none is published. */
    @FunctionalInterface
    public interface Tables {
        OreOverlayTable table(int chunkX, int chunkZ);
    }

    /** One face overlay of the ore at {@code (x, y, z)} that appears ({@code visible}) or vanishes. */
    public record FaceChange(int x, int y, int z, OverlayFace face, int kind, boolean visible, boolean outcrop) {
    }

    /**
     * The face changes for the cell {@code (x, y, z)} becoming a state that occludes or not ({@code newOccludes}), of ore
     * kind {@code newKind} ({@link #NOT_ORE}, {@link #UNKNOWN_ORE} or a kind).
     *
     * <ul>
     *   <li>an ore cell that stops occluding, or is known to be no ore now, loses all its faces;</li>
     *   <li>an ore cell whose kind is known to change swaps the overlay of its exposed faces;</li>
     *   <li>every neighbouring ore gets its face towards the cell when the new state does not occlude and loses it when it
     *       does.</li>
     * </ul>
     * A cell that becomes an ore gets its own faces only from the rebuild (the tables know nothing about its neighbours).
     */
    public static List<FaceChange> decide(Tables tables, int x, int y, int z, boolean newOccludes, int newKind) {
        List<FaceChange> out = new ArrayList<>(6);
        OreOverlayTable own = tables.table(x >> 4, z >> 4);
        int i = own == null ? -1 : own.find(x, y, z);
        if (i >= 0) {
            int mask = own.mask(i);
            int outcrop = own.outcrop(i);
            boolean gone = !newOccludes || newKind == NOT_ORE;
            boolean swapped = !gone && newKind >= 0 && newKind != own.kind(i);
            if (gone || swapped) {
                for (OverlayFace f : FACES) {
                    if ((mask & (1 << f.ordinal())) != 0) {
                        boolean o = (outcrop & (1 << f.ordinal())) != 0;
                        out.add(new FaceChange(x, y, z, f, own.kind(i), false, o));
                        if (swapped) {
                            out.add(new FaceChange(x, y, z, f, newKind, true, o));
                        }
                    }
                }
            }
        }
        for (OverlayFace f : FACES) {
            int nx = x + f.dx();
            int ny = y + f.dy();
            int nz = z + f.dz();
            OreOverlayTable t = (nx >> 4) == (x >> 4) && (nz >> 4) == (z >> 4) ? own : tables.table(nx >> 4, nz >> 4);
            int j = t == null ? -1 : t.find(nx, ny, nz);
            if (j < 0) {
                continue;
            }
            OverlayFace towards = ExposedFaces.opposite(f);
            boolean was = t.exposed(j, towards);
            boolean now = !newOccludes;
            if (was != now) {
                boolean o = (t.outcrop(j) & (1 << towards.ordinal())) != 0;
                out.add(new FaceChange(nx, ny, nz, towards, t.kind(j), now, o));
            }
        }
        return out;
    }
}
