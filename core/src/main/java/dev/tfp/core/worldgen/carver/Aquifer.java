/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/TFCAquifer.java @ c3b4d9536 (sampleState, calculatePressure, createAquifer,
 * globalAquifer and the three nearest aquifers). The barrier noise is an input; see {@link BarrierNoise}.
 */
package dev.tfp.core.worldgen.carver;

import dev.tfp.core.worldgen.noise.Cellular3D;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashMap;
import java.util.Map;

/**
 * TFC's aquifers for one chunk: the fluid (water or lava, or air above it) that fills a cave or a gap at a position. Aquifer
 * centres are hashed per 16 x 12 x 16 grid cell, and the three nearest decide the pressure between their fluid levels.
 *
 * <p>The per-chunk surface heights are the 4 x 4 array of TFC's {@code TFCAquifer.surfaceHeights}: index {@code sx + 4 * sz}
 * holds the surface height of the 16-block section at {@code chunkX * 16 - 16 + 16 * sx} (and the same for z). Pure, but the
 * caches make an instance single-threaded: create one per chunk and per worker.
 */
public final class Aquifer {
    private static final int GRID_WIDTH = 16;
    private static final int GRID_HEIGHT = 12;
    private static final int XZ_RANGE = 10;
    private static final int Y_RANGE = 8;
    /** The global lava aquifer covers the bottom 10 blocks: {@code minY + 10}. */
    private static final int LAVA_ABOVE_BOTTOM = 10;
    /** A fluid cell below this height may hold lava (one in three). */
    private static final float LAVA_CELL_MAX_Y = 40;

    /**
     * The aquifer barrier noise, sampled at {@code (x, y * 0.5, z)} when two aquifers are at a similar pressure. TFC uses the
     * vanilla {@code aquifer_barrier} normal noise; the plugin passes its own function (see the ledger).
     */
    @FunctionalInterface
    public interface BarrierNoise {
        double getValue(double x, double y, double z);
    }

    /** A grid cell, or a block position in one of the cells (the cell it was hashed for). */
    private record Pos(int x, int y, int z) {}

    /** A fluid below {@code fluidY}, and air at or above it. */
    private record Entry(Carve fluid, int fluidY) {
        Carve at(int y) {
            return y < fluidY ? fluid : Carve.AIR;
        }
    }

    private final int minChunkX;
    private final int minChunkZ;
    private final int minY;
    private final int seaLevel;
    private final PositionalRandom fork;
    private final BarrierNoise barrier;
    private final int[] surfaceHeights;
    private final long fluidCellSeed;
    private final Cellular3D fluidCellNoise;
    private final Entry lavaLevel;
    private final Entry seaLevelEntry;
    private final Map<Pos, Pos> locations = new HashMap<>();
    private final Map<Pos, Entry> entries = new HashMap<>();

    /**
     * @param chunkX         the chunk x of the chunk (block x is {@code 16 * chunkX})
     * @param chunkZ         the chunk z of the chunk
     * @param minY           the world's lowest block y (-64 in the overworld)
     * @param seaLevel       the sea level (63)
     * @param fork           the noise sampler's positional random factory
     * @param barrier        the barrier noise
     * @param surfaceHeights the 4 x 4 surface heights of the chunk and its neighbours (copied)
     */
    public Aquifer(int chunkX, int chunkZ, int minY, int seaLevel, PositionalRandom fork, BarrierNoise barrier, int[] surfaceHeights) {
        if (surfaceHeights.length != 16) {
            throw new IllegalArgumentException("the aquifer surface heights are a 4 x 4 array, got " + surfaceHeights.length);
        }
        this.minChunkX = chunkX * 16;
        this.minChunkZ = chunkZ * 16;
        this.minY = minY;
        this.seaLevel = seaLevel;
        this.fork = fork;
        this.barrier = barrier;
        this.surfaceHeights = surfaceHeights.clone();

        final XoroshiroRandomSource fluidCellRandom = fork.fromHashOf("aquifer_fluid_cell_noise");
        this.fluidCellSeed = fluidCellRandom.nextLong();
        this.fluidCellNoise = new Cellular3D(fluidCellRandom.nextLong()).spread(0.015f);

        this.lavaLevel = new Entry(Carve.LAVA, minY + LAVA_ABOVE_BOTTOM);
        this.seaLevelEntry = new Entry(Carve.WATER, seaLevel);
    }

