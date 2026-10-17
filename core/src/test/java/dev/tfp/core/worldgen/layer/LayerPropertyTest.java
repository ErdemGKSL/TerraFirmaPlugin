/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.layer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Properties of the layer framework and the basic layers that do not depend on TFC's exact numbers: determinism for a seed,
 * sensitivity to the seed, id ranges, cache consistency, and thread confinement through {@link ConcurrentArea}.
 */
class LayerPropertyTest {
    private static final int SIZE = 64;
    private static final int ORIGIN = -40;

    /** Ids 0..3, the same fixture as {@link LayerGoldenTest}. */
    private static final SourceLayer SMALL_IDS = (context, x, z) -> context.random().nextInt(4);

    /** The four-layer stack of TFCLayers' style, seeded from one {@link Random}. */
    private static AreaFactory stack(long seed) {
        final Random random = new Random(seed);
        AreaFactory layer = SMALL_IDS.apply(seed);
        layer = ZoomLayer.NORMAL.apply(random.nextLong(), layer);
        layer = ZoomLayer.FUZZY.apply(random.nextLong(), layer);
        layer = SmoothLayer.INSTANCE.apply(random.nextLong(), layer);
        layer = ZoomLayer.NORMAL.apply(random.nextLong(), layer);
        return layer;
    }

    private static int[] grid(AreaFactory factory) {
        final Area area = factory.get();
        final int[] out = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                out[z * SIZE + x] = area.get(ORIGIN + x, ORIGIN + z);
            }
        }
        return out;
    }

    @Test
    void sameSeedGivesSameGrid() {
        assertThat(grid(stack(123L))).containsExactly(grid(stack(123L)));
        assertThat(grid(UniformLayer.INSTANCE.apply(123L))).containsExactly(grid(UniformLayer.INSTANCE.apply(123L)));
        assertThat(grid(ZoomLayer.FUZZY.apply(9L, SMALL_IDS.apply(9L))))
            .containsExactly(grid(ZoomLayer.FUZZY.apply(9L, SMALL_IDS.apply(9L))));
    }

    @Test
    void repeatedReadsOfOneAreaAreStable() {
        // The direct-mapped cache must return the same value on a hit as on the miss that filled it, including after eviction.
        final Area area = stack(5L).get();
        final int[] first = new int[SIZE * SIZE];
        for (int i = 0; i < first.length; i++) {
            first[i] = area.get(ORIGIN + i % SIZE, ORIGIN + i / SIZE);
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int i = first.length - 1; i >= 0; i--) {
                assertThat(area.get(ORIGIN + i % SIZE, ORIGIN + i / SIZE)).isEqualTo(first[i]);
            }
        }
    }

    @Test
    void differentSeedsGiveDifferentGrids() {
        final int[] a = grid(stack(1L));
        final int[] b = grid(stack(2L));
        int differing = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                differing++;
            }
        }
        assertThat(differing).isGreaterThan(a.length / 4);
        assertThat(grid(UniformLayer.INSTANCE.apply(1L))).isNotEqualTo(grid(UniformLayer.INSTANCE.apply(2L)));
    }

    @Test
    void idLayersStayInTheirRange() {
        // Zoom and smooth only pick among their inputs, so ids 0..3 stay in 0..3 at any depth, including negative coordinates.
        for (long seed : new long[] {0L, 42L, -7L, 987654321L}) {
            for (int value : grid(stack(seed))) {
                assertThat(value).isBetween(0, 3);
            }
            for (int value : grid(ZoomLayer.NORMAL.apply(seed, SMALL_IDS.apply(seed)))) {
                assertThat(value).isBetween(0, 3);
            }
            for (int value : grid(SmoothLayer.INSTANCE.apply(seed, SMALL_IDS.apply(seed)))) {
                assertThat(value).isBetween(0, 3);
            }
        }
    }

    @Test
    void uniformLayerCoversBothSignsAndManyValues() {
        // TFC's own output for this window has 4096 distinct values (checked in the oracle harness), so the count is exact.
        final Area area = UniformLayer.INSTANCE.apply(77L).get();
        final java.util.Set<Integer> distinct = new java.util.HashSet<>();
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                distinct.add(area.get(x, z));
            }
        }
        assertThat(distinct).hasSize(SIZE * SIZE);
        boolean negative = false, positive = false;
        for (int v : grid(UniformLayer.INSTANCE.apply(77L))) {
            negative |= v < 0;
            positive |= v > 0;
        }
        assertThat(negative).isTrue();
        assertThat(positive).isTrue();
    }

    @Test
    void contextDrawsDependOnlyOnTheSeedAndPosition() {
        // setSeed(x, z) must reset the random completely, so earlier draws cannot leak into later positions.
        final AreaContext used = new AreaContext(99L);
        for (int i = 0; i < 17; i++) {
            used.random().nextLong();
        }
        used.setSeed(12, -34);
        final AreaContext fresh = new AreaContext(99L);
        fresh.setSeed(12, -34);
        for (int i = 0; i < 8; i++) {
            assertThat(used.random().nextLong()).isEqualTo(fresh.random().nextLong());
        }
    }

    @Test
    void typedAreaMatchesItsSourceAcrossCacheEviction() {
        // 64 x 64 positions are more than the 1024-entry cache holds, so reads evict and refill.
        final TypedSourceLayer<String> typed = (context, x, z) -> x + "," + z;
        final TypedArea<String> area = typed.apply(5L).get();
        for (int pass = 0; pass < 2; pass++) {
            for (int z = ORIGIN; z < ORIGIN + SIZE; z++) {
                for (int x = ORIGIN; x < ORIGIN + SIZE; x++) {
                    assertThat(area.get(x, z)).isEqualTo(x + "," + z);
                }
            }
        }
    }

    @Test
    void concurrentAreaGivesTheSingleThreadGridFromEveryThread() throws Exception {
        // One ConcurrentArea shared by all workers: each thread builds its own Area, so no cache or context is shared.
        final AreaFactory factory = stack(7L);
        final int[] expected = grid(factory);
        final ConcurrentArea<Integer> shared = new ConcurrentArea<>(factory, id -> id * 10);
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Callable<int[]>> tasks = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                tasks.add(() -> {
                    final int[] out = new int[SIZE * SIZE];
                    for (int z = 0; z < SIZE; z++) {
                        for (int x = 0; x < SIZE; x++) {
                            out[z * SIZE + x] = shared.get(ORIGIN + x, ORIGIN + z);
                        }
                    }
                    return out;
                });
            }
            for (Future<int[]> future : pool.invokeAll(tasks)) {
                final int[] got = future.get();
                for (int i = 0; i < expected.length; i++) {
                    assertThat(got[i]).isEqualTo(expected[i] * 10);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
