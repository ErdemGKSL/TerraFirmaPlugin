/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.soil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-05: the grass tick decisions of IGrassBlock, ConnectedGrassBlock and PlantRegrowth. */
class GrassRulesTest {
    @Test
    void canBeGrassFollowsSnowFluidAndLight() {
        assertThat(GrassRules.canBeGrass(true, true, 15)).isTrue(); // snow wins
        assertThat(GrassRules.canBeGrass(false, true, 0)).isFalse(); // full fluid above
        assertThat(GrassRules.canBeGrass(false, false, 0)).isTrue(); // air
        assertThat(GrassRules.canBeGrass(false, false, 14)).isTrue(); // leaves
        assertThat(GrassRules.canBeGrass(false, false, 15)).isFalse(); // full block
    }

    @Test
    void propagateNeedsNoFluidAbove() {
        assertThat(GrassRules.canPropagate(false, false, 0, true)).isFalse(); // shallow water above
        assertThat(GrassRules.canPropagate(false, false, 0, false)).isTrue();
    }

    @Test
    void spreadNeedsLightNine() {
        assertThat(GrassRules.canSpread(8)).isFalse();
        assertThat(GrassRules.canSpread(9)).isTrue();
    }

    @Test
    void spreadOffsetDrawsInTfcOrderAndRange() {
        Random a = new Random(42);
        Random b = new Random(42);
        int[] o = GrassRules.spreadOffset(a);
        assertThat(o).containsExactly(b.nextInt(3) - 1, b.nextInt(5) - 3, b.nextInt(3) - 1);
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            int[] v = GrassRules.spreadOffset(r);
            assertThat(v[0]).isBetween(-1, 1);
            assertThat(v[1]).isBetween(-3, 1);
            assertThat(v[2]).isBetween(-1, 1);
        }
    }

    @Test
    void slimeChunkMatchesVanillaFormulaAndIsAboutOneInFive() {
        // WorldgenRandom.seedSlimeChunk(cx, cz, seed, salt) = new Random(seed + cx*cx*4987142 + cx*5947611 + cz*cz*4392871L + cz*389711 ^ salt)
        long seed = 123456789L;
        int cx = -7;
        int cz = 12;
        long expected = (seed + (long) (cx * cx * 4987142) + (long) (cx * 5947611) + (long) (cz * cz) * 4392871L
            + (long) (cz * 389711)) ^ 6942069420L;
        assertThat(GrassRules.slimeChunkRandom(cx, cz, seed, 6942069420L).nextInt()).isEqualTo(new Random(expected).nextInt());
        int hits = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                if (GrassRules.risingRockChunk(x, z, seed)) {
                    hits++;
                }
            }
        }
        assertThat(hits).isBetween(1700, 2300);
    }

    @Test
    void risingRockGatesInOrder() {
        assertThat(GrassRules.risingRockGates(0.0005f, 0.001, true, 5f, false)).isTrue();
        assertThat(GrassRules.risingRockGates(0.5f, 0.001, true, 5f, false)).isFalse(); // roll above chance
        assertThat(GrassRules.risingRockGates(0.0005f, 0.001, false, 5f, false)).isFalse(); // not spring
        assertThat(GrassRules.risingRockGates(0.0005f, 0.001, true, 8.1f, false)).isFalse(); // too warm
        assertThat(GrassRules.risingRockGates(0.0005f, 0.001, true, 8f, false)).isTrue();
        assertThat(GrassRules.risingRockGates(0.0005f, 0.001, true, 5f, true)).isFalse(); // player near
    }
}
