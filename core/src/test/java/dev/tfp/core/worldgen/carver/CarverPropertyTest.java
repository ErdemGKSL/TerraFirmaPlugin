/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.carver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Properties of the carve decision that hold for every seed and chunk, not only for the golden samples. */
class CarverPropertyTest {
    private static final int MIN_Y = -64;
    private static final int SEA_LEVEL = 63;

    private static Aquifer aquifer(long seed, int cx, int cz, int[] heights) {
        PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(seed));
        return new Aquifer(cx, cz, MIN_Y, SEA_LEVEL, fork, (x, y, z) -> 0.0, heights);
    }

    private static int[] uniform(int height) {
        int[] heights = new int[16];
        Arrays.fill(heights, height);
        return heights;
    }

    @Test
    void sameSeedAndChunkGiveSameDecisions() {
        for (long seed : new long[] {0L, 1L, -99L, 1234567890123L}) {
            String first = AquiferGoldenTest.decisions(seed, -4, 9);
            assertThat(AquiferGoldenTest.decisions(seed, -4, 9)).isEqualTo(first);
        }
    }

    @Test
    void decisionDoesNotDependOnQueryOrder() {
        int[] heights = {50, 60, 70, 45, 55, 65, 48, 58, 68, 52, 62, 72, 44, 54, 64, 49};
        Aquifer forward = aquifer(42L, 2, 3, heights);
        Aquifer backward = aquifer(42L, 2, 3, heights);
        int[] ys = {-60, -30, -5, 20, 48, 61, 75};
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        for (int x = 32; x < 48; x += 3) {
            for (int z = 48; z < 64; z += 3) {
                for (int y : ys) {
                    positions.add(new int[] {x, y, z});
                }
            }
        }
        assertThat(positions).hasSize(6 * 6 * 7);
        Carve[] inOrder = new Carve[positions.size()];
        Carve[] reversed = new Carve[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            int[] p = positions.get(i);
            inOrder[i] = forward.sample(p[0], p[1], p[2], 0.0);
        }
        for (int i = positions.size() - 1; i >= 0; i--) {
            int[] p = positions.get(i);
            reversed[i] = backward.sample(p[0], p[1], p[2], 0.0);
        }
        assertThat(reversed).containsExactly(inOrder);
    }

    @Test
    void seedsChangeTheDecisions() {
        assertThat(AquiferGoldenTest.decisions(0L, 0, 0)).isNotEqualTo(AquiferGoldenTest.decisions(42L, 0, 0));
    }

    @Test
    void nothingIsCarvedAboveTheSurfaceExceptSeaLevelWater() {
        int surface = 50;
        for (long seed : new long[] {3L, -17L}) {
            Aquifer aquifer = aquifer(seed, 0, 0, uniform(surface));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = surface; y <= 120; y++) {
                        Carve carve = aquifer.sample(x, y, z, 0.0);
                        assertThat(carve).isNotEqualTo(Carve.LAVA);
                        if (y >= SEA_LEVEL) {
                            assertThat(carve).isIn(Carve.AIR, Carve.KEEP);
                        } else {
                            assertThat(carve).isIn(Carve.AIR, Carve.WATER, Carve.KEEP);
                        }
                    }
                }
            }
        }
    }

    @Test
    void solidTerrainIsNeverFilled() {
        Aquifer aquifer = aquifer(9L, 1, 1, uniform(60));
        for (int y = -64; y < 80; y += 4) {
            assertThat(aquifer.sample(20, y, 20, 0.5)).isEqualTo(Carve.KEEP);
        }
    }

    @Test
    void lavaFillsBelowTheLavaLevelInsideTheTunnel() {
        Aquifer aquifer = aquifer(5L, -2, 7, uniform(70));
        // The carve's lava level is minY + 8 (inclusive); the aquifer's own lava band is below minY + 10, so only the first is exact
        for (int y = MIN_Y; y <= MIN_Y + CaveCarver.LAVA_ABOVE_BOTTOM; y++) {
            assertThat(CaveCarver.carve(aquifer, -20, y, 110, true)).isEqualTo(Carve.LAVA);
        }
    }

    @Test
    void positionsOutsideTheTunnelAreKept() {
        Aquifer aquifer = aquifer(5L, 0, 0, uniform(70));
        for (int y = MIN_Y; y < 100; y += 5) {
            assertThat(CaveCarver.carve(aquifer, 4, y, 4, false)).isEqualTo(Carve.KEEP);
        }
    }

    @Test
    void surfaceHeightsMustCoverTheFourByFourArray() {
        assertThatThrownBy(() -> aquifer(1L, 0, 0, new int[15])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisionsAreTheCarveEnumOnly() {
        for (long seed : new long[] {0L, 42L, -7654321L}) {
            for (char c : AquiferGoldenTest.decisions(seed, 5, -5).toCharArray()) {
                assertThat(c).isIn('A', 'W', 'L', '.');
            }
        }
    }
}