    /** The lowest block y this aquifer was built for. */
    public int minY() {
        return minY;
    }

    /**
     * TFC's {@code TFCAquifer.sampleState} for a position with the given terrain density (the carver passes 0). Returns the
     * fluid that fills the position, or {@link Carve#KEEP} where solid rock (or a barrier) stays.
     *
     * @throws IllegalArgumentException if the position is outside the three chunks the surface heights cover
     */
    public Carve sample(int x, int y, int z, double terrainNoise) {
        if (terrainNoise > 0) {
            return Carve.KEEP;
        }
        final Entry global = globalAquifer(y);
        final Carve state;
        final double contribution;

        if (global.at(y) == Carve.LAVA) {
            // Always lava below the lava level, and no border with its neighbours
            state = Carve.LAVA;
            contribution = 0;
        } else {
            final int lowerGridX = Math.floorDiv(x - XZ_RANGE / 2, GRID_WIDTH);
            final int lowerGridY = Math.floorDiv(y, GRID_HEIGHT);
            final int lowerGridZ = Math.floorDiv(z - XZ_RANGE / 2, GRID_WIDTH);

            // The closest three aquifers, by squared distance, in the order TFC keeps them
            int distance1 = Integer.MAX_VALUE;
            int distance2 = Integer.MAX_VALUE;
            int distance3 = Integer.MAX_VALUE;
            Pos aquifer1 = null;
            Pos aquifer2 = null;
            Pos aquifer3 = null;

            for (int offsetGridX = 0; offsetGridX <= 1; offsetGridX++) {
                for (int offsetGridY = -1; offsetGridY <= 1; offsetGridY++) {
                    for (int offsetGridZ = 0; offsetGridZ <= 1; offsetGridZ++) {
                        final Pos cell = new Pos(lowerGridX + offsetGridX, lowerGridY + offsetGridY, lowerGridZ + offsetGridZ);
                        final Pos adjAquifer = locations.computeIfAbsent(cell, this::location);

                        final int dx = adjAquifer.x() - x;
                        final int dy = adjAquifer.y() - y;
                        final int dz = adjAquifer.z() - z;
                        final int distance = dx * dx + dy * dy + dz * dz;

                        if (distance <= distance1) {
                            aquifer3 = aquifer2;
                            aquifer2 = aquifer1;
                            aquifer1 = adjAquifer;
                            distance3 = distance2;
                            distance2 = distance1;
                            distance1 = distance;
                        } else if (distance <= distance2) {
                            aquifer3 = aquifer2;
                            aquifer2 = adjAquifer;
                            distance3 = distance2;
                            distance2 = distance;
                        } else if (distance <= distance3) {
                            aquifer3 = adjAquifer;
                            distance3 = distance;
                        }
                    }
                }
            }

            final Entry entry1 = entry(aquifer1);
            final Entry entry2 = entry(aquifer2);
            final Entry entry3 = entry(aquifer3);

            final double similarity12 = similarity(distance1, distance2);
            final double similarity13 = similarity(distance1, distance3);
            final double similarity23 = similarity(distance2, distance3);

            if (entry1.at(y) == Carve.WATER && globalAquifer(y - 1).at(y - 1) == Carve.LAVA) {
                // Border lava and water with solid blocks
                contribution = 1;
            } else if (similarity12 > -1) {
                final double[] barrierNoise = {Double.NaN};
                final double pressure12 = pressure(x, y, z, barrierNoise, entry1, entry2);
                final double pressure13 = pressure(x, y, z, barrierNoise, entry1, entry3);
                final double pressure23 = pressure(x, y, z, barrierNoise, entry2, entry3);

                final double clampedSimilarity12 = Math.max(0, similarity12);
                final double clampedSimilarity13 = Math.max(0, similarity13);
                final double clampedSimilarity23 = Math.max(0, similarity23);

                contribution = Math.max(0, 2 * clampedSimilarity12
                    * Math.max(pressure12, Math.max(pressure13 * clampedSimilarity13, pressure23 * clampedSimilarity23)));
            } else {
                contribution = 0;
            }
            state = entry1.at(y);
        }

        if (terrainNoise + contribution <= 0) {
            return state;
        }
        return Carve.KEEP;
    }

    private static double similarity(int firstDistance, int secondDistance) {
        return 1 - Math.abs(secondDistance - firstDistance) / 25d;
    }

