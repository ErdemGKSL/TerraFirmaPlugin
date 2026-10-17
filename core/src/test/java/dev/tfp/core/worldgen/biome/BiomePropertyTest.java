/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.biome;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the biome map that hold for any seed: determinism, id and altitude ranges, the ocean and land rules. */
class BiomePropertyTest {
    private static final int[][] GRID = {{-400, -60}, {-100, 25}, {0, 0}, {60, 200}, {350, -400}, {-250, 350}, {120, -120}, {-5, -5}};

    private static RegionGenerator generator(long seed) {
        return new RegionGenerator(new RegionSettings(0.5f, seed == 20260917L, 20_000, 0f, 20_000, 0f), Seed.of(seed));
    }

    @Test
    void sameSeedGivesSameBiomes() {
        for (long seed : new long[] {42L, -7L}) {
            final RegionGenerator first = generator(seed);
            final RegionGenerator second = generator(seed);
            for (int[] g : GRID) {
                assertThat(hash(second.getOrCreateRegion(g[0], g[1]))).as("seed %d at %s", seed, g[0] + "," + g[1])
                    .isEqualTo(hash(first.getOrCreateRegion(g[0], g[1])));
            }
        }
    }

    @Test
    void differentSeedsGiveDifferentBiomes() {
        final RegionGenerator a = generator(42L);
        final RegionGenerator b = generator(43L);
        assertThat(hash(a.getOrCreateRegion(0, 0))).isNotEqualTo(hash(b.getOrCreateRegion(0, 0)));
    }

    @Test
    void idsAndAltitudesAreInRange() {
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final RegionGenerator generator = generator(seed);
            for (int[] g : GRID) {
                for (Region.Point p : generator.getOrCreateRegion(g[0], g[1]).points()) {
                    assertThat(p.biome).as("biome at (%d,%d)", p.x, p.z).isBetween(0, TfcBiomes.COUNT - 1);
                    assertThat(p.biomeAltitude).as("altitude at (%d,%d)", p.x, p.z).isBetween((byte) 0, (byte) (3 * BiomeAltitude.WIDTH));
                    assertThat(p.discreteBiomeAltitude()).isBetween(0, 3);
                    assertThat(p.isSurfaceRockKarst).as("karst is not ported").isFalse();
                }
            }
        }
    }

    @Test
    void landIsNeverOceanAndOceanIsOcean() {
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final RegionGenerator generator = generator(seed);
            for (int[] g : GRID) {
                for (Region.Point p : generator.getOrCreateRegion(g[0], g[1]).points()) {
                    if (p.land()) {
                        assertThat(TfcBiomes.isOcean(p.biome)).as("land biome %s at (%d,%d)", TfcBiomes.name(p.biome), p.x, p.z).isFalse();
                    } else if (!p.island() && !p.barrierIsland() && !p.mountain() && !p.river()) {
                        // Water is an ocean biome, or a hot spot volcano that rises from the seafloor
                        assertThat(TfcBiomes.isOcean(p.biome) || isSeafloorVolcano(p.biome))
                            .as("water biome %s at (%d,%d)", TfcBiomes.name(p.biome), p.x, p.z).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void lakesReplaceOnlyLakeCapableBiomes() {
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final RegionGenerator generator = generator(seed);
            for (int[] g : GRID) {
                for (Region.Point p : generator.getOrCreateRegion(g[0], g[1]).points()) {
                    // A lake replaces a lake-capable biome with a lake biome; the others keep their biome
                    if (p.lake()) {
                        assertThat(TfcBiomes.isLake(p.biome) || !TfcBiomes.hasLake(p.biome))
                            .as("lake point %s", TfcBiomes.name(p.biome)).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void fourThreadsSharingOneGeneratorGetTheSameBiomes() throws Exception {
        final RegionGenerator shared = generator(42L);
        final List<Long> expected = new ArrayList<>();
        for (int[] g : GRID) {
            expected.add(hash(generator(42L).getOrCreateRegion(g[0], g[1])));
        }
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<List<Long>>> results = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                results.add(pool.submit(() -> {
                    final List<Long> hashes = new ArrayList<>();
                    for (int[] g : GRID) {
                        hashes.add(hash(shared.getOrCreateRegion(g[0], g[1])));
                    }
                    return hashes;
                }));
            }
            for (Future<List<Long>> result : results) {
                assertThat(result.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean isSeafloorVolcano(int biome) {
        return biome == TfcBiomes.SUNKEN_SHIELD_VOLCANO || biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO
            || biome == TfcBiomes.DORMANT_SHIELD_VOLCANO || biome == TfcBiomes.EXTINCT_SHIELD_VOLCANO
            || biome == TfcBiomes.ANCIENT_SHIELD_VOLCANO;
    }

    /** A hash of every point's grid position, biome and biome altitude. */
    private static long hash(Region region) {
        long h = 0;
        for (Region.Point p : region.points()) {
            h = h * 31 + p.x;
            h = h * 31 + p.z;
            h = h * 31 + p.biome;
            h = h * 31 + p.biomeAltitude;
        }
        return h;
    }
}
