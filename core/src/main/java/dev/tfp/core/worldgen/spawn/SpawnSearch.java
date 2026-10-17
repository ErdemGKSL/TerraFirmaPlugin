/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/BiomeSourceExtension.java @ c3b4d9536 (findSpawnBiome), with
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onCreateWorldSpawn: the chunk spiral and the
 * 16 x 16 column scan), and the overworld preset's spawn settings (src/data/java/.../BuiltinWorldPreset.java: spawn distance
 * 4000, centre 0, 0). The safe-column test is this port's (dry land above sea level), not vanilla's respawn check.
 */
package dev.tfp.core.worldgen.spawn;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * TFC's world spawn: a biome is picked from the spawnable biomes within a square of the spawn distance, uniformly by
 * reservoir sampling, then the first dry column is searched for in a spiral of chunks around that biome's chunk.
 *
 * <p>Pure and thread-safe: the inputs are functions of block coordinates, and the only state is the random source made
 * for each call. TFC's search steps in quart units (4 blocks) with a step of {@code spawnDistance / 256} quarts, which is
 * kept as written: the candidate biomes are sampled every 15 quarts (60 blocks) at the default distance.
 */
public final class SpawnSearch {
    /** The overworld preset's {@code spawn_distance}, in blocks (TFC's default settings). */
    public static final int DEFAULT_SPAWN_DISTANCE = 4_000;
    /** The overworld preset's {@code spawn_center_x} and {@code spawn_center_z}. */
    public static final int DEFAULT_CENTER = 0;

    /** TFC's column scan tries: a spiral of at most 1024 chunks. */
    private static final int CHUNK_TRIES = 1024;

    private SpawnSearch() {}

    /**
     * The spawn position: {@code y} is the surface height plus one, so the player stands on the top solid block.
     */
    public record Spawn(int x, int y, int z) {}

    /**
     * Finds the spawn as TFC does.
     *
     * @param seed        the level seed; TFC draws the biome choice from {@code new XoroshiroRandomSource(seed)}
     * @param centerX     the spawn centre, in blocks ({@link #DEFAULT_CENTER} for TFC's default)
     * @param centerZ     the spawn centre, in blocks
     * @param spawnDistance the radius of the biome search, in blocks ({@link #DEFAULT_SPAWN_DISTANCE} for TFC's default)
     * @param seaLevel    the sea level: a column is dry when its surface is above it
     * @param biomeId     the TFC biome id at a block position (the region biome, without rivers)
     * @param heightAt    the surface height (the top solid block) at a block column
     * @return the spawn, or empty when no chunk of the spiral has a dry column (TFC then warns and keeps its unsafe default;
     *         this port leaves the choice to the caller)
     */
    public static Optional<Spawn> find(long seed, int centerX, int centerZ, int spawnDistance, int seaLevel,
                                       IntBinaryOperator biomeId, IntBinaryOperator heightAt) {
        final BlockPosition biome = findSpawnBiome(seed, centerX, centerZ, spawnDistance, biomeId);
        return findSpawnColumn(biome.x(), biome.z(), seaLevel, heightAt);
    }

    /**
     * TFC's {@code findSpawnBiome}: the reservoir pick of the spawnable biomes in the square, with the centre as the fallback.
     * The quart coordinates are the TFC ones, so every candidate is looked up at {@code 4 * quart} blocks.
     */
    static BlockPosition findSpawnBiome(long seed, int centerX, int centerZ, int spawnDistance, IntBinaryOperator biomeId) {
        final int step = Math.max(1, spawnDistance / 256);
        final int quartCenterX = centerX >> 2;
        final int quartCenterZ = centerZ >> 2;
        final int maxRadius = spawnDistance >> 2;
        final XoroshiroRandomSource random = new XoroshiroRandomSource(seed);

        boolean hasFound = false;
        int foundX = 0;
        int foundZ = 0;
        int count = 0;
        for (int radius = -maxRadius; radius <= maxRadius; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    final int quartX = quartCenterX + dz;
                    final int quartZ = quartCenterZ + dx;
                    final int biome = biomeId.applyAsInt(quartX << 2, quartZ << 2);
                    if (SpawnBiomes.isSpawnable(biome)) {
                        if (!hasFound || random.nextInt(count + 1) == 0) {
                            foundX = quartX << 2;
                            foundZ = quartZ << 2;
                            hasFound = true;
                        }
                        count++;
                    }
                }
            }
        }
        return hasFound ? new BlockPosition(foundX, foundZ) : new BlockPosition(centerX, centerZ);
    }

    /**
     * TFC's {@code onCreateWorldSpawn} search: the chunks of a 32 x 32 spiral around the biome's chunk, the first one with a
     * dry column; within a chunk the first dry column scanning x, then z.
     */
    static Optional<Spawn> findSpawnColumn(int biomeX, int biomeZ, int seaLevel, IntBinaryOperator heightAt) {
        final int chunkX = biomeX >> 4;
        final int chunkZ = biomeZ >> 4;
        int x = 0;
        int z = 0;
        int xStep = 0;
        int zStep = -1;
        for (int tries = 0; tries < CHUNK_TRIES; ++tries) {
            if (x > -16 && x <= 16 && z > -16 && z <= 16) {
                final Optional<Spawn> spawn = dryColumnInChunk(chunkX + x, chunkZ + z, seaLevel, heightAt);
                if (spawn.isPresent()) {
                    return spawn;
                }
            }
            if ((x == z) || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                final int swap = xStep;
                xStep = -zStep;
                zStep = swap;
            }
            x += xStep;
            z += zStep;
        }
        return Optional.empty();
    }

    /** The first column of a chunk whose surface is above sea level, scanning x then z. */
    static Optional<Spawn> dryColumnInChunk(int chunkX, int chunkZ, int seaLevel, IntBinaryOperator heightAt) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final int x = (chunkX << 4) + lx;
                final int z = (chunkZ << 4) + lz;
                final int surface = heightAt.applyAsInt(x, z);
                if (surface > seaLevel) {
                    return Optional.of(new Spawn(x, surface + 1, z));
                }
            }
        }
        return Optional.empty();
    }

    /** A block column, {@code (x, z)}; internal to the search. */
    record BlockPosition(int x, int z) {}
}
