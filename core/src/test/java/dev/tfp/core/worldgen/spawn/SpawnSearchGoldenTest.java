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
 * Golden values of {@link SpawnSearch} against TFC's rule (c3b4d9536). Every answer is worked out by hand from TFC's loops
 * (findSpawnBiome's square sampling and onCreateWorldSpawn's spiral), with synthetic height and biome functions.
 */
class SpawnSearchGoldenTest {
    private static final int SEA = 63;
    private static final int OCEAN = TfcBiomes.OCEAN;
    private static final int LAND = TfcBiomes.PLAINS;

    @Test
    void spawnableBiomesAreTheNinetyThreeTfcRegistrations() {
        assertThat(SpawnBiomes.count()).isEqualTo(93);
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.PLAINS)).isTrue();
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.TOWER_KARST_LAKE)).isTrue();
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.OCEAN)).isFalse();
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.RIVER)).isFalse();
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.LAKE)).isFalse();
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.SHORE)).isFalse();
        assertThat(SpawnBiomes.isSpawnable(-1)).isFalse();
        assertThat(SpawnBiomes.isSpawnable(TfcBiomes.COUNT)).isFalse();
    }

    /**
     * The only spawnable column is block (-20, -20), quart (-5, -5), the first sample of TFC's square (radius 5). The
     * reservoir has one candidate and picks it for any seed.
     */
    @Test
    void theOnlyCandidateIsPickedWhateverTheSeed() {
        IntBinaryOperator biome = (x, z) -> x == -20 && z == -20 ? LAND : OCEAN;
        for (long seed : new long[] {0L, 1L, -99L, Long.MAX_VALUE}) {
            assertThat(SpawnSearch.findSpawnBiome(seed, 0, 0, SpawnSearch.DEFAULT_SPAWN_DISTANCE, biome))
                .isEqualTo(new SpawnSearch.BlockPosition(-20, -20));
        }
    }

    /**
     * TFC's radius loop starts at -maxRadius (1000 quarts), which is not a multiple of the step (15), so the sampled quart
     * coordinates are congruent to 10 mod 15 (-5 is sampled, 0 and 1 are not). A spawnable column at quart (1, 0) (block
     * (4, 0)) is never visited, so the pick falls back to the centre.
     */
    @Test
    void theSquareIsSampledOnTheStepLattice() {
        IntBinaryOperator offLattice = (x, z) -> x == 4 && z == 0 ? LAND : OCEAN;
        assertThat(SpawnSearch.findSpawnBiome(7L, 0, 0, 4_000, offLattice)).isEqualTo(new SpawnSearch.BlockPosition(0, 0));
    }

    /**
     * The pick is block (-20, -20), chunk (-2, -2) (blocks -32..-17). Its columns x -31..-29, z -31..-29 are 80 high, the
     * rest 50 (ocean). The first dry column in x-then-z order is (-31, -31), so y = 80 + 1.
     */
    @Test
    void firstDryColumnOfThePickChunkIsTheSpawn() {
        IntBinaryOperator heights = (x, z) -> x >= -31 && x <= -29 && z >= -31 && z <= -29 ? 80 : 50;
        Optional<SpawnSearch.Spawn> spawn = SpawnSearch.find(1L, 0, 0, SpawnSearch.DEFAULT_SPAWN_DISTANCE, SEA,
            (x, z) -> x == -20 && z == -20 ? LAND : OCEAN, heights);
        assertThat(spawn).contains(new SpawnSearch.Spawn(-31, 81, -31));
    }

    /**
     * The spiral from chunk (0, 0): tries visit (0,0), (1,0), (1,1), ... (the turn rule of onCreateWorldSpawn). Only chunk
     * (1, 1) (blocks 16..31) is dry, so the third try is the spawn: its first column (16, 16), y = 70 + 1.
     */
    @Test
    void theSpiralReachesTheThirdChunkWhenOnlyItIsDry() {
        IntBinaryOperator heights = (x, z) -> x >= 16 && x <= 31 && z >= 16 && z <= 31 ? 70 : 50;
        assertThat(SpawnSearch.findSpawnColumn(0, 0, SEA, heights)).contains(new SpawnSearch.Spawn(16, 71, 16));
    }

    /** No dry column anywhere in the 32 x 32 chunk spiral: no spawn (the caller decides; never a sea column). */
    @Test
    void noDryColumnInTheSpiralGivesNoSpawn() {
        IntBinaryOperator allSea = (x, z) -> 50;
        assertThat(SpawnSearch.findSpawnColumn(0, 0, SEA, allSea)).isEmpty();
    }

    /** No spawnable biome in the square: TFC falls back to the centre (0, 0), and the chunk scan starts there. */
    @Test
    void noCandidateFallsBackToTheCentre() {
        IntBinaryOperator allOcean = (x, z) -> OCEAN;
        IntBinaryOperator heights = (x, z) -> x == 2 && z == 9 || x == 7 && z == 1 ? 70 : 50;
        assertThat(SpawnSearch.find(3L, 0, 0, SpawnSearch.DEFAULT_SPAWN_DISTANCE, SEA, allOcean, heights))
            .contains(new SpawnSearch.Spawn(2, 71, 9));
    }
}
