/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.river;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Properties of the chunk-level river decision: determinism per seed and across threads, nothing changes without a river,
 * the river heights and the opened positions stay inside the bounds TFC's samplers can reach, water only below the sea level,
 * and the river biome only next to a river in a biome that has rivers.
 */
class RiverColumnPropertyTest {
    /** River chunks found by the golden scan (seed 42): two with rivers in every column, one partly. */
    private static final int[][] RIVER_CHUNKS = {{-700, -592}, {-700, -534}, {37, 70}, {-301, 840}};
    private static final int NO_RIVER_CHUNK_X = -700;
    private static final int NO_RIVER_CHUNK_Z = -650;
    /** The lowest river height of any sampler below its input: tall canyon, {@code 55 - 0.3 * 16 - 7}. */
    private static final double MIN_RIVER_HEIGHT = 43;
    /** The highest raised bank: tall banked, {@code 57 + 9 + 2}. */
    private static final double MAX_RAISED_BANK = 68;
    /** Above this no sampler opens a block: the tall canyon cliffs end at sea level + 35. */
    private static final int MAX_OPENED_Y = RiverNoise.SEA_LEVEL_Y + 35;

    private static RegionGenerator generator(long seed) {
        return new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(seed));
    }

    private static List<Double> decisions(RegionGenerator g, Map<RiverBlendType, RiverNoiseSampler> samplers, int cx, int cz) {
        final RiverChunk chunk = RiverChunk.sample(g, cx, cz);
        final List<Double> out = new ArrayList<>();
        for (RiverBlendType type : RiverBlendType.ALL) {
            for (int lx = 0; lx < 16; lx += 5) {
                for (int lz = 0; lz < 16; lz += 5) {
                    final RiverColumn column = RiverColumn.sample(samplers, type, chunk.info(lx, lz), (cx << 4) + lx, (cz << 4) + lz, 90);
                    out.add(column.height());
                    for (int y = 40; y <= 100; y += 3) {
                        out.add(column.density(y, RiverColumn.SOLID, column.surfaceHeight()));
                    }
                    out.add((double) chunk.flow(lx, lz).ordinal());
                }
            }
        }
        return out;
    }

    @Test
    void deterministicPerSeed() {
        for (int[] c : RIVER_CHUNKS) {
            final List<Double> a = decisions(generator(42L), RiverBlendType.createSamplers(42L), c[0], c[1]);
            final List<Double> b = decisions(generator(42L), RiverBlendType.createSamplers(42L), c[0], c[1]);
            assertThat(a).isEqualTo(b);
        }
        assertThat(decisions(generator(42L), RiverBlendType.createSamplers(42L), -700, -592))
            .isNotEqualTo(decisions(generator(42L), RiverBlendType.createSamplers(43L), -700, -592));
    }

    @Test
    void deterministicAcrossThreads() throws Exception {
        final RegionGenerator g = generator(42L);
        final Map<RiverBlendType, RiverNoiseSampler> samplers = RiverBlendType.createSamplers(42L);
        final List<Double> expected = decisions(generator(42L), RiverBlendType.createSamplers(42L), 37, 70);
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<List<Double>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> decisions(g, samplers, 37, 70)));
            }
            for (Future<List<Double>> f : futures) {
                assertThat(f.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nothingChangesWithoutARiver() {
        final RegionGenerator g = generator(42L);
        final Map<RiverBlendType, RiverNoiseSampler> samplers = RiverBlendType.createSamplers(42L);
        final RiverChunk chunk = RiverChunk.sample(g, NO_RIVER_CHUNK_X, NO_RIVER_CHUNK_Z);
        assertThat(chunk.isEmpty()).isTrue();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                assertThat(chunk.flow(lx, lz)).isEqualTo(Flow.NONE);
                for (RiverBlendType type : RiverBlendType.ALL) {
                    final RiverColumn column = RiverColumn.sample(samplers, type, chunk.info(lx, lz), lx, lz, 77.5);
                    assertThat(column.hasRiver()).isFalse();
                    assertThat(column.height()).isEqualTo(77.5);
                    for (int y = -64; y <= 200; y += 4) {
                        assertThat(column.opens(y, 77)).isFalse();
                    }
                    assertThat(column.isRiverBiome(TfcBiomes.PLAINS, 60)).isFalse();
                }
            }
        }
    }

    @Test
    void heightsAndOpeningsStayInsideTfcBounds() {
        final RegionGenerator g = generator(42L);
        final Map<RiverBlendType, RiverNoiseSampler> samplers = RiverBlendType.createSamplers(42L);
        final double[] inputs = {30, 50, 62, 64, 75, 100, 160};
        int opened = 0;
        for (int[] c : RIVER_CHUNKS) {
            final RiverChunk chunk = RiverChunk.sample(g, c[0], c[1]);
            assertThat(chunk.isEmpty()).isFalse();
            for (int lx = 0; lx < 16; lx += 3) {
                for (int lz = 0; lz < 16; lz += 3) {
                    final RiverInfo info = chunk.info(lx, lz);
                    if (info == null) {
                        continue;
                    }
                    assertThat(info.distSq()).isBetween(0.0, 50.0 * 50.0);
                    assertThat(info.widthSq()).isPositive();
                    for (RiverBlendType type : RiverBlendType.ALL) {
                        for (double heightIn : inputs) {
                            final RiverColumn column = RiverColumn.sample(samplers, type, info, (c[0] << 4) + lx, (c[1] << 4) + lz, heightIn);
                            assertThat(column.height())
                                .isBetween(Math.min(heightIn, MIN_RIVER_HEIGHT), Math.max(heightIn, MAX_RAISED_BANK));
                            final int top = column.surfaceHeight();
                            for (int y = -64; y <= top; y++) {
                                if (column.opens(y, top)) {
                                    opened++;
                                    assertThat(y).isGreaterThan((int) MIN_RIVER_HEIGHT).isLessThanOrEqualTo(MAX_OPENED_Y);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(opened).as("some sampler opens blocks below the surface (canyons, caves)").isPositive();
    }

    @Test
    void waterOnlyBelowSeaLevelAndRiverWaterOnlyWithFlow() {
        assertThat(RiverColumn.isWater(RiverNoise.SEA_LEVEL_Y - 1)).isTrue();
        assertThat(RiverColumn.isWater(RiverNoise.SEA_LEVEL_Y)).isFalse();
        assertThat(RiverColumn.isRiverWater(60, Flow.NONE, 50)).isFalse();
        assertThat(RiverColumn.isRiverWater(59, Flow.valueOf(3), 50)).isTrue();
        assertThat(RiverColumn.isRiverWater(58, Flow.valueOf(3), 70)).isFalse();
        assertThat(RiverColumn.isRiverWater(52, Flow.valueOf(3), 50)).isTrue();
    }

    @Test
    void riverBiomeOnlyNearARiverInARiverBiome() {
        final RegionGenerator g = generator(42L);
        final Map<RiverBlendType, RiverNoiseSampler> samplers = RiverBlendType.createSamplers(42L);
        final RiverChunk chunk = RiverChunk.sample(g, -700, -592);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final RiverInfo info = chunk.info(lx, lz);
                final RiverColumn column = RiverColumn.sample(samplers, RiverBlendType.FLOODPLAIN, info, lx, lz, 70);
                final boolean near = info != null && info.normDistSq() < RiverColumn.RIVER_BIOME_NORM_DIST_SQ;
                assertThat(column.isRiverBiome(TfcBiomes.PLAINS, 60)).isEqualTo(near);
                assertThat(column.isRiverBiome(TfcBiomes.PLAINS, RiverNoise.SEA_LEVEL_Y + 2)).isFalse();
                assertThat(column.isRiverBiome(TfcBiomes.OCEAN, 60)).isFalse();
            }
        }
    }

    @Test
    void biomeTableFollowsTfc() {
        assertThat(RiverBiomes.size()).isEqualTo(TfcBiomes.COUNT);
        assertThat(RiverBiomes.blendType(TfcBiomes.PLAINS)).isEqualTo(RiverBlendType.FLOODPLAIN);
        assertThat(RiverBiomes.blendType(TfcBiomes.MOUNTAINS)).isEqualTo(RiverBlendType.CAVE);
        assertThat(RiverBiomes.blendType(TfcBiomes.OCEAN)).isEqualTo(RiverBlendType.NONE);
        assertThat(RiverBiomes.hasRivers(TfcBiomes.OCEAN)).isFalse();
        assertThat(RiverBiomes.hasRivers(TfcBiomes.PLAINS)).isTrue();
        assertThat(RiverBiomes.hasRivers(TfcBiomes.RIVER)).isTrue();
        assertThat(RiverBiomes.hasRivers(TfcBiomes.RIFT_LAKE)).isFalse();
        assertThat(RiverBiomes.blendType(-1)).isEqualTo(RiverBlendType.NONE);
        assertThat(RiverBiomes.hasRivers(TfcBiomes.COUNT)).isFalse();
    }
}
