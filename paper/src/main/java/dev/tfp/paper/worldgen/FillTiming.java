/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import java.util.Arrays;

/**
 * M2-GEN-15: the per-chunk cost of the full Paper fill path ({@link TfcTerrain#fill}: the core stages, the block writes, the
 * deposits, trees, plants, corals, groundcover and loose rocks), as {@code /tfp debug worldgen <n>} reports it. The chunks are
 * generated into a throwaway {@code ChunkData}, never into the world, so nothing is loaded or saved.
 */
public final class FillTiming {
    /** Chunks generated before the timed ones (terrain caches, JIT), in a separate area; not counted. */
    public static final int WARM_UP = 20;
    /** The first chunk of the sample area: block 100000, 100000, far from spawn and never loaded by the command. */
    public static final int SAMPLE_ORIGIN = 6250;
    /** The warm-up area, away from the sample area. */
    public static final int WARM_UP_ORIGIN = -6250;

    private FillTiming() {
    }

    /** The summary of the timed chunks, in milliseconds. {@code refused} chunks are not in the statistics. */
    public record Stats(int count, int refused, double meanMs, double p95Ms, double minMs, double maxMs) {
    }

    /** The side of the square sample grid of {@code n} chunks. */
    public static int side(int n) {
        return (int) Math.ceil(Math.sqrt(n));
    }

    /**
     * The statistics of {@code nanos} (one entry per chunk; a negative entry is a refused chunk). The p95 is the nearest-rank
     * percentile of the sorted times. All zero when no chunk was timed.
     */
    public static Stats of(long[] nanos) {
        long[] timed = Arrays.stream(nanos).filter(t -> t >= 0).sorted().toArray();
        int refused = nanos.length - timed.length;
        if (timed.length == 0) {
            return new Stats(0, refused, 0, 0, 0, 0);
        }
        double sum = 0;
        for (long t : timed) {
            sum += t;
        }
        int rank = (int) Math.ceil(0.95 * timed.length) - 1;
        return new Stats(timed.length, refused, ms(sum / timed.length), ms(timed[Math.max(0, rank)]), ms(timed[0]),
            ms(timed[timed.length - 1]));
    }

    private static double ms(double nanos) {
        return nanos / 1_000_000.0;
    }
}
