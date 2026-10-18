/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real chunk climate at a block position, for the weather and HUD readings (replaces {@link ChunkClimate#NEUTRAL}).
 * <p>
 * The chunk data is not stored by the terrain: {@code TfcTerrain} computes it during worldgen and drops it. So it is
 * recomputed from the world seed with {@link ChunkDataGenerator#forLevel}, which is pure and thread-safe, and the generator
 * per seed is memoised here (a few levels at most). Any thread.
 */
public final class ChunkClimateSource {
    /** Levels kept in memory. A seed beyond this is built per call, which is rare and still correct. */
    private static final int MAX_LEVELS = 4;

    private static final Map<Long, ChunkDataGenerator> LEVELS = new ConcurrentHashMap<>();

    private ChunkClimateSource() {
    }

    /** @return The chunk climate of the block column {@code (x, z)} of the level with {@code worldSeed}. */
    public static ChunkClimate climateAt(long worldSeed, int x, int z) {
        return generator(worldSeed).climateAt(x, z);
    }

    /** The chunk data generator of a level, memoised per seed. */
    static ChunkDataGenerator generator(long worldSeed) {
        ChunkDataGenerator known = LEVELS.get(worldSeed);
        if (known != null) {
            return known;
        }
        if (LEVELS.size() >= MAX_LEVELS) {
            return ChunkDataGenerator.forLevel(worldSeed);
        }
        return LEVELS.computeIfAbsent(worldSeed, ChunkDataGenerator::forLevel);
    }
}
