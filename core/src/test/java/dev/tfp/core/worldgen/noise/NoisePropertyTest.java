/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.noise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Properties of the ported noise that do not depend on TFC's exact numbers: output range, determinism for a seed,
 * seed sensitivity, and the invariants of cellular noise and clamping.
 */
class NoisePropertyTest {
    private static final int SAMPLES = 10_000;

    @Test
    void simplexIsBoundedAndNotDegenerate() {
        final OpenSimplex2D noise = new OpenSimplex2D(12345L);
        final Random random = new Random(7L);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < SAMPLES; i++) {
            final double v = noise.noise(random.nextDouble() * 2000 - 1000, random.nextDouble() * 2000 - 1000);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertThat(min).isGreaterThanOrEqualTo(-1.0).isLessThan(-0.5);
        assertThat(max).isLessThanOrEqualTo(1.0).isGreaterThan(0.5);
    }

    @Test
    void scaledLayerNoiseStaysNearItsRange() {
        // octaves(3) is FBm normalised to about [-1, 1], so scaled(43, 63) stays about within [43, 63].
        final Noise2D height = new OpenSimplex2D(99L).octaves(3).scaled(43, 63).spread(0.014f);
        final Random random = new Random(11L);
        for (int i = 0; i < SAMPLES; i++) {
            final double v = height.noise(random.nextDouble() * 4000 - 2000, random.nextDouble() * 4000 - 2000);
            assertThat(v).isBetween(42.0, 64.0);
        }
    }

    @Test
    void sameSeedGivesSameValues() {
        final Random random = new Random(3L);
        final OpenSimplex2D a = new OpenSimplex2D(-5L);
        final OpenSimplex2D b = new OpenSimplex2D(-5L);
        for (int i = 0; i < 1_000; i++) {
            final double x = random.nextDouble() * 500, z = random.nextDouble() * 500;
            assertThat(a.noise(x, z)).isEqualTo(b.noise(x, z));
        }
    }

    @Test
    void differentSeedsGiveDifferentValues() {
        final Random random = new Random(5L);
        final OpenSimplex2D a = new OpenSimplex2D(1L);
        final OpenSimplex2D b = new OpenSimplex2D(2L);
        int different = 0;
        for (int i = 0; i < 1_000; i++) {
            final double x = random.nextDouble() * 500, z = random.nextDouble() * 500;
            if (a.noise(x, z) != b.noise(x, z)) {
                different++;
            }
        }
        assertThat(different).isGreaterThan(990);
    }

    @Test
    void seedFoldingKeepsOnlyTheFoldedBits() {
        // TFC folds the long seed as (int) (seed ^ (seed >> 32)): 1L << 32 folds to 1, so it matches seed 1.
        final Random random = new Random(9L);
        for (int i = 0; i < 100; i++) {
            final double x = random.nextDouble() * 100, z = random.nextDouble() * 100;
            assertThat(new OpenSimplex2D(1L << 32).noise(x, z)).isEqualTo(new OpenSimplex2D(1L).noise(x, z));
        }
    }

    @Test
    void clampedStaysWithinBounds() {
        final Noise2D clamped = new OpenSimplex2D(42L).clamped(-0.3, 0.4);
        final Random random = new Random(13L);
        for (int i = 0; i < SAMPLES; i++) {
            final double v = clamped.noise(random.nextDouble() * 1000, random.nextDouble() * 1000);
            assertThat(v).isBetween(-0.3, 0.4);
        }
    }

    @Test
    void cellularFieldsAreOrderedAndBounded() {
        final Cellular2D cells = new Cellular2D(77L);
        final Random random = new Random(17L);
        for (int i = 0; i < SAMPLES; i++) {
            final double x = random.nextDouble() * 1000 - 500, z = random.nextDouble() * 1000 - 500;
            final Cellular2D.Cell cell = cells.cell(x, z);
            assertThat(cell.f1()).isGreaterThanOrEqualTo(0.0);
            assertThat(cell.f1()).isLessThanOrEqualTo(cell.f2());
            assertThat(cell.noise()).isBetween(-1.0, 1.0);
            assertThat(cells.cell(x, z)).isEqualTo(cell);
        }
    }
}
