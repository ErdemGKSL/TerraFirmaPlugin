/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;

/**
 * The 2D noises of the surface rules, with TFC's seeds and frequencies: the soil patch noise that breaks up the transitions
 * between surface types, the sand and gravel beach noise, and the shore tide level. Pure and thread-safe.
 */
public final class SurfaceNoise {
    /** TFC's {@code SoilSurfaceState.PATCH_NOISE}: the blobs that choose between two soils of a transition. */
    public static final Noise2D PATCH = new OpenSimplex2D(18273952837592L).octaves(2).spread(0.04f);

    /** TFC's {@code SurfaceStates.sandGravelBeachNoise()}: above the cutoff of the temperature, a beach is gravel. */
    public static final Noise2D SAND_GRAVEL_BEACH = new OpenSimplex2D(124154L).octaves(3).spread(0.00002f);

    /** The sea level in TFC's {@code ChunkData.SEA_LEVEL_Y}; the tide noise is centred on it. */
    private static final int SEA_LEVEL_Y = ChunkData.SEA_LEVEL_Y;

    private SurfaceNoise() {}

    /**
     * TFC's {@code BiomeNoise.shoreTideLevelNoise(seed)}: the shore tide level, between sea level minus 6 and sea level plus 6,
     * clamped to the range from sea level to sea level plus 4, with a small wiggle on top. The column's tide level is
     * {@code (int)} of this value.
     *
     * @param levelSeed the level seed ({@code Seed.seed()}), not the stream
     */
    public static Noise2D shoreTide(long levelSeed) {
        return new OpenSimplex2D(levelSeed).octaves(3).spread(0.005f).scaled(SEA_LEVEL_Y - 6, SEA_LEVEL_Y + 6)
            .clamped(SEA_LEVEL_Y, SEA_LEVEL_Y + 4).add(new OpenSimplex2D(levelSeed).spread(0.03));
    }
}
