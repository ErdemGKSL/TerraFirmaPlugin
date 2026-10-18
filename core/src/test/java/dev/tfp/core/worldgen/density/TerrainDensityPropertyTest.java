/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.density;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.height.SurfaceHeights;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the per-block terrain density ({@link SurfaceHeights.Column#density}, {@link SurfaceHeights.Column#solid}). */
class TerrainDensityPropertyTest {
    /** Golden chunks of seed 42: the sandy shore, the sea stacks, the terraces, embayments, the shield volcano shore, plain chunks ((60, -60) has neither a lake nor a river). */
    private static final int[][] CHUNKS = {{3, 3}, {168, 285}, {207, -210}, {210, -210}, {0, 0}, {-12, -9}, {18, -6}, {231, -48}, {60, -60}};

    private static long hash(SurfaceHeights heights, int cx, int cz) {
        final SurfaceHeights.Chunk chunk = heights.chunk(cx, cz);
        long h = 17;
        for (SurfaceHeights.Column column : chunk.columns()) {
            for (int y = -64; y <= 200; y++) {
                h = h * 31 + Double.doubleToLongBits(column.density(y)) + (column.solid(y) ? 7 : 0);
            }
            h = h * 31 + column.terrainTop(-64);
        }
        return h;
    }

    @Test
    void deterministic() {
        final SurfaceHeights a = TerrainDensityGoldenTest.heights(42L);
        for (int[] c : CHUNKS) {
            assertThat(hash(a, c[0], c[1])).isEqualTo(hash(a, c[0], c[1]));
        }
    }

    @Test
    void solidBelowTheSurfaceOnLand() {
        final SurfaceHeights heights = TerrainDensityGoldenTest.heights(42L);
        int checked = 0;
        for (int[] c : CHUNKS) {
            for (SurfaceHeights.Column column : heights.chunk(c[0], c[1]).columns()) {
                if (!column.carves()) {
                    // a plain column: solid exactly up to its surface (one more over a sea floor)
                    for (int y = -64; y <= column.surfaceHeight(); y++) {
                        assertThat(column.solid(y)).isTrue();
                    }
                    final boolean underSea = column.surfaceHeight() < 63;
                    assertThat(column.solid(column.surfaceHeight() + 1)).isEqualTo(underSea);
                    assertThat(column.terrainTop(-64)).isEqualTo(column.surfaceHeight() + (underSea ? 1 : 0));
                }
                if (column.cenotes().present()) {
                    continue;
                }
                // the shores, the lakes and the rivers carve near the surface and the sea level only: deep ground stays solid
                for (int y = -64; y <= Math.min(20, column.surfaceHeight()); y++) {
                    assertThat(column.solid(y)).as("y %d", y).isTrue();
                    checked++;
                }
                if (column.surfaceHeight() >= 64) {
                    assertThat(column.solid(column.surfaceHeight() + 1)).as("land ends at its surface").isFalse();
                }
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void nothingSolidAboveTheFilledHeight() {
        final SurfaceHeights heights = TerrainDensityGoldenTest.heights(42L);
        int overhangs = 0;
        for (int[] c : CHUNKS) {
            for (SurfaceHeights.Column column : heights.chunk(c[0], c[1]).columns()) {
                final int max = column.maxFilledY();
                assertThat(max).isLessThanOrEqualTo(Math.max(column.surfaceHeight(), 63) + 1);
                for (int y = max; y <= max + 40; y++) {
                    assertThat(column.solid(y)).isFalse();
                }
                final int top = column.terrainTop(-64);
                assertThat(top).isLessThan(max);
                for (int y = column.surfaceHeight() + 1; y < max; y++) {
                    if (column.solid(y)) {
                        // only under the sea level: a land column is not filled above its surface
                        assertThat(y).isLessThanOrEqualTo(63);
                        overhangs++;
                    }
                }
                for (int y = -64; y <= column.surfaceHeight(); y++) {
                    assertThat(column.opens(y)).isEqualTo(!column.solid(y));
                }
                for (int y = -64; y <= 200; y++) {
                    assertThat(column.density(y)).isBetween(-1.0, 1.0);
                }
            }
        }
        assertThat(overhangs).as("the sea floors keep the slide block").isPositive();
    }

    @Test
    void threadDeterminism() throws Exception {
        final long[] expected = new long[CHUNKS.length];
        final SurfaceHeights fresh = TerrainDensityGoldenTest.heights(-7L);
        for (int i = 0; i < CHUNKS.length; i++) {
            expected[i] = hash(fresh, CHUNKS[i][0], CHUNKS[i][1]);
        }
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<long[]>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                futures.add(pool.submit(() -> {
                    final long[] out = new long[CHUNKS.length];
                    for (int i = CHUNKS.length - 1; i >= 0; i--) {
                        out[i] = hash(fresh, CHUNKS[i][0], CHUNKS[i][1]);
                    }
                    return out;
                }));
            }
            for (Future<long[]> f : futures) {
                assertThat(f.get()).containsExactly(expected);
            }
        } finally {
            pool.shutdown();
        }
    }
}
