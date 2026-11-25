/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real chunk climate at a block position, for the weather and HUD readings (replaces {@link ChunkClimate#NEUTRAL}).
 * <p>
 * The chunk data is not stored by the terrain: {@code TfcTerrain} computes it during worldgen and drops it. So it is
 * recomputed from the world seed and the world's TFC settings with {@link ChunkDataGenerator#forLevel}, which is pure and
 * thread-safe, and the generator per seed and settings is memoised here (a few levels at most). Any thread.
 */
public final class ChunkClimateSource {
    /** Levels kept in memory. A level beyond this is built per call, which is rare and still correct. */
    private static final int MAX_LEVELS = 4;

    /** A level: its seed and its TFC region settings (PAR-WORLD-51: the world's own, not TFC's preset). */
    private record Level(long seed, RegionSettings settings) {}

    private static final Map<Level, ChunkDataGenerator> LEVELS = new ConcurrentHashMap<>();

    private ChunkClimateSource() {
    }

    /**
     * @return The chunk climate of the block column {@code (x, z)} of the level with {@code worldSeed}, with the TFC settings
     * of the loaded world with that seed ({@link WorldSettingsLookup#forSeed}).
     */
    public static ChunkClimate climateAt(long worldSeed, int x, int z) {
        return climateAt(worldSeed, WorldSettingsLookup.forSeed(worldSeed).regionSettings(), x, z);
    }

    /** @return The chunk climate of the block column {@code (x, z)} of a level with the given region settings. */
    public static ChunkClimate climateAt(long worldSeed, RegionSettings settings, int x, int z) {
        return generator(worldSeed, settings).climateAt(x, z);
    }

    /** The chunk data generator of a level, memoised per seed and settings. */
    static ChunkDataGenerator generator(long worldSeed, RegionSettings settings) {
        final Level level = new Level(worldSeed, settings);
        ChunkDataGenerator known = LEVELS.get(level);
        if (known != null) {
            return known;
        }
        if (LEVELS.size() >= MAX_LEVELS) {
            return ChunkDataGenerator.forLevel(worldSeed, settings);
        }
        return LEVELS.computeIfAbsent(level, l -> ChunkDataGenerator.forLevel(l.seed(), l.settings()));
    }
}
