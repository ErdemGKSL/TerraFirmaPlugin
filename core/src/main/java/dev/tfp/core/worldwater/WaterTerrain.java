/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java @ c3b4d9536 (initRandomState: the order the region
 *       generator, the chunk data generator and the biome layer take their seeds)
 */
package dev.tfp.core.worldwater;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.volcano.CinderCones;

/**
 * The surface heights, biomes and rivers of a TFC overworld, rebuilt from its seed the way the chunk generator builds them, so
 * the water of a generated chunk can be classified after generation (PAR-FLUID-03). Thread-safe; {@link #chunk} is the
 * worldgen column pass and belongs on a worker thread.
 */
public final class WaterTerrain {
    private final long seed;
    private final SurfaceHeights heights;

    /** @param seed the world seed the generator used */
    public WaterTerrain(long seed) {
        this.seed = seed;
        final Seed stream = Seed.of(seed);
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        // The chunk data generator takes its seeds from the stream before the biome layer, as in TFC
        new ChunkDataGenerator(regions, stream);
        this.heights = SurfaceHeights.create(seed, regions, stream, new CinderCones(seed));
    }

    public long seed() {
        return seed;
    }

    /** The TFC biome of a column as the biome source gives it (quart resolution, with the river biome). Any thread. */
    public int biomeAt(int blockX, int blockZ) {
        return heights.biomeAt(blockX, blockZ);
    }

    /** The water facts of a chunk (a worldgen column pass: call it off the region threads). */
    public ChunkWater chunk(int chunkX, int chunkZ) {
        return ChunkWater.of(heights.chunk(chunkX, chunkZ));
    }
}
