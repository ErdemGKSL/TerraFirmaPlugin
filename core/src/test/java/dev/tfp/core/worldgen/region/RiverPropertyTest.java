/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AddRiversAndLakes.java @ c3b4d9536 (the properties are its rules, with
 * RiverEdge.java, RegionPartition.java and the river builder in world/river/River.java)
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldgen.river.MidpointFractal;
import dev.tfp.core.worldgen.river.River;
import dev.tfp.core.worldgen.river.RiverHelpers;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RiverPropertyTest {
    private static final int[][] GRID = {{-400, -60}, {-100, 25}, {0, 0}, {60, 200}, {350, -400}, {-250, 350}, {120, -120}, {-5, -5}};

    private static RegionGenerator generator(long seed) {
        return new RegionGenerator(new RegionSettings(0.5f, false, 20_000, 0f, 20_000, 0f), Seed.of(seed));
    }

    /** A digest of every river edge of the regions at the grid points: equal digests mean equal river maps. */
    private static List<String> digest(RegionGenerator generator) {
        final List<String> out = new ArrayList<>();
        for (int[] g : GRID) {
            final Region region = generator.getOrCreateRegion(g[0], g[1]);
            for (RiverEdge edge : region.rivers()) {
                out.add(edge.source() + " " + edge.drain() + " " + edge.width + " " + edge.sourceEdge());
            }
            int flags = 0;
            for (Region.Point p : region.points()) {
                flags = 31 * flags + (p.river() ? 1 : 0) + (p.lake() ? 2 : 0);
            }
            out.add("flags " + flags);
        }
        return out;
    }

    @Test
    void deterministicPerSeed() {
        assertThat(digest(generator(42L))).isEqualTo(digest(generator(42L)));
        assertThat(digest(generator(-7L))).isEqualTo(digest(generator(-7L)));
    }

    @Test
    void differentSeedsDiffer() {
        assertThat(digest(generator(42L))).isNotEqualTo(digest(generator(-7L)));
        assertThat(digest(generator(42L))).isNotEqualTo(digest(generator(20260917L)));
    }

    @Test
    void riverValuesAreInRange() {
        for (long seed : new long[] {42L, -7L}) {
            final RegionGenerator generator = generator(seed);
            for (int[] g : GRID) {
                final Region region = generator.getOrCreateRegion(g[0], g[1]);
                for (RiverEdge edge : region.rivers()) {
                    assertThat(edge.width).isBetween(RiverEdge.MIN_WIDTH, RiverEdge.MAX_WIDTH);
                    assertThat(edge.fractal().segments).hasSize(2 * (16 + 1));
                }
                for (Region.Point p : region.points()) {
                    if (p.river() || p.lake()) {
                        assertThat(p.land()).as("river or lake on land at (%d, %d)", p.x, p.z).isTrue();
                        assertThat(p.rainfall).isLessThanOrEqualTo(500f);
                    }
                    if (p.lake()) {
                        assertThat(p.distanceToOcean).isGreaterThanOrEqualTo((byte) 2);
                        assertThat(p.distanceToEdge).isGreaterThanOrEqualTo((byte) 2);
                    }
                }
            }
        }
    }

    @Test
    void riversFormPathsToTheirMouths() {
        // Every edge drains into the source of another edge of the same river, or into a mouth. Following the drains
        // must end at a mouth without a cycle, and the drain link must be the one the source map gives.
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final RegionGenerator generator = generator(seed);
            for (int[] g : GRID) {
                final List<RiverEdge> rivers = generator.getOrCreateRegion(g[0], g[1]).rivers();
                final Map<River.Vertex, RiverEdge> bySource = new HashMap<>();
                for (RiverEdge edge : rivers) {
                    bySource.put(edge.source(), edge);
                }
                for (RiverEdge edge : rivers) {
                    assertThat(edge.drainEdge()).isSameAs(bySource.get(edge.drain()));
                    int steps = 0;
                    RiverEdge current = edge;
                    while (current.drainEdge() != null) {
                        current = current.drainEdge();
                        steps++;
                        assertThat(steps).as("cycle at %s", edge.source()).isLessThanOrEqualTo(rivers.size());
                    }
                }
            }
        }
    }

    @Test
    void partitionListsEveryNearbyEdge() {
        final RegionGenerator generator = generator(42L);
        int checked = 0;
        for (int[] g : GRID) {
            for (RiverEdge edge : generator.getOrCreateRegion(g[0], g[1]).rivers()) {
                final int x = (int) Math.round(edge.source().x());
                final int z = (int) Math.round(edge.source().y());
                final int cellX = Units.gridToCell(x), cellZ = Units.gridToCell(z);
                // The partition of this cell only holds the edges of the 3 x 3 regions sampled around it (TFC's rule)
                final List<RiverEdge> nearby = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        nearby.addAll(generator.getOrCreateRegion(Units.cellToGrid(cellX + dx), Units.cellToGrid(cellZ + dz)).rivers());
                    }
                }
                if (nearby.contains(edge)) {
                    assertThat(generator.getOrCreatePartitionPoint(x, z).rivers()).as("edge at (%d, %d)", x, z).contains(edge);
                    checked++;
                }
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void fractalHasItsSegmentsAndFlows() {
        final XoroshiroRandomSource random = new XoroshiroRandomSource(99L);
        for (int bisections = 0; bisections < 6; bisections++) {
            final MidpointFractal fractal = new MidpointFractal(random, bisections, 0.5, 0.5, 2.5, -1.5);
            assertThat(fractal.segments).hasSize(2 * ((1 << bisections) + 1));
            // The first segment's midpoint is always on the fractal, and its flow is never NONE
            final double mx = (fractal.segments[0] + fractal.segments[2]) / 2, mz = (fractal.segments[1] + fractal.segments[3]) / 2;
            assertThat(fractal.intersectDistance(mx, mz)).isLessThan(1e-9);
            assertThat(fractal.calculateFlow(mx, mz)).isNotEqualTo(Flow.NONE);
        }
    }

    @Test
    void packedPositionsRoundTrip() {
        for (int x : new int[] {-1000, -1, 0, 1, 123456}) {
            for (int z : new int[] {-99999, -2, 0, 7, 654321}) {
                final long key = RiverHelpers.pack(x, z);
                assertThat(RiverHelpers.unpackX(key)).isEqualTo(x);
                assertThat(RiverHelpers.unpackZ(key)).isEqualTo(z);
            }
        }
        assertThat(RiverHelpers.floor(-0.5)).isEqualTo(-1);
        assertThat(RiverHelpers.floor(2.9)).isEqualTo(2);
    }

    @Test
    void fourThreadsSeeTheSameRivers() throws Exception {
        final RegionGenerator shared = generator(-7L);
        final List<String> expected = digest(generator(-7L));
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<List<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> digest(shared)));
            }
            for (Future<List<String>> future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdown();
        }
    }
}
