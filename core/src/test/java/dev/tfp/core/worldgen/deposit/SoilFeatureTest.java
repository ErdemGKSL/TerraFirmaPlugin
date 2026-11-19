/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.deposit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.groundcover.SyntheticView;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-09: the water clay disc, rooted dirt, powder snow and the mollisol discs. */
class SoilFeatureTest {
    /** A view with a fixed climate and forest density. */
    private static class Climate extends SyntheticView {
        private final float temperature;
        private final float water;
        private final int forest;

        Climate(float temperature, float water, int forest) {
            super(TfcBiomes.PLAINS);
            this.temperature = temperature;
            this.water = water;
            this.forest = forest;
        }

        @Override
        public float averageTemperature(int x, int z) {
            return temperature;
        }

        @Override
        public float groundwater(int x, int z) {
            return water;
        }

        @Override
        public float averageRainfall(int x, int z) {
            return water;
        }

        @Override
        public float rainVariance(int x, int z) {
            return 0;
        }

        @Override
        public int forestDensity(int x, int z) {
            return forest;
        }
    }

    private static Deposits.Feature feature(String id) {
        return Deposits.FEATURES.stream().filter(f -> f.feature().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void rootedDirtNeverPassesItsFuzzyFilterAsInTfc() {
        // fuzzy with infinite temperature bounds: TFC's target temperature is NaN, so the fuzzy test is always false
        final Deposits.Feature rooted = feature("tfc:rooted_dirt");
        for (int forest = 0; forest <= 4; forest++) {
            final GroundView view = new Climate(15, 200, forest);
            for (int i = 0; i < 2000; i++) {
                assertThat(Deposits.place(new XoroshiroRandomSource(i), rooted, 0, 0, view, (x, y, z) -> { })).isFalse();
            }
        }
    }

    @Test
    void unknownForestFailsForestFilters() {
        final GroundView unknown = new SyntheticView(TfcBiomes.PLAINS);
        assertThat(unknown.forestDensity(0, 0)).isEqualTo(-1);
        for (int i = 0; i < 200; i++) {
            assertThat(MollisolDiscs.place(new XoroshiroRandomSource(i), 0, 0, unknown, (x, z, b, t) -> { })).isFalse();
        }
    }

    @Test
    void waterClayNeedsFreshWaterNearby() {
        final GroundView dry = new Climate(15, 200, 0) {
            @Override
            public int worldSurface(int x, int z) {
                return oceanFloor(x, z);
            }
        };
        final GroundView fresh = new Climate(15, 200, 0) {
            @Override
            public int oceanFloor(int x, int z) {
                return x == 3 && z == -4 ? 60 : 64;
            }

            @Override
            public int worldSurface(int x, int z) {
                return 64;
            }

            @Override
            public boolean saltWater(int x, int z) {
                return false;
            }
        };
        final GroundView salt = new Climate(15, 200, 0) {
            @Override
            public int oceanFloor(int x, int z) {
                return x == 3 && z == -4 ? 60 : 64;
            }

            @Override
            public int worldSurface(int x, int z) {
                return 64;
            }

            @Override
            public boolean saltWater(int x, int z) {
                return true;
            }
        };
        assertThat(Deposits.nearWater(dry, 0, 64, 0, 4)).isFalse();
        assertThat(Deposits.nearWater(fresh, 0, 64, 0, 4)).isTrue();
        assertThat(Deposits.nearWater(fresh, 0, 64, 0, 2)).isFalse();
        assertThat(Deposits.nearWater(salt, 0, 64, 0, 4)).isFalse();
        final Deposits.Feature water = feature("tfc:water_clay_disc_with_indicator");
        assertThat(water.nearWater()).isEqualTo(4);
        assertThat(water.disc().replace()).containsEntry(DepositMaterial.DIRT, DepositKind.CLAY);
    }

    @Test
    void powderSnowOnlyAboveSeaLevelInTheCold() {
        final GroundView cold = new Climate(-20, 200, 0) {
            @Override
            public int worldSurface(int x, int z) {
                return 70;
            }
        };
        int placed = 0;
        for (int i = 0; i < 200; i++) {
            final int[] count = {0};
            if (PowderSnow.place(new XoroshiroRandomSource(i), 0, 0, cold, (x, y, z) -> {
                assertThat(y).isGreaterThan(63).isBetween(70 - 9, 70 + 9);
                assertThat(Math.abs(x - 8)).isLessThanOrEqualTo(9 + 8);
                count[0]++;
            })) {
                placed++;
            }
        }
        // rarity 2
        assertThat(placed).isBetween(70, 130);
        final GroundView warm = new Climate(-13, 200, 0);
        for (int i = 0; i < 200; i++) {
            assertThat(PowderSnow.place(new XoroshiroRandomSource(i), 0, 0, warm, (x, y, z) -> { })).isFalse();
        }
    }

    @Test
    void mollisolCoversSparseForestAndNotDenseForest() {
        final Set<Long> sparse = new HashSet<>();
        assertThat(MollisolDiscs.place(new XoroshiroRandomSource(1), 0, 0, new Climate(0, 300, 0),
            (x, z, bottom, top) -> sparse.add((long) x << 32 | (z & 0xFFFFFFFFL)))).isTrue();
        // forest 0: the whole chunk (forest + noise <= 1 maps to at most 0.17, inverted at least 0.83)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertThat(sparse).contains((long) x << 32 | (z & 0xFFFFFFFFL));
            }
        }
        final Set<Long> dense = new HashSet<>();
        assertThat(MollisolDiscs.place(new XoroshiroRandomSource(1), 0, 0, new Climate(0, 300, 3),
            (x, z, bottom, top) -> dense.add((long) x << 32 | (z & 0xFFFFFFFFL)))).isTrue();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertThat(dense).doesNotContain((long) x << 32 | (z & 0xFFFFFFFFL));
            }
        }
        // the climate: warmer than 3 degrees or denser than 3 places nothing
        assertThat(MollisolDiscs.place(new XoroshiroRandomSource(1), 0, 0, new Climate(4, 300, 0), (x, z, b, t) -> { })).isFalse();
        assertThat(MollisolDiscs.place(new XoroshiroRandomSource(1), 0, 0, new Climate(0, 300, 4), (x, z, b, t) -> { })).isFalse();
    }

    @Test
    void mollisolColumnsOutsideTheChunkUseTheWrappedHeight() {
        final GroundView view = new Climate(0, 300, 0);
        MollisolDiscs.place(new XoroshiroRandomSource(5), 16, 32, view, (x, z, bottom, top) -> {
            assertThat(top).isEqualTo(view.oceanFloor(16 + (x & 15), 32 + (z & 15)));
            assertThat(top - bottom).isEqualTo(7);
            assertThat(x).isBetween(16 - 3, 16 + 18);
            assertThat(z).isBetween(32 - 3, 32 + 18);
        });
    }

    @Test
    void depositsChunkTurnsSoilsToMollisolOnce() {
        final Climate view = new Climate(0, 300, 0);
        final List<DepositBlock> blocks = new Deposits(11L).chunk(0, 0, view, SyntheticView::material);
        final Set<Long> mollisol = new HashSet<>();
        int count = 0;
        for (DepositBlock b : blocks) {
            if (b.kind() == DepositKind.MOLLISOL) {
                count++;
                assertThat(mollisol.add(Deposits.key(b.x(), b.y(), b.z()))).isTrue();
                assertThat(b.x() >> 4 == 0 && b.z() >> 4 == 0).isTrue();
                assertThat(b.replaced()).isIn(DepositMaterial.DIRT, DepositMaterial.GRASS, DepositMaterial.MUD);
            }
        }
        assertThat(count).isPositive();
    }
}
