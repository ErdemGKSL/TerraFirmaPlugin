/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater.hot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PAR-FLUID-04: the pure hot spring and bubble column rules. */
class HotSpringRulesTest {
    /** A small block map: water and gravel where set, everything else other. */
    private static final class Map3 implements HotSpringRules.Blocks {
        final Map<String, HotSpringRules.Kind> blocks = new HashMap<>();

        Map3 set(int x, int y, int z, HotSpringRules.Kind k) {
            blocks.put(x + "," + y + "," + z, k);
            return this;
        }

        @Override
        public HotSpringRules.Kind kind(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, HotSpringRules.Kind.OTHER);
        }
    }

    /** A pool one block deep at y 70 over x 0..4, z 0, bedded with gravel, with a fissure of water at x 2 down to y 60. */
    private static Map3 pool() {
        Map3 m = new Map3();
        for (int x = 0; x <= 4; x++) {
            m.set(x, 70, 0, HotSpringRules.Kind.WATER);
            m.set(x, 69, 0, HotSpringRules.Kind.GRAVEL);
        }
        for (int y = 60; y <= 69; y++) {
            m.set(2, y, 0, HotSpringRules.Kind.WATER);
        }
        return m;
    }

    @Test
    void healRollsAreOneInTenPerTickAndBlock() {
        RandomGenerator r = new SplittableRandom(4);
        int n = 200_000;
        double healed = HotSpringRules.heal(0, 1e9, 2, n / 2, 1.0, r);
        // one success per ten rolls on average, each worth the amount
        assertThat(healed / n).isCloseTo(0.1, within(0.003));
    }

    @Test
    void healStopsAtMaxHealthAndOnlyWhenHurt() {
        assertThat(HotSpringRules.heal(20, 20, 4, 1000, HotSpringRules.HOT_WATER_HEAL_AMOUNT, new SplittableRandom(1))).isEqualTo(20);
        double h = HotSpringRules.heal(19.99, 20, 4, 1000, HotSpringRules.HOT_WATER_HEAL_AMOUNT, new SplittableRandom(2));
        assertThat(h).isEqualTo(20);
        assertThat(HotSpringRules.heal(10, 20, 0, 1000, 0.08, new SplittableRandom(3))).as("no spring water").isEqualTo(10);
    }

    @Test
    void tfcDefaults() {
        assertThat(HotSpringRules.HOT_WATER_HEAL_AMOUNT).isEqualTo(0.08);
        assertThat(HotSpringRules.BUBBLE_COLUMN_PROVIDES_AIR).isFalse();
    }

    @Test
    void poolAndFissureWaterIsSpringWater() {
        Map3 m = pool();
        assertThat(HotSpringRules.isSpringWater(m, 0, 70, 0)).isTrue();
        assertThat(HotSpringRules.isSpringWater(m, 4, 70, 0)).isTrue();
        // the fissure: water down to rock, told by the gravel-bedded pool cells around its mouth
        assertThat(HotSpringRules.isSpringWater(m, 2, 65, 0)).isTrue();
        assertThat(HotSpringRules.waterTop(m, 2, 61, 0)).isEqualTo(70);
    }

    @Test
    void waterAtOrBelowSeaLevelOrNotOnGravelIsNot() {
        Map3 sea = new Map3().set(0, 63, 0, HotSpringRules.Kind.WATER).set(0, 62, 0, HotSpringRules.Kind.GRAVEL);
        assertThat(HotSpringRules.isSpringWater(sea, 0, 63, 0)).as("TFC skips y <= sea level").isFalse();
        Map3 rock = new Map3().set(0, 70, 0, HotSpringRules.Kind.WATER);
        assertThat(HotSpringRules.isSpringWater(rock, 0, 70, 0)).as("a pool on rock").isFalse();
        Map3 deep = new Map3();
        for (int y = 64; y < 64 + HotSpringRules.MAX_SCAN + 2; y++) {
            deep.set(0, y, 0, HotSpringRules.Kind.WATER);
        }
        assertThat(HotSpringRules.isSpringWater(deep, 0, 64, 0)).as("deeper than the scan").isFalse();
    }

    @Test
    void onlyChunksNearAnActiveSpringBiome() {
        assertThat(HotSpringRules.activeSpringBiome(TfcBiomes.OLD_MOUNTAINS)).isTrue();
        assertThat(HotSpringRules.activeSpringBiome(TfcBiomes.CANYONS)).isTrue();
        assertThat(HotSpringRules.activeSpringBiome(TfcBiomes.VOLCANIC_MOUNTAINS)).isTrue();
        assertThat(HotSpringRules.activeSpringBiome(TfcBiomes.DORMANT_SHIELD_VOLCANO)).isTrue();
        assertThat(HotSpringRules.activeSpringBiome(TfcBiomes.PLATEAU)).as("empty springs hold no water").isFalse();
        assertThat(HotSpringRules.activeSpringBiome(TfcBiomes.OCEAN)).isFalse();
        // a neighbour chunk's centre is an active spring biome
        assertThat(HotSpringRules.springChunk(0, 0, (x, z) -> x == 1 && z == -1 ? TfcBiomes.CANYONS : TfcBiomes.PLAINS))
                .contains(true);
        assertThat(HotSpringRules.springChunk(0, 0, (x, z) -> x == 2 ? TfcBiomes.CANYONS : TfcBiomes.PLAINS)).contains(false);
        assertThat(HotSpringRules.springChunk(0, 0, (x, z) -> x == 0 && z == 0 ? -1 : TfcBiomes.PLAINS))
                .as("unknown").isEqualTo(Optional.empty());
    }

    @Test
    void airLossIsVanillas() {
        RandomGenerator r = new SplittableRandom(5);
        assertThat(HotSpringRules.air(300, 0, r)).isEqualTo(new HotSpringRules.Air(299, false));
        assertThat(HotSpringRules.air(-19, 0, r)).as("drowns at -20").isEqualTo(new HotSpringRules.Air(0, true));
        // respiration III keeps the air three times in four
        int air = 0;
        int n = 40_000;
        for (int i = 0; i < n; i++) {
            air += 1000 - HotSpringRules.air(1000, 3, r).air();
        }
        assertThat(air / (double) n).isCloseTo(0.25, within(0.01));
    }
}
