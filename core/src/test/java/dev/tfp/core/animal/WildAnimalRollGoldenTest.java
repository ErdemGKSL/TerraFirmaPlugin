/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/WildAnimal.java @ c3b4d9536
 * (finalizeSpawn, tick; golden values only)
 */
package dev.tfp.core.animal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * TFC's wild-animal spawn roll ({@code setIsMale(nextBoolean())}, {@code setBaby(nextFloat() < 0.1f)}) and the lazy
 * grow-up that replaces {@code gameTime % 4000 == 0 && nextInt(2000) == 0}. The grow ticks below come from
 * {@code k = ceil(ln(1 - u) / ln(0.9995))} computed in a scratch script (kept outside the repository).
 */
class WildAnimalRollGoldenTest {
    private static final long NOW = 10_000L;
    private static final double Q = 1999.0 / 2000.0;

    /** Returns the scripted values in order and fails on a draw of the wrong kind or an unused draw. */
    private static final class Script implements WildAnimalRoll.Draws {
        private final Deque<Object> values = new ArrayDeque<>();

        Script(Object... v) {
            values.addAll(List.of(v));
        }

        @Override
        public boolean nextBoolean() {
            return (Boolean) values.removeFirst();
        }

        @Override
        public float nextFloat() {
            return (Float) values.removeFirst();
        }

        @Override
        public double nextDouble() {
            return (Double) values.removeFirst();
        }

        void assertDone() {
            assertThat(values).as("unused draws").isEmpty();
        }
    }

    @Test
    void exactlyTenPercentIsNotABabyAndDrawsNoGrowUp() {
        Script s = new Script(true, 0.1f);
        WildAnimalRoll.Rolled r = WildAnimalRoll.roll(s, NOW, AnimalSpawnRoll.AgeHint.RANDOM, null);
        s.assertDone();
        assertThat(r).isEqualTo(new WildAnimalRoll.Rolled(true, false, AnimalRecord.NO_GROW_TICK));
    }

    @Test
    void justBelowTenPercentIsABabyAndDrawsItsGrowUp() {
        Script s = new Script(false, Math.nextDown(0.1f), 0.5);
        WildAnimalRoll.Rolled r = WildAnimalRoll.roll(s, NOW, AnimalSpawnRoll.AgeHint.RANDOM, null);
        s.assertDone();
        assertThat(r.male()).isFalse();
        assertThat(r.baby()).isTrue();
        assertThat(r.growTick()).isEqualTo(12_000L + 1385L * 4000L);
    }

    @Test
    void hintsAndGenderOverrideTheResultsButKeepTfcDraws() {
        Script child = new Script(true, 0.99f, 0.0);
        WildAnimalRoll.Rolled c = WildAnimalRoll.roll(child, NOW, AnimalSpawnRoll.AgeHint.CHILD, false);
        child.assertDone();
        assertThat(c).isEqualTo(new WildAnimalRoll.Rolled(false, true, 12_000L));

        Script adult = new Script(false, 0.01f);
        WildAnimalRoll.Rolled a = WildAnimalRoll.roll(adult, NOW, AnimalSpawnRoll.AgeHint.ADULT, true);
        adult.assertDone();
        assertThat(a).isEqualTo(new WildAnimalRoll.Rolled(true, false, AnimalRecord.NO_GROW_TICK));

        Script old = new Script(false, 0.01f);
        assertThat(WildAnimalRoll.roll(old, NOW, AnimalSpawnRoll.AgeHint.OLD, null).baby()).isFalse();
        old.assertDone();
    }

