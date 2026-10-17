/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.spawn.SpawnSearch;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * The spawn override of M2-GEN-16 with a fake height function: the spawn is the dry column TFC's search finds, the location
 * stands on it, and an all-sea world has no spawn (Paper's default then applies). Not the terrain of any seed.
 */
class TfcSpawnTest {
    private static final int SEA = ChunkData.SEA_LEVEL_Y;
    private static final IntBinaryOperator LAND_BIOME = (x, z) -> x == -20 && z == -20 ? TfcBiomes.PLAINS : TfcBiomes.OCEAN;

    /** An island of surface 80 over the chunk (-2, -2) at x -31..-29, z -31..-29; the rest is sea floor at 50. */
    private static int island(int x, int z) {
        return x >= -31 && x <= -29 && z >= -31 && z <= -29 ? 80 : 50;
    }

    @Test
    void theSpawnIsTheFirstDryColumnOfTheSearch() {
        Optional<SpawnSearch.Spawn> spawn = TfcSpawn.search(9L, LAND_BIOME, TfcSpawnTest::island);
        assertThat(spawn).contains(new SpawnSearch.Spawn(-31, 81, -31));
    }

    @Test
    void theLocationStandsOnTheSurfaceInTheMiddleOfTheColumn() {
        Location location = TfcSpawn.location(null, new SpawnSearch.Spawn(-31, 81, -31));
        assertThat(location.getX()).isEqualTo(-30.5);
        assertThat(location.getY()).isEqualTo(81.0);
        assertThat(location.getZ()).isEqualTo(-30.5);
        assertThat(location.getWorld()).isNull();
    }

    @Test
    void theSpawnIsAboveSeaLevel() {
        Optional<SpawnSearch.Spawn> spawn = TfcSpawn.search(9L, LAND_BIOME, TfcSpawnTest::island);
        assertThat(spawn).isPresent();
        SpawnSearch.Spawn s = spawn.get();
        assertThat(island(s.x(), s.z())).isGreaterThan(SEA);
        assertThat(s.y()).isEqualTo(island(s.x(), s.z()) + 1);
    }

    @Test
    void anAllSeaWorldHasNoSpawnSoPaperDecides() {
        Optional<SpawnSearch.Spawn> spawn = TfcSpawn.search(9L, LAND_BIOME, (x, z) -> SEA - 13);
        assertThat(spawn).isEmpty();
    }
}
