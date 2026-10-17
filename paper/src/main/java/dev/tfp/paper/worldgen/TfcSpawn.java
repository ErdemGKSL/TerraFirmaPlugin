/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.spawn.SpawnSearch;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * The world spawn of a TFC overworld (M2-GEN-16): TFC's search ({@link SpawnSearch}) over the terrain's biomes and surface
 * heights, at TFC's default centre and spawn distance, with the sea level of the chunk data. The player stands in the middle of
 * the spawn column, one block above its surface.
 */
final class TfcSpawn {
    private TfcSpawn() {}

    /**
     * @param seed    the seed of the terrain (the reservoir of the biome choice)
     * @param biomeId the TFC biome id at a block position
     * @param heightAt the surface height (top solid block) at a block column
     * @return the spawn, or empty when no column of the search is dry land
     */
    static Optional<SpawnSearch.Spawn> search(long seed, IntBinaryOperator biomeId, IntBinaryOperator heightAt) {
        return SpawnSearch.find(seed, SpawnSearch.DEFAULT_CENTER, SpawnSearch.DEFAULT_CENTER, SpawnSearch.DEFAULT_SPAWN_DISTANCE,
            ChunkData.SEA_LEVEL_Y, biomeId, heightAt);
    }

    /** The Bukkit location of a spawn: the centre of its block column, with the feet on the surface plus one. */
    static Location location(World world, SpawnSearch.Spawn spawn) {
        return new Location(world, spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5);
    }
}