    @Test
    void growTickGoldens() {
        assertThat(WildAnimalRoll.growTickFromUniform(NOW, 0.0)).isEqualTo(12_000L);
        assertThat(WildAnimalRoll.growTickFromUniform(NOW, 0.25)).isEqualTo(12_000L + 575L * 4000L);
        assertThat(WildAnimalRoll.growTickFromUniform(NOW, 0.5)).isEqualTo(12_000L + 1385L * 4000L);
        assertThat(WildAnimalRoll.growTickFromUniform(NOW, 0.9)).isEqualTo(12_000L + 4604L * 4000L);
        assertThat(WildAnimalRoll.growTickFromUniform(NOW, 0.999)).isEqualTo(12_000L + 13812L * 4000L);
        // A spawn on a boundary rolls first on the next one; negative ticks floor.
        assertThat(WildAnimalRoll.growTickFromUniform(12_000L, 0.0)).isEqualTo(16_000L);
        assertThat(WildAnimalRoll.growTickFromUniform(-1L, 0.0)).isEqualTo(0L);
        assertThatThrownBy(() -> WildAnimalRoll.growTickFromUniform(NOW, 1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** The lazy draw gives boundary k exactly on [1 - q^(k-1), 1 - q^k): tested at the middle of each interval. */
    @Test
    void lazyBoundaryCountIsTheGeometricQuantile() {
        for (long k : new long[] {1, 2, 3, 10, 577, 1386, 4605}) {
            double u = 1 - Math.pow(Q, k - 0.5);
            assertThat(WildAnimalRoll.growTickFromUniform(NOW, u)).as("k=%d", k).isEqualTo(12_000L + (k - 1) * 4000L);
        }
    }

    @Test
    void steppedRollDrawsOnlyOnBoundaryTicks() {
        IntUnaryOperator never = bound -> {
            throw new AssertionError("drew on a non-boundary tick");
        };
        assertThat(WildAnimalRoll.growsUpOnTick(12_001L, never)).isFalse();
        assertThat(WildAnimalRoll.growsUpOnTick(11_999L, never)).isFalse();
        assertThat(WildAnimalRoll.growsUpOnTick(12_000L, bound -> 0)).isTrue();
        assertThat(WildAnimalRoll.growsUpOnTick(12_000L, bound -> 1)).isFalse();
    }

    /** Lazy grow-up has the distribution of TFC's stepped roll: mean, median and upper quartile agree. */
    @Test
    void lazyGrowUpMatchesSteppedGrowUpInDistribution() {
        int n = 6000;
        long[] stepped = new long[n];
        long[] lazy = new long[n];
        Random a = new Random(42);
        Random b = new Random(7);
        long first = WildAnimalRoll.firstBoundaryAfter(NOW);
        for (int i = 0; i < n; i++) {
            long t = first;
            while (!WildAnimalRoll.growsUpOnTick(t, a::nextInt)) {
                t += WildAnimalRoll.GROW_INTERVAL_TICKS;
            }
            stepped[i] = t;
            lazy[i] = WildAnimalRoll.growTickFromUniform(NOW, b.nextDouble());
        }
        // Boundaries to the first hit: geometric, mean 2000, sd about 2000, so the standard error is about 26.
        assertThat(meanBoundaries(stepped, first)).isBetween(1870.0, 2130.0);
        assertThat(meanBoundaries(lazy, first)).isBetween(1870.0, 2130.0);
        // P(k <= 1386) = 1 - q^1386 = 0.5 and P(k <= 2773) = 0.75; the standard error is under 0.007.
        long median = first + 1385L * 4000L;
        long upper = first + 2772L * 4000L;
        assertThat(share(stepped, median)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.03));
        assertThat(share(lazy, median)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.03));
        assertThat(share(stepped, upper)).isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.03));
        assertThat(share(lazy, upper)).isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.03));
        for (long t : lazy) {
            assertThat(Math.floorMod(t, WildAnimalRoll.GROW_INTERVAL_TICKS)).isZero();
            assertThat(t).isGreaterThan(NOW);
        }
    }

    @Test
    void wildSpeciesAreTheThirtyOneWildAnimalRegistrationsAndNoneIsLivestock() {
        assertThat(WildAnimalRoll.SPECIES).hasSize(31);
        for (String id : WildAnimalRoll.SPECIES) {
            Carrier c = CarrierTable.get().find(id).orElseThrow(() -> new AssertionError(id + " has no carrier"));
            assertThat(c.wildAnimal()).isTrue();
            assertThat(c.livestock()).as(id).isEmpty();
        }
        assertThat(CarrierTable.get().find("tfc:cow").orElseThrow().wildAnimal()).isFalse();
        assertThat(CarrierTable.get().find("tfc:cod").orElseThrow().wildAnimal()).isFalse();
    }

    private static double meanBoundaries(long[] ticks, long first) {
        double sum = 0;
        for (long t : ticks) {
            sum += (t - first) / (double) WildAnimalRoll.GROW_INTERVAL_TICKS + 1;
        }
        return sum / ticks.length;
    }

    private static double share(long[] ticks, long atMost) {
        int c = 0;
        for (long t : ticks) {
            if (t <= atMost) {
                c++;
            }
        }
        return c / (double) ticks.length;
    }
}
