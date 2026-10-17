/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java @ c3b4d9536 (properties of the
 * generated chunk data; TFC has no test for them, so these are checks of the arithmetic's invariants)
 */
package dev.tfp.core.worldgen.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.region.Units;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class ChunkDataPropertyTest {
    private static final long[] SEEDS = {42L, -7L, 20260917L};

    /** Chunks across negative and positive coordinates, spanning several regions and grid squares. */
    private static List<int[]> chunks() {
        final List<int[]> chunks = new ArrayList<>();
        for (int cx = -40; cx <= 40; cx += 9) {
            for (int cz = -40; cz <= 40; cz += 11) {
                chunks.add(new int[] {cx, cz});
            }
        }
        return chunks;
    }

    private static ChunkDataGenerator generator(long seed) {
        final boolean finite = seed == 20260917L;
        final Seed levelSeed = Seed.of(seed);
        final RegionGenerator regionGenerator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), levelSeed);
        return new ChunkDataGenerator(regionGenerator, levelSeed);
    }

    @Test
    void sameSeedGivesTheSameChunkDataAndDifferentSeedsDiffer() {
        for (long seed : SEEDS) {
            final ChunkDataGenerator first = generator(seed);
            final ChunkDataGenerator second = generator(seed);
            for (int[] c : chunks()) {
                assertThat(second.generate(c[0], c[1])).as("seed %d chunk %s", seed, c[0] + "," + c[1])
                    .isEqualTo(first.generate(c[0], c[1]));
            }
        }
        final ChunkDataGenerator a = generator(SEEDS[0]);
        final ChunkDataGenerator b = generator(SEEDS[1]);
        assertThat(a.generate(0, 0)).isNotEqualTo(b.generate(0, 0));
    }

    @Test
    void valuesAreInTheirTfcRanges() {
        for (long seed : SEEDS) {
            final ChunkDataGenerator generator = generator(seed);
            for (int[] c : chunks()) {
                final ChunkData data = generator.generate(c[0], c[1]);
                assertThat(data.chunkX()).isEqualTo(c[0]);
                assertThat(data.chunkZ()).isEqualTo(c[1]);
                for (int lx = 0; lx < 16; lx += 5) {
                    for (int lz = 0; lz < 16; lz += 5) {
                        final int x = c[0] * 16 + lx;
                        final int z = c[1] * 16 + lz;
                        assertThat(data.averageRainfall(x, z)).isBetween(0f, 500f);
                        assertThat(data.rainVariance(x, z)).isBetween(-1f, 1f);
                        assertThat(data.baseGroundwater(x, z)).isBetween(0f, 500f);
                        assertThat(data.averageGroundwater(x, z)).isBetween(0f, 500f);
                        assertThat(data.averageGroundwater(x, z))
                            .isEqualTo(Math.min(data.baseGroundwater(x, z) + data.averageRainfall(x, z), 500f));
                    }
                }
            }
        }
    }

    @Test
    void chunkValuesStayInsideTheRegionCorners() {
        // Bilinear interpolation never leaves the range of the four grid points around the chunk
        for (long seed : SEEDS) {
            final Seed levelSeed = Seed.of(seed);
            final RegionGenerator regionGenerator = new RegionGenerator(new RegionSettings(0.5f, seed == 20260917L, 20_000, 0f, 20_000, 0f), levelSeed);
            final ChunkDataGenerator generator = new ChunkDataGenerator(regionGenerator, levelSeed);
            for (int[] c : chunks()) {
                final int gridX = Units.blockToGrid(c[0] << 4);
                final int gridZ = Units.blockToGrid(c[1] << 4);
                final Region.Point[] corners = corners(regionGenerator, gridX, gridZ);
                final ChunkData data = generator.generate(c[0], c[1]);
                for (int lx = 0; lx < 16; lx += 3) {
                    for (int lz = 0; lz < 16; lz += 3) {
                        final int x = c[0] * 16 + lx;
                        final int z = c[1] * 16 + lz;
                        final float tolerance = 1e-3f;
                        assertInHull(data.averageRainfall(x, z), corners, p -> p.rainfall, tolerance);
                        assertInHull(data.rainVariance(x, z), corners, p -> p.rainfallVariance, tolerance);
                        assertInHull(data.averageSeaLevelTemperature(x, z), corners, p -> p.temperature, tolerance);
                    }
                }
            }
        }
    }

    @Test
    void neighbouringBlocksChangeSmoothly() {
        // Each block step moves a bilinear value by at most the corner spread over one grid unit (128 blocks)
        for (long seed : SEEDS) {
            final Seed levelSeed = Seed.of(seed);
            final RegionGenerator regionGenerator = new RegionGenerator(new RegionSettings(0.5f, seed == 20260917L, 20_000, 0f, 20_000, 0f), levelSeed);
            final ChunkDataGenerator generator = new ChunkDataGenerator(regionGenerator, levelSeed);
            // Rows through the chunk seams at x = 0, +-16 and the grid seam at x = 0 and x = -128 (block columns -70..69)
            for (int z : new int[] {-300, -5, 7, 133, 400}) {
                final int gridZ = Units.blockToGrid(z);
                for (int x = -70; x < 70; x++) {
                    final ChunkData data = generator.generate(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                    final ChunkData next = generator.generate(Math.floorDiv(x + 1, 16), Math.floorDiv(z, 16));
                    assertStep(data, next, x, z, regionGenerator, Units.blockToGrid(x), Units.blockToGrid(x + 1), gridZ);
                }
            }
        }
    }

    @Test
    void rockLayersDeepenMonotonically() {
        final ChunkDataGenerator generator = generator(SEEDS[0]);
        for (int x = -90; x <= 90; x += 13) {
            for (int z = -90; z <= 90; z += 17) {
                int previousLayer = 0;
                for (int y = 110; y >= -60; y--) {
                    final int layer = generator.generateRock(x, y, z, 110).layer();
                    assertThat(layer).isGreaterThanOrEqualTo(previousLayer);
                    assertThat(layer).isLessThanOrEqualTo(previousLayer + 1);
                    previousLayer = layer;
                }
            }
        }
    }

    @Test
    void fourThreadsSharingOneGeneratorMatchTheSequentialValues() throws Exception {
        final ChunkDataGenerator shared = generator(SEEDS[2]);
        final List<int[]> chunks = chunks();
        final List<ChunkData> expected = new ArrayList<>();
        for (int[] c : chunks) {
            expected.add(generator(SEEDS[2]).generate(c[0], c[1]));
        }

        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<ChunkData>> futures = new ArrayList<>();
            for (int[] c : chunks) {
                futures.add(pool.submit(() -> shared.generate(c[0], c[1])));
            }
            for (int i = 0; i < chunks.size(); i++) {
                assertThat(futures.get(i).get()).isEqualTo(expected.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void climateFallsBackToNeutralWithoutChunkData() {
        assertThat(ChunkData.climateOrNeutral(null, 5, -9)).isEqualTo(ChunkClimate.NEUTRAL);

        final ChunkDataGenerator generator = generator(SEEDS[0]);
        final ChunkData data = generator.generate(-3, 2);
        final ChunkClimate climate = ChunkData.climateOrNeutral(data, -45, 40);
        assertThat(climate).isEqualTo(data.climateAt(-45, 40));
        assertThat(climate.averageRainfall()).isEqualTo(data.averageRainfall(-45, 40));
    }

    private static Region.Point[] corners(RegionGenerator regionGenerator, int gridX, int gridZ) {
        return new Region.Point[] {
            regionGenerator.getOrCreateRegionPoint(gridX, gridZ),
            regionGenerator.getOrCreateRegionPoint(gridX, gridZ + 1),
            regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ),
            regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ + 1),
        };
    }

    private static void assertInHull(float value, Region.Point[] corners, ToDoubleFunction<Region.Point> field, float tolerance) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Region.Point p : corners) {
            min = Math.min(min, field.applyAsDouble(p));
            max = Math.max(max, field.applyAsDouble(p));
        }
        assertThat((double) value).isBetween(min - tolerance, max + tolerance);
    }

    private static void assertStep(ChunkData data, ChunkData next, int x, int z, RegionGenerator regionGenerator, int gridX, int gridXNext, int gridZ) {
        final int lx = Math.floorMod(x, 16);
        final int lxNext = Math.floorMod(x + 1, 16);
        final float tolerance = 1e-3f;
        final double rainBound = maxSpread(regionGenerator, gridX, gridXNext, gridZ, p -> p.rainfall);
        final double varianceBound = maxSpread(regionGenerator, gridX, gridXNext, gridZ, p -> p.rainfallVariance);
        final double temperatureBound = maxSpread(regionGenerator, gridX, gridXNext, gridZ, p -> p.temperature);
        final float rainStep = Math.abs(next.averageRainfall(x + 1, z) - data.averageRainfall(x, z));
        final float varianceStep = Math.abs(next.rainVariance(x + 1, z) - data.rainVariance(x, z));
        final float temperatureStep = Math.abs(next.averageSeaLevelTemperature(x + 1, z) - data.averageSeaLevelTemperature(x, z));
        assertThat((double) rainStep).as("rain step at %d,%d (local %d -> %d)", x, z, lx, lxNext).isLessThanOrEqualTo(rainBound + tolerance);
        assertThat((double) varianceStep).as("variance step at %d,%d", x, z).isLessThanOrEqualTo(varianceBound + tolerance);
        assertThat((double) temperatureStep).as("temperature step at %d,%d", x, z).isLessThanOrEqualTo(temperatureBound + tolerance);
    }

    /** The larger corner spread of the two grid squares that a block step touches, per block (one grid unit is 128 blocks). */
    private static double maxSpread(RegionGenerator regionGenerator, int gridX, int gridXNext, int gridZ, ToDoubleFunction<Region.Point> field) {
        return Math.max(spread(regionGenerator, gridX, gridZ, field), spread(regionGenerator, gridXNext, gridZ, field)) / Units.GRID_WIDTH_IN_BLOCK;
    }

    private static double spread(RegionGenerator regionGenerator, int gridX, int gridZ, ToDoubleFunction<Region.Point> field) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Region.Point p : corners(regionGenerator, gridX, gridZ)) {
            min = Math.min(min, field.applyAsDouble(p));
            max = Math.max(max, field.applyAsDouble(p));
        }
        return max - min;
    }
}
