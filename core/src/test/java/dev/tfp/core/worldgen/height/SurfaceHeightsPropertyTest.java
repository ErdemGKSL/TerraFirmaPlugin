/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.height;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.volcano.CinderCones;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the surface heights (M2-GEN-06): determinism, range, continuity across chunk edges, thread safety. */
class SurfaceHeightsPropertyTest {
    /** The overworld's build range: TFC's deepest trench lies above it, its highest peaks below. */
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static SurfaceHeights fresh(long seed) {
        final Seed stream = Seed.of(seed);
        final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        new ChunkDataGenerator(regions, stream);
        return SurfaceHeights.create(seed, regions, stream, new CinderCones(seed));
    }

    @Test
    void theSameSeedGivesTheSameHeightsAndBiomes() {
        final SurfaceHeights a = fresh(7L);
        final SurfaceHeights b = fresh(7L);
        for (int cx = -3; cx <= 3; cx += 3) {
            for (int cz = -5; cz <= 4; cz += 3) {
                final SurfaceHeights.Chunk ca = a.chunk(cx, cz);
                final SurfaceHeights.Chunk cb = b.chunk(cx, cz);
                for (int i = 0; i < 256; i++) {
                    assertThat(ca.columns()[i].height()).isEqualTo(cb.columns()[i].height());
                    assertThat(ca.columns()[i].biome()).isEqualTo(cb.columns()[i].biome());
                }
                assertThat(a.aquiferSurfaceHeights(cx, cz)).containsExactly(b.aquiferSurfaceHeights(cx, cz));
            }
        }
    }

    @Test
    void anotherSeedGivesOtherHeights() {
        assertThat(fresh(7L).chunk(2, -9).surfaceHeights()).isNotEqualTo(fresh(8L).chunk(2, -9).surfaceHeights());
    }

    @Test
    void heightsStayInTheWorldAndBiomesAreRealAndNotTheRiver() {
        final SurfaceHeights h = SurfaceHeightsGoldenTest.heights(42L);
        boolean land = false;
        boolean sea = false;
        for (int cx = -40; cx <= 40; cx += 8) {
            for (int cz = -40; cz <= 40; cz += 8) {
                for (SurfaceHeights.Column column : h.chunk(cx, cz).columns()) {
                    assertThat(column.surfaceHeight()).isBetween(MIN_Y, MAX_Y - 1);
                    assertThat(column.biome()).isBetween(0, TfcBiomes.COUNT - 1).isNotEqualTo(TfcBiomes.RIVER);
                    assertThat(column.localBiome()).isBetween(0, TfcBiomes.COUNT - 1);
                    assertThat(column.height()).isFinite();
                    land |= column.surfaceHeight() > ChunkData.SEA_LEVEL_Y;
                    sea |= column.surfaceHeight() < ChunkData.SEA_LEVEL_Y;
                }
            }
        }
        assertThat(land).as("some land").isTrue();
        assertThat(sea).as("some sea floor").isTrue();
    }

    @Test
    void aSingleColumnIsTheColumnOfItsChunk() {
        final SurfaceHeights h = SurfaceHeightsGoldenTest.heights(-7L);
        for (int[] c : new int[][] {{0, 0}, {-1, -1}, {-56, -35}, {13, -2}}) {
            final SurfaceHeights.Chunk chunk = h.chunk(c[0], c[1]);
            for (int lz = 0; lz < 16; lz += 3) {
                for (int lx = 0; lx < 16; lx += 3) {
                    final SurfaceHeights.Column column = h.column((c[0] << 4) + lx, (c[1] << 4) + lz);
                    assertThat(column.height()).isEqualTo(chunk.column(lx, lz).height());
                    assertThat(column.biome()).isEqualTo(chunk.column(lx, lz).biome());
                    assertThat(h.heightAt((c[0] << 4) + lx, (c[1] << 4) + lz)).isEqualTo(chunk.column(lx, lz).surfaceHeight());
                }
            }
        }
    }

    /**
     * The heights are continuous across chunk edges: the steps between neighbouring columns across an edge are no larger on
     * average than the steps inside the chunks (the blending of the biome weights has no seam at chunk borders).
     */
    @Test
    void heightsAreContinuousAcrossChunkEdges() {
        for (long seed : new long[] {42L, -7L}) {
            final SurfaceHeights h = SurfaceHeightsGoldenTest.heights(seed);
            double inside = 0;
            double across = 0;
            int insideCount = 0;
            int acrossCount = 0;
            double maxAcross = 0;
            for (int x = -600; x < 600; x += 1) {
                final int z = 37 + (x * 7) / 5;
                final double a = h.column(x, z).height();
                final double b = h.column(x + 1, z).height();
                final double step = Math.abs(a - b);
                if ((x & 15) == 15) {
                    across += step;
                    acrossCount++;
                    maxAcross = Math.max(maxAcross, step);
                } else {
                    inside += step;
                    insideCount++;
                }
            }
            final double meanInside = inside / insideCount;
            final double meanAcross = across / acrossCount;
            assertThat(meanAcross).as("seed %d: mean step across edges vs inside %.3f", seed, meanInside).isLessThan(1.5 * meanInside + 0.5);
            assertThat(maxAcross).as("seed %d: largest step across a chunk edge", seed).isLessThan(16);
        }
    }

    @Test
    void fourThreadsSharingOneInstanceAgreeWithOneThread() throws Exception {
        final SurfaceHeights shared = fresh(20260917L);
        final SurfaceHeights reference = fresh(20260917L);
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<int[]>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                final int offset = t;
                futures.add(pool.submit(() -> {
                    final int[] out = new int[16 * 256];
                    for (int i = 0; i < 16; i++) {
                        final int[] heights = shared.chunk(((i + offset) % 16) - 8, (i * 3) % 7 - 3).surfaceHeights();
                        System.arraycopy(heights, 0, out, ((i + offset) % 16) * 256, 256);
                    }
                    return out;
                }));
            }
            for (int t = 0; t < 4; t++) {
                final int[] got = futures.get(t).get();
                for (int i = 0; i < 16; i++) {
                    final int chunk = (i + t) % 16;
                    final int[] want = reference.chunk(chunk - 8, (i * 3) % 7 - 3).surfaceHeights();
                    for (int k = 0; k < 256; k++) {
                        assertThat(got[chunk * 256 + k]).isEqualTo(want[k]);
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
