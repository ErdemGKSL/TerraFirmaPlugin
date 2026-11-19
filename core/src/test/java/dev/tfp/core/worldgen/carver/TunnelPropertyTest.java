/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.carver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Properties of the tunnel masks that hold for every seed and chunk, not only for the golden samples. */
class TunnelPropertyTest {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    private static PositionalRandom fork(long seed) {
        return PositionalRandom.fork(new XoroshiroRandomSource(seed));
    }

    private static TunnelMask mask(long seed, int cx, int cz) {
        return TunnelCarvers.mask(fork(seed), cx, cz, MIN_Y, HEIGHT);
    }

    @Test
    void sameSeedAndChunkGiveTheSameMask() {
        for (long seed : new long[] {0L, 42L, -99L}) {
            assertThat(mask(seed, -7, -2).toLongArray()).isEqualTo(mask(seed, -7, -2).toLongArray());
        }
        // Another seed gives other caves (seed 0, chunk -7,-2 is a golden row with 16296 positions)
        assertThat(mask(0L, -7, -2).toLongArray()).isNotEqualTo(mask(1L, -7, -2).toLongArray());
    }

    @Test
    void allCarversAreTheUnionOfEachCarverAlone() {
        final PositionalRandom fork = fork(-7654321L);
        for (int[] chunk : new int[][] {{-9, 2}, {-9, 3}, {-2, -2}, {0, 0}}) {
            final TunnelMask all = TunnelCarvers.mask(fork, chunk[0], chunk[1], MIN_Y, HEIGHT);
            final List<TunnelMask> single = new ArrayList<>();
            for (TunnelCarvers.Carver carver : TunnelCarvers.Carver.values()) {
                single.add(TunnelCarvers.mask(fork, chunk[0], chunk[1], MIN_Y, HEIGHT, carver));
            }
            int count = 0;
            for (int y = MIN_Y - 2; y < MIN_Y + HEIGHT + 2; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        boolean any = false;
                        for (TunnelMask mask : single) {
                            any |= mask.contains(x, y, z);
                        }
                        assertThat(all.contains(x, y, z)).isEqualTo(any);
                        count += any ? 1 : 0;
                    }
                }
            }
            assertThat(all.count()).isEqualTo(count);
            assertThat(all.isEmpty()).isEqualTo(count == 0);
        }
    }

    /** WorldCarver.carveEllipsoid never carves the lowest two layers nor the highest 7, and TFC's y ranges bound the rest. */
    @Test
    void positionsStayInsideTheCarversVerticalRange() {
        int found = 0;
        for (int cx = -6; cx <= 6; cx += 3) {
            for (int cz = -6; cz <= 6; cz += 3) {
                final TunnelMask mask = mask(1234567890123L, cx, cz);
                found += mask.count();
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        assertThat(mask.contains(x, MIN_Y, z)).isFalse();
                        assertThat(mask.contains(x, MIN_Y + 1, z)).isFalse();
                        for (int y = MIN_Y + HEIGHT - 7; y < MIN_Y + HEIGHT + 4; y++) {
                            assertThat(mask.contains(x, y, z)).isFalse();
                        }
                        // Outside the world
                        assertThat(mask.contains(x, MIN_Y - 1, z)).isFalse();
                    }
                }
            }
        }
        assertThat(found).isPositive();
    }

    /** A world of another height gives masks of its own range: the canyon's width factors are per layer of the world. */
    @Test
    void maskFollowsTheWorldHeight() {
        final PositionalRandom fork = fork(0L);
        final TunnelMask low = TunnelCarvers.mask(fork, 9, 2, 0, 64);
        assertThat(low.minY()).isZero();
        assertThat(low.height()).isEqualTo(64);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertThat(low.contains(x, 1, z)).isFalse();
                assertThat(low.contains(x, 57, z)).isFalse();
                assertThat(low.contains(x, -5, z)).isFalse();
            }
        }
    }

    /** The carvers are in most chunks' reach: over a 21 x 21 area every carver appears, the deep caves most often. */
    @Test
    void everyCarverAppearsAtTfcsRates() {
        final PositionalRandom fork = fork(42L);
        final int[] chunks = new int[3];
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                for (TunnelCarvers.Carver carver : TunnelCarvers.Carver.values()) {
                    if (!TunnelCarvers.mask(fork, cx, cz, MIN_Y, HEIGHT, carver).isEmpty()) {
                        chunks[carver.ordinal()]++;
                    }
                }
            }
        }
        // The oracle's counts for seed 42 (441 chunks): cave 49, deep cave 334, canyon 19
        assertThat(chunks).containsExactly(49, 334, 19);
    }

    /** The start chunk decision is one float against the probability of the configured carver. */
    @Test
    void startChunkRatesMatchTheProbabilities() {
        final PositionalRandom fork = fork(7L);
        final int[] starts = new int[3];
        final int samples = 200 * 200;
        for (int cx = 0; cx < 200; cx++) {
            for (int cz = 0; cz < 200; cz++) {
                starts[0] += CaveTunnels.isStartChunk(CaveTunnels.CAVE, fork.at(cx, 1, cz)) ? 1 : 0;
                starts[1] += CaveTunnels.isStartChunk(CaveTunnels.DEEP_CAVE, fork.at(cx, 2, cz)) ? 1 : 0;
                starts[2] += CanyonTunnels.isStartChunk(fork.at(cx, 3, cz)) ? 1 : 0;
            }
        }
        // Binomial 5 sigma bounds around 0.02, 0.08 and 0.01 of 40000
        assertThat(starts[0]).isBetween(800 - 140, 800 + 140);
        assertThat(starts[1]).isBetween(3200 - 272, 3200 + 272);
        assertThat(starts[2]).isBetween(400 - 100, 400 + 100);
        assertThat(samples).isEqualTo(40000);
    }

    /** The 1.21.1 float sine: exact at the quarter turns, and a table lookup elsewhere. */
    @Test
    void sineIsMinecraft1211FloatTable() {
        assertThat(CarverMath.sin(0.0f)).isEqualTo(0.0f);
        assertThat(CarverMath.sin((float) (Math.PI / 2))).isEqualTo(1.0f);
        assertThat(CarverMath.cos(0.0f)).isEqualTo(1.0f);
        assertThat(CarverMath.cos((float) Math.PI)).isEqualTo(-1.0f);
        assertThat(CarverMath.sin(1.0f)).isEqualTo((float) Math.sin(10430.0 * Math.PI * 2.0 / 65536.0));
        assertThat(CarverMath.sin(-1.0f)).isEqualTo((float) Math.sin((65536 - 10430) * Math.PI * 2.0 / 65536.0));
    }

    /** Masks are built from call-local state only: concurrent builds of the same and of other chunks agree with serial. */
    @Test
    void concurrentBuildsMatchSerial() throws Exception {
        final PositionalRandom fork = fork(-7654321L);
        final int[][] chunks = {{-10, 2}, {-9, 2}, {-9, 3}, {-6, -1}, {-2, -2}, {-10, 2}, {-9, 2}, {-9, 3}};
        final List<long[]> serial = new ArrayList<>();
        for (int[] chunk : chunks) {
            serial.add(TunnelCarvers.mask(fork, chunk[0], chunk[1], MIN_Y, HEIGHT).toLongArray());
        }
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            final List<Future<long[]>> futures = new ArrayList<>();
            for (int[] chunk : chunks) {
                final Callable<long[]> task = () -> TunnelCarvers.mask(fork, chunk[0], chunk[1], MIN_Y, HEIGHT).toLongArray();
                futures.add(pool.submit(task));
            }
            for (int i = 0; i < chunks.length; i++) {
                assertThat(futures.get(i).get()).isEqualTo(serial.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** The mask is the carve decision's tunnel input: outside it the block is kept, inside it the lava level and aquifer decide. */
    @Test
    void maskFeedsTheCarveDecision() {
        final long seed = 0L;
        final PositionalRandom fork = fork(seed);
        final int[] heights = new int[16];
        java.util.Arrays.fill(heights, 70);
        int carved = 0;
        int lava = 0;
        // Deep caves reach the lava level (8 above the bottom) only in some chunks: scan a strip of them
        for (int cx = -10; cx <= 10 && (carved == 0 || lava == 0); cx++) {
            final int cz = -2;
            final TunnelMask mask = TunnelCarvers.mask(fork, cx, cz, MIN_Y, HEIGHT);
            final Aquifer aquifer = new Aquifer(cx, cz, MIN_Y, 63, fork, (x, y, z) -> 0.0, heights);
            for (int y = MIN_Y; y < 70; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        final boolean inCave = mask.contains(lx, y, lz);
                        final Carve carve = CaveCarver.carve(aquifer, (cx << 4) + lx, y, (cz << 4) + lz, inCave);
                        if (!inCave) {
                            assertThat(carve).isEqualTo(Carve.KEEP);
                        } else if (y <= MIN_Y + CaveCarver.LAVA_ABOVE_BOTTOM) {
                            assertThat(carve).isEqualTo(Carve.LAVA);
                            lava++;
                        }
                        carved += carve == Carve.KEEP ? 0 : 1;
                    }
                }
            }
        }
        assertThat(carved).isPositive();
        assertThat(lava).isPositive();
    }
}
