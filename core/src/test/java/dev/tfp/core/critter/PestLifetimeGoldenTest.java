/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/Pest.java @ c3b4d9536 (tick, the 3 minute age test and the 1 in 500 roll, line 162)
 * (golden values only)
 */
package dev.tfp.core.critter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the lazy discard age. The per-tick roll tests ages 3601, 3602 and so on with chance 1 / 500 each, so the
 * first hit is geometric. The expected ages below are computed from {@code k = ceil(ln(1 - u) / ln(0.998))} in a scratch script
 * (kept outside the repository), and the test asserts the same values with the Java code.
 */
class PestLifetimeGoldenTest {

    /** The first tick past 3600 always has a 1 in 500 chance, so {@code u = 0} gives the age 3601. */
    @Test
    void zeroDrawDiscardsTheFirstTickPastThreeMinutes() {
        assertThat(PestLifetime.discardAgeFromUniform(0.0)).isEqualTo(3601L);
    }

    /** The median: {@code ln(0.5) / ln(0.998)} is about 346.2, so k is 347 and the age is 3947. */
    @Test
    void medianDrawIsAboutSixHundredTicksOfLife() {
        assertThat(PestLifetime.discardAgeFromUniform(0.5)).isEqualTo(3947L);
    }

    /** {@code u = 0.25}: {@code ln(0.75) / ln(0.998)} is about 143.7, so k is 144 and the age is 3744. */
    @Test
    void lowerQuartileDraw() {
        assertThat(PestLifetime.discardAgeFromUniform(0.25)).isEqualTo(3744L);
    }

    /** {@code u = 0.9}: {@code ln(0.1) / ln(0.998)} is about 1150.1, so k is 1151 and the age is 4751. */
    @Test
    void highDrawIsLate() {
        assertThat(PestLifetime.discardAgeFromUniform(0.9)).isEqualTo(4751L);
    }

    /** {@code u = 0.999}: {@code ln(0.001) / ln(0.998)} is about 3450.4, so k is 3451 and the age is 7051. */
    @Test
    void nearlyOneDrawIsVeryLate() {
        assertThat(PestLifetime.discardAgeFromUniform(0.999)).isEqualTo(7051L);
    }

    /** The age never goes down as the draw rises, and it is always past the three minute mark. */
    @Test
    void agesAreMonotoneAndPastTheMinimum() {
        long previous = 0;
        for (double u = 0; u < 1; u += 0.01) {
            long age = PestLifetime.discardAgeFromUniform(u);
            assertThat(age).isGreaterThan(PestLifetime.MIN_AGE_TICKS).isGreaterThanOrEqualTo(previous);
            previous = age;
        }
    }

    @Test
    void drawOutsideTheUnitIntervalIsRejected() {
        assertThatThrownBy(() -> PestLifetime.discardAgeFromUniform(1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
