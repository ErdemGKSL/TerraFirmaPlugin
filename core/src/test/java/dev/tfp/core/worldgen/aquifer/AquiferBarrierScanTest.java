/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.aquifer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.carver.Aquifer;
import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.carver.CaveCarver;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

/**
 * A 100-chunk scan (10 x 10 chunks, every position from y -64 to 99 carved, the worst case for the aquifer) with the real
 * barrier noise: the barrier opens walls between aquifers, which leaves a few fluids next to air, and {@link FluidSupport}
 * dams every one of them, so no water or lava floats.
 */
class AquiferBarrierScanTest {
    private static final int MIN_Y = -64;
    private static final int TOP_Y = 100;
    private static final int SEA = 63;
    private static final int CHUNKS = 10;
    private static final int SIZE = CHUNKS * 16;

    /** The surface height of a 16-block section, the same seen from every chunk. */
    private static int surface(int sectionX, int sectionZ) {
        return 50 + Math.floorMod(sectionX * 31 + sectionZ * 17, 45);
    }

    /** The aquifer's carve of every position of the region, before {@link FluidSupport}. */
    private static Carve[][][] carveRegion(long seed, Aquifer.BarrierNoise barrier) {
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(seed));
        final Carve[][][] grid = new Carve[SIZE][TOP_Y - MIN_Y][SIZE];
        for (int cx = 0; cx < CHUNKS; cx++) {
            for (int cz = 0; cz < CHUNKS; cz++) {
                final int[] heights = new int[16];
                for (int sx = 0; sx < 4; sx++) {
                    for (int sz = 0; sz < 4; sz++) {
                        heights[sx + 4 * sz] = surface(cx - 1 + sx, cz - 1 + sz);
                    }
                }
                final Aquifer aquifer = new Aquifer(cx, cz, MIN_Y, SEA, fork, barrier, heights);
                for (int x = cx * 16; x < cx * 16 + 16; x++) {
                    for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        for (int y = MIN_Y; y < TOP_Y; y++) {
                            grid[x][y - MIN_Y][z] = CaveCarver.carve(aquifer, x, y, z, true);
                        }
                    }
                }
            }
        }
        return grid;
    }

    private static FluidSupport.CarveLookup lookup(Carve[][][] grid) {
        return (x, y, z) -> x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < MIN_Y || y >= TOP_Y
            ? Carve.KEEP
            : grid[x][y - MIN_Y][z];
    }

    private static Carve[][][] settle(Carve[][][] grid) {
        final FluidSupport.CarveLookup before = lookup(grid);
        final Carve[][][] out = new Carve[SIZE][TOP_Y - MIN_Y][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int y = MIN_Y; y < TOP_Y; y++) {
                for (int z = 0; z < SIZE; z++) {
                    out[x][y - MIN_Y][z] = FluidSupport.settle(before, x, y, z);
                }
            }
        }
        return out;
    }

    private static long count(Carve[][][] grid, java.util.function.Predicate<int[]> test) {
        long n = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = MIN_Y; y < TOP_Y; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (test.test(new int[] {x, y, z})) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static long unsupported(Carve[][][] grid) {
        final FluidSupport.CarveLookup l = lookup(grid);
        return count(grid, p -> FluidSupport.unsupported(l, p[0], p[1], p[2]));
    }

    private static long fluids(Carve[][][] grid) {
        return count(grid, p -> {
            final Carve c = grid[p[0]][p[1] - MIN_Y][p[2]];
            return c == Carve.WATER || c == Carve.LAVA;
        });
    }

    @Test
    void noFloatingFluidAfterSettling() {
        long breaches = 0;
        for (long seed : new long[] {0L, 7L}) {
            final Carve[][][] raw = carveRegion(seed, AquiferBarrierNoise.forSeed(seed));
            final long rawUnsupported = unsupported(raw);
            breaches += rawUnsupported;
            final Carve[][][] settled = settle(raw);

            assertThat(unsupported(settled)).as("seed %d", seed).isZero();
            // Only the breached fluids changed, and only into rock
            assertThat(fluids(raw) - fluids(settled)).as("seed %d", seed).isEqualTo(rawUnsupported);
            assertThat(rawUnsupported).as("seed %d", seed).isLessThan(fluids(raw) / 100);
        }
        // The barrier noise is live: it opens some walls the zero noise keeps
        assertThat(breaches).isPositive();
    }

    @Test
    void barrierNoiseChangesTheWalls() {
        final Carve[][][] zero = carveRegion(0L, (x, y, z) -> 0.0);
        final Carve[][][] real = carveRegion(0L, AquiferBarrierNoise.forSeed(0L));
        long differ = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < TOP_Y - MIN_Y; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (zero[x][y][z] != real[x][y][z]) {
                        differ++;
                        // The barrier only moves walls (keep vs the aquifer's own fluid or air); it never swaps fluids
                        assertThat(zero[x][y][z] == Carve.KEEP || real[x][y][z] == Carve.KEEP).isTrue();
                    }
                }
            }
        }
        assertThat(differ).isPositive();
    }

    @Test
    void settleLeavesNonFluidsAlone() {
        final FluidSupport.CarveLookup allAir = (x, y, z) -> y == 0 ? Carve.WATER : Carve.AIR;
        assertThat(FluidSupport.settle(allAir, 0, 1, 0)).isEqualTo(Carve.AIR);
        assertThat(FluidSupport.settle(allAir, 0, 0, 0)).isEqualTo(Carve.KEEP);
        // Water on rock with water beside it and air only above is a still pool surface
        final FluidSupport.CarveLookup pool = (x, y, z) -> y < 0 ? Carve.KEEP : y == 0 ? Carve.WATER : Carve.AIR;
        assertThat(FluidSupport.settle(pool, 3, 0, -2)).isEqualTo(Carve.WATER);
        final FluidSupport.CarveLookup lavaOverAir = (x, y, z) -> y == 0 ? Carve.LAVA : y < 0 ? Carve.AIR : Carve.KEEP;
        assertThat(FluidSupport.settle(lavaOverAir, 0, 0, 0)).isEqualTo(Carve.KEEP);
    }
}
