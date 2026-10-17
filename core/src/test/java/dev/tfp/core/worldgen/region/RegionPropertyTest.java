/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RegionPropertyTest {
    private static final int[][] GRID = {{-400, -60}, {-100, 25}, {0, 0}, {60, 200}, {350, -400}, {-250, 350}, {120, -120}, {-5, -5}};

    private static RegionGenerator generator(long seed) {
        return new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(seed));
    }

    @Test
    void sameSeedGivesSameRegions() {
        final RegionGenerator a = generator(42L);
        final RegionGenerator b = generator(42L);
        for (int[] grid : GRID) {
            assertThat(RegionGeneratorGoldenTest.describe(b, 42L, false, grid[0], grid[1]))
                .isEqualTo(RegionGeneratorGoldenTest.describe(a, 42L, false, grid[0], grid[1]));
        }
    }

    @Test
    void differentSeedsGiveDifferentRegions() {
        for (long seed : new long[] {42L, -7L, 1L}) {
            final String first = RegionGeneratorGoldenTest.describe(generator(seed), seed, false, 0, 0);
            final String second = RegionGeneratorGoldenTest.describe(generator(seed + 1), seed + 1, false, 0, 0);
            assertThat(second).isNotEqualTo(first);
            assertThat(second.substring(second.indexOf("hash="))).isNotEqualTo(first.substring(first.indexOf("hash=")));
        }
    }

    @Test
    void regionOwnsItsGridPointsAndIdsStayInRange() {
        final RegionGenerator generator = generator(42L);
        for (int[] grid : GRID) {
            final Region region = generator.getOrCreateRegion(grid[0], grid[1]);
            assertThat(region.minX()).isLessThanOrEqualTo(grid[0]);
            assertThat(region.maxX()).isGreaterThanOrEqualTo(grid[0]);
            assertThat(region.minZ()).isLessThanOrEqualTo(grid[1]);
            assertThat(region.maxZ()).isGreaterThanOrEqualTo(grid[1]);

            int points = 0;
            for (Region.Point p : region.points()) {
                points++;
                assertThat(p.x).isBetween(region.minX(), region.maxX());
                assertThat(p.z).isBetween(region.minZ(), region.maxZ());
                assertThat(p.hotSpotAge).isBetween((byte) 0, (byte) 4);
                // The rock supertype is the lower two bits: ocean, volcanic, land or uplift
                assertThat(p.rock & ChooseRocks.TYPE_MASK).isBetween(0, 3);
                assertThat(p.oceanDepth).isBetween((byte) 0, (byte) 5);
                assertThat(p.distanceToEdge).isGreaterThanOrEqualTo((byte) -1);
                assertThat(p.rainfall).isBetween(0f, 500f);
                assertThat(p.rainfallVariance).isBetween(-1f, 1f);
                assertThat(Float.isFinite(p.temperature)).isTrue();
                assertThat(Float.isFinite(p.divergence)).isTrue();
                if (p.shore()) {
                    assertThat(p.distanceToOcean).isEqualTo((byte) -2);
                }
                if (p.island()) {
                    assertThat(p.land()).isTrue();
                }
            }
            assertThat(points).isPositive();
        }
    }

    @Test
    void pointLookupAgreesWithRegion() {
        final RegionGenerator generator = generator(42L);
        final Region region = generator.getOrCreateRegion(0, 0);
        final Region.Point origin = generator.getOrCreateRegionPoint(0, 0);
        assertThat(origin.x).isEqualTo(0);
        assertThat(origin.z).isEqualTo(0);
        // Every point of the region answers the same lookup with the same flags
        for (Region.Point p : region.points()) {
            final Region.Point again = generator.getOrCreateRegionPoint(p.x, p.z);
            assertThat(again.land()).isEqualTo(p.land());
            assertThat(again.rock).isEqualTo(p.rock);
            assertThat(again.distanceToOcean).isEqualTo(p.distanceToOcean);
        }
    }

    @Test
    void sameRegionsFromManyThreadsAtOnce() throws Exception {
        final long seed = 42L;
        final List<String> expected = new ArrayList<>();
        final RegionGenerator single = generator(seed);
        for (int[] grid : GRID) {
            expected.add(RegionGeneratorGoldenTest.describe(single, seed, false, grid[0], grid[1]));
        }

        // One shared generator, as in the server: each thread builds regions it has not seen yet
        final RegionGenerator shared = generator(seed);
        final int threads = 4;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<List<String>>> results = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                final int offset = t;
                results.add(pool.submit(() -> {
                    start.await();
                    final List<String> out = new ArrayList<>();
                    for (int i = 0; i < GRID.length; i++) {
                        final int[] grid = GRID[(i + offset) % GRID.length];
                        out.add(RegionGeneratorGoldenTest.describe(shared, seed, false, grid[0], grid[1]));
                    }
                    return out;
                }));
            }
            start.countDown();
            for (int t = 0; t < threads; t++) {
                final List<String> out = results.get(t).get(60, TimeUnit.SECONDS);
                for (int i = 0; i < GRID.length; i++) {
                    final int which = (i + t) % GRID.length;
                    assertThat(out.get(i)).as("thread %d grid %d", t, which).isEqualTo(expected.get(which));
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