    private double pressure(int x, int y, int z, double[] barrierNoise, Entry left, Entry right) {
        final Carve leftState = left.at(y);
        final Carve rightState = right.at(y);
        if ((leftState == Carve.LAVA && rightState == Carve.WATER) || (leftState == Carve.WATER && rightState == Carve.LAVA)) {
            // Pressure between different fluids is always the maximum
            return 1;
        }

        final int deltaFluidLevel = Math.abs(left.fluidY() - right.fluidY());
        if (deltaFluidLevel == 0) {
            return 0;
        }

        final double averageFluidLevel = 0.5 * (left.fluidY() + right.fluidY());
        final double deltaAboveAverageFluidLevel = y + 0.5 - averageFluidLevel;
        final double deltaNearAverageFluidLevel = (0.5 * deltaFluidLevel) - Math.abs(deltaAboveAverageFluidLevel);

        double pressure;
        if (deltaAboveAverageFluidLevel > 0) {
            pressure = deltaNearAverageFluidLevel / (deltaNearAverageFluidLevel > 0 ? 1.5 : 2.5);
        } else {
            pressure = (deltaNearAverageFluidLevel + 3) / (deltaNearAverageFluidLevel > -3 ? 3 : 10);
        }

        if (pressure < -2 || pressure > 2) {
            // Bounded between [-2, 2]: no barrier noise is needed
            return pressure;
        }

        final double cached = barrierNoise[0];
        if (Double.isNaN(cached)) {
            final double value = barrier.getValue(x, y * 0.5, z);
            barrierNoise[0] = value;
            return value + pressure;
        }
        return cached + pressure;
    }

    /** The aquifer centre hashed for a grid cell: a point in the middle 10 x 8 x 10 of the cell. */
    private Pos location(Pos cell) {
        final XoroshiroRandomSource random = fork.at(cell.x(), cell.y(), cell.z());
        final int x = cell.x() * GRID_WIDTH + random.nextInt(XZ_RANGE) + (GRID_WIDTH - XZ_RANGE) / 2;
        final int y = cell.y() * GRID_HEIGHT + random.nextInt(Y_RANGE) + (GRID_HEIGHT - Y_RANGE) / 2;
        final int z = cell.z() * GRID_WIDTH + random.nextInt(XZ_RANGE) + (GRID_WIDTH - XZ_RANGE) / 2;
        return new Pos(x, y, z);
    }

    private Entry entry(Pos location) {
        return entries.computeIfAbsent(new Pos(Math.floorDiv(location.x(), GRID_WIDTH), Math.floorDiv(location.y(), GRID_HEIGHT),
            Math.floorDiv(location.z(), GRID_WIDTH)), cell -> create(location));
    }

    private Entry create(Pos location) {
        final int dx = location.x() - minChunkX + 16;
        final int dz = location.z() - minChunkZ + 16;
        final int sx = dx >> 4;
        final int sz = dz >> 4;
        if (sx < 0 || sx > 2 || sz < 0 || sz > 2) {
            throw new IllegalArgumentException("aquifer at " + location + " is outside the surface heights of its chunk");
        }
        final int surfaceHeight = surfaceHeights[sx + 4 * sz];

        final int x = location.x();
        final int y = location.y();
        final int z = location.z();
        if (y >= surfaceHeight) {
            // Above the surface, all aquifers are sea level
            return seaLevelEntry;
        }

        final Cellular3D.Cell cell = fluidCellNoise.cell(x, y / 0.6f, z);
        final float cellNoise = (float) cell.noise();
        final float cellY = (float) cell.y();

        if (cellNoise < 0.25f || (cellY > seaLevel - 10 && cellNoise < 0.5f)) {
            return new Entry(Carve.WATER, minY - 1);
        }

        final XoroshiroRandomSource random = new XoroshiroRandomSource(fluidCellSeed, (long) Float.floatToIntBits(cellNoise));
        final float aquiferY = Math.min((random.nextFloat() - random.nextFloat() - 2) * 5 + cellY, surfaceHeight);

        final boolean lava = cellY < LAVA_CELL_MAX_Y && random.nextInt(3) == 0;
        return new Entry(lava ? Carve.LAVA : Carve.WATER, (int) aquiferY);
    }

    private Entry globalAquifer(int y) {
        return y < minY + LAVA_ABOVE_BOTTOM ? lavaLevel : seaLevelEntry;
    }
}
