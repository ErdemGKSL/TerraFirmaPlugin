/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Properties of {@link SpawnSearch} over synthetic worlds: the spawn is on dry land, the y is the surface plus one, the search
 * is deterministic, and it stays inside the search radius plus the chunk spiral. Fixed seeds, so the test is reproducible.
 */
class SpawnSearchPropertyTest {
    private static final int SEA = 63;
    private static final long[] SEEDS = {0L, 1L, 42L, -7L, 123_456_789L, -987_654_321L, Long.MAX_VALUE};
    /** The spiral covers chunk offsets -15..16 from the biome's chunk, so the block offset is at most 16 chunks plus a chunk. */
    private static final int SPIRAL_SLACK = 16 * 16 + 16;

    /** A chunk-constant synthetic world: heights 40..99 (land above 63 for most chunks), spawnable biome in 2 of 3 chunks. */
    private static final class World {
        final long seed;

        World(long seed) {
            this.seed = seed;
        }

        int height(int x, int z) {
            return 40 + Math.floorMod(mix(seed, x >> 4, z >> 4), 60);
        }

        int biome(int x, int z) {
            return Math.floorMod(mix(seed ^ 0x5DEECE66DL, x >> 4, z >> 4), 3) == 0 ? TfcBiomes.OCEAN : TfcBiomes.PLAINS;
        }

        private static int mix(long seed, int a, int b) {
            long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            return (int) (h ^ (h >>> 32));
        }
    }

    private static Optional<SpawnSearch.Spawn> spawn(World world) {
        IntBinaryOperator biome = world::biome;
        IntBinaryOperator height = world::height;
        return SpawnSearch.find(world.seed, SpawnSearch.DEFAULT_CENTER, SpawnSearch.DEFAULT_CENTER,
            SpawnSearch.DEFAULT_SPAWN_DISTANCE, SEA, biome, height);
    }

    @Test
    void theSpawnIsOnDryLandWithYOnTopOfTheSurface() {
        int found = 0;
        for (long seed : SEEDS) {
            World world = new World(seed);
            Optional<SpawnSearch.Spawn> spawn = spawn(world);
            if (spawn.isPresent()) {
                found++;
                SpawnSearch.Spawn s = spawn.get();
                assertThat(world.height(s.x(), s.z())).as("surface under the spawn of seed %s", seed).isGreaterThan(SEA);
                assertThat(s.y()).as("y of seed %s", seed).isEqualTo(world.height(s.x(), s.z()) + 1);
            }
        }
        assertThat(found).as("most seeds have a dry spawn").isGreaterThanOrEqualTo(SEEDS.length - 1);
    }

    @Test
    void theSearchIsDeterministic() {
        for (long seed : SEEDS) {
            World world = new World(seed);
            assertThat(spawn(world)).isEqualTo(spawn(new World(seed)));
        }
    }

    @Test
    void theSpawnStaysInsideTheSearchRadiusPlusTheSpiral() {
        for (long seed : SEEDS) {
            Optional<SpawnSearch.Spawn> spawn = spawn(new World(seed));
            if (spawn.isPresent()) {
                assertThat(Math.abs(spawn.get().x())).isLessThanOrEqualTo(SpawnSearch.DEFAULT_SPAWN_DISTANCE + SPIRAL_SLACK);
                assertThat(Math.abs(spawn.get().z())).isLessThanOrEqualTo(SpawnSearch.DEFAULT_SPAWN_DISTANCE + SPIRAL_SLACK);
            }
        }
    }

    /** The biome pick is always a spawnable biome when the square has one (this world has many). */
    @Test
    void thePickedBiomeIsSpawnable() {
        for (long seed : SEEDS) {
            World world = new World(seed);
            SpawnSearch.BlockPosition pick = SpawnSearch.findSpawnBiome(seed, 0, 0, SpawnSearch.DEFAULT_SPAWN_DISTANCE, world::biome);
            assertThat(SpawnBiomes.isSpawnable(world.biome(pick.x(), pick.z()))).as("seed %s", seed).isTrue();
        }
    }
}
