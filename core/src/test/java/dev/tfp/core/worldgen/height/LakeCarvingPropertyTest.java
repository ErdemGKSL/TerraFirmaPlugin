/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.height;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.river.RiverBlendType;
import dev.tfp.core.worldgen.volcano.CinderCones;
import org.junit.jupiter.api.Test;

/** M2-GEN-04: properties of the carved lakes and the blended terrain density. */
class LakeCarvingPropertyTest {
    /** Seed 42 chunks from the golden set: all lake columns; lake columns with blended rivers; rivers only. */
    private static final int[][] CHUNKS = {{50, 0}, {60, -5}, {15, 35}};

    private static SurfaceHeights fresh(long seed) {
        final Seed stream = Seed.of(seed);
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        new ChunkDataGenerator(regions, stream);
        return SurfaceHeights.create(seed, regions, stream, new CinderCones(seed));
    }

    private static long densityHash(SurfaceHeights heights, int cx, int cz) {
        final SurfaceHeights.Chunk chunk = heights.chunk(cx, cz);
        long h = 17;
        for (SurfaceHeights.Column column : chunk.columns()) {
            for (int y = 30; y <= 110; y++) {
                h = h * 31 + Double.doubleToLongBits(column.density(y));
            }
        }
        return h;
    }

    @Test
    void deterministicPerSeedAndSeedSensitive() {
        final SurfaceHeights a = fresh(42L);
        final SurfaceHeights b = fresh(42L);
        for (int[] c : CHUNKS) {
            assertThat(densityHash(a, c[0], c[1])).isEqualTo(densityHash(b, c[0], c[1]));
        }
        final LakeCarving lakes42 = new LakeCarving(42L);
        final LakeCarving lakes7 = new LakeCarving(-7L);
        final BiomeWeights weights = new BiomeWeights();
        weights.put(dev.tfp.core.worldgen.biome.TfcBiomes.PLATEAU_LAKE, 1.0);
        int differ = 0;
        for (int i = 0; i < 64; i++) {
            if (lakes42.column(weights, i * 37, i * 53).noise(63) != lakes7.column(weights, i * 37, i * 53).noise(63)) {
                differ++;
            }
        }
        assertThat(differ).as("another seed gives other lakes").isPositive();
    }

    @Test
    void blendWeightsSumToOne() {
        final SurfaceHeights heights = SurfaceHeightsGoldenTest.heights(42L);
        for (int[] c : CHUNKS) {
            for (SurfaceHeights.Column column : heights.chunk(c[0], c[1]).columns()) {
                double river = 0;
                for (RiverBlendType type : RiverBlendType.ALL) {
                    assertThat(column.river().weight(type)).isBetween(0.0, 1.0 + 1e-9);
                    river += column.river().weight(type);
                }
                assertThat(river).isCloseTo(1.0, within(1e-9));
                double shore = 0;
                for (double w : column.shoreWeights()) {
                    shore += w;
                }
                assertThat(shore).isCloseTo(1.0, within(1e-9));
                assertThat(column.lake().weight()).isBetween(0.0, 1.0 + 1e-9);
            }
        }
    }

    @Test
    void blendedValuesStayWithinTheirInputs() {
        final SurfaceHeights heights = SurfaceHeightsGoldenTest.heights(42L);
        int lakeOpen = 0;
        for (int[] c : CHUNKS) {
            for (SurfaceHeights.Column column : heights.chunk(c[0], c[1]).columns()) {
                final LakeCarving.Column lake = column.lake();
                for (int y = 0; y <= 140; y++) {
                    // The inputs are SOLID (0) for every other biome and the one lake sampler value
                    final double sampler = lake.present() ? LakeCarving.samplerNoise(lake.center(), lake.height(), y) : 0;
                    final double blended = lake.noise(y);
                    // The biome weights sum to 1 within rounding (1e-9)
                    assertThat(blended).isBetween(Math.min(0, sampler) * (1 + 1e-9) - 1e-12, Math.max(0, sampler) * (1 + 1e-9) + 1e-12);
                    final double post = LakeCarving.postShoreNoise(blended, column.shoreWeights(), y, column.surfaceHeight(),
                        column.surfaceIntegrityDepth());
                    // The shore weights sum to 1 within rounding (1e-9)
                    assertThat(post).isBetween(Math.min(0, blended) * (1 + 1e-9) - 1e-12, Math.max(0, blended) * (1 + 1e-9) + 1e-12);
                    assertThat(column.density(y)).isBetween(-1.0, 1.0);
                    if (column.opens(y)) {
                        assertThat(y).as("opened only at or below the surface").isLessThanOrEqualTo(column.surfaceHeight());
                        if (lake.present()) {
                            lakeOpen++;
                        }
                    }
                }
            }
        }
        assertThat(lakeOpen).as("the lake chunks open lake blocks").isPositive();
    }

    @Test
    void nothingOpensWithoutALakeOrARiver() {
        final SurfaceHeights heights = SurfaceHeightsGoldenTest.heights(42L);
        // Chunk (60, -60) of seed 42 has neither (golden: opened=0)
        for (SurfaceHeights.Column column : heights.chunk(60, -60).columns()) {
            assertThat(column.lake().present()).isFalse();
            for (int y = -64; y <= 320; y++) {
                assertThat(column.opens(y)).isFalse();
            }
        }
    }

    @Test
    void sameValuesFromFourThreads() throws InterruptedException {
        final SurfaceHeights heights = fresh(42L);
        final long expected = densityHash(fresh(42L), 50, 0);
        final long[] results = new long[4];
        final Thread[] threads = new Thread[4];
        for (int t = 0; t < 4; t++) {
            final int index = t;
            threads[t] = new Thread(() -> results[index] = densityHash(heights, 50, 0));
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        for (long r : results) {
            assertThat(r).isEqualTo(expected);
        }
    }
}
