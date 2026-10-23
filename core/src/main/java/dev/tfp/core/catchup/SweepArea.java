/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.catchup;

import dev.tfp.core.world.ChunkPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The visible-area sweep of PAR-INFRA-11 (NP-SIM-01): which chunks around a player are caught up, and when a chunk is
 * due again. Pure; any thread.
 */
public final class SweepArea {
    private SweepArea() {
    }

    /**
     * The chunks within {@code radius} (Chebyshev, in chunks) of ({@code centerX}, {@code centerZ}), nearest first
     * (then by x, z), so the chunks the player is in and looks at change first.
     */
    public static List<ChunkPos> around(int centerX, int centerZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }
        List<ChunkPos> out = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(new ChunkPos(centerX + dx, centerZ + dz));
            }
        }
        out.sort(Comparator.<ChunkPos>comparingInt(c -> Math.max(Math.abs(c.x() - centerX), Math.abs(c.z() - centerZ)))
                .thenComparingInt(c -> Math.abs(c.x() - centerX) + Math.abs(c.z() - centerZ))
                .thenComparingInt(ChunkPos::x).thenComparingInt(ChunkPos::z));
        return out;
    }

    /**
     * Whether a chunk last caught up at {@code lastTick} ({@code Long.MIN_VALUE} = never) is due at {@code nowTick}
     * with a sweep every {@code periodTicks}. Any monotonic unit; a clock that went back makes the chunk due.
     */
    public static boolean due(long lastTick, long nowTick, long periodTicks) {
        return lastTick == Long.MIN_VALUE || nowTick - lastTick >= periodTicks || nowTick < lastTick;
    }
}
