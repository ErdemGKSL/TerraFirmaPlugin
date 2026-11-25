/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weathering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.weathering.WeatheringExposure.Site;
import dev.tfp.core.weathering.WeatheringRules.Age;
import org.junit.jupiter.api.Test;

/** PAR-METAL-17: the drip, climate and neighbour terms, with values worked out by hand from TFC's formulas. */
class WeatheringExposureTest {
    private static final float EPS = 1e-6f;

    @Test
    void climateModifierGoldenValues() {
        // temp 0 deg: |-0.5 + 40/80| * 2 = 0; rainfall 250 -> 0.5; climate 0; raining 1 * wind 1 -> weather 1
        assertThat(WeatheringExposure.climateModifier(0f, 250f, 1f, true)).isCloseTo(0.2f * 0.6f + 1.2f * 0.4f, within(EPS));
        // temp 40 deg -> 1; rainfall 500 -> 1; climate 1; dry 0.5 * wind 0.5 -> 0.25
        assertThat(WeatheringExposure.climateModifier(40f, 500f, 0.5f, false)).isCloseTo(1.2f * 0.6f + 0.45f * 0.4f, within(EPS));
        // temperature and rainfall are clamped
        assertThat(WeatheringExposure.climateModifier(90f, 900f, 3f, true)).isCloseTo(1.2f * 0.6f + 1.2f * 0.4f, within(EPS));
        assertThat(WeatheringExposure.climateModifier(-90f, -5f, -1f, false)).isCloseTo(0.2f * 0.6f + 0.2f * 0.4f, within(EPS));
    }

    @Test
    void neighbourModifierNeedsMoreThanFortyPercent() {
        assertThat(WeatheringExposure.neighborModifier(new Site(2, 5, 0, 0, true))).isEqualTo(1f); // exactly 0.4 is not more
        assertThat(WeatheringExposure.neighborModifier(new Site(3, 7, 0, 0, true))).isEqualTo(WeatheringExposure.CROWDED_MODIFIER);
        assertThat(WeatheringExposure.neighborModifier(new Site(0, 1, 0, 0, true))).isEqualTo(1f);
    }

    @Test
    void dripPathNeedsFewAdjacentAndCountsAboveBlocks() {
        assertThat(WeatheringExposure.dripChance(Age.NONE, new Site(0, 3, 4, 2, false))).isZero();
        assertThat(WeatheringExposure.dripChance(Age.NONE, new Site(0, 3, 3, 2, false))).isCloseTo(0.5f * 0.4f, within(EPS));
        assertThat(WeatheringExposure.dripChance(Age.EXPOSED, new Site(0, 3, 0, 4, false))).isCloseTo(0.8f, within(EPS));
        // a crowded stage is damped, the first stage is not
        assertThat(WeatheringExposure.dripChance(Age.EXPOSED, new Site(3, 5, 0, 4, false)))
                .isCloseTo(0.8f * WeatheringExposure.CROWDED_MODIFIER, within(EPS));
        assertThat(WeatheringExposure.dripChance(Age.NONE, new Site(3, 5, 0, 4, false))).isCloseTo(0.4f, within(EPS));
    }

    @Test
    void climatePathNeedsOpenSkyAndTheLargerPathWins() {
        Site closed = new Site(0, 1, 0, 1, false);
        Site open = new Site(0, 1, 0, 1, true);
        assertThat(WeatheringExposure.climatePathChance(Age.NONE, closed, 0.7f)).isZero();
        assertThat(WeatheringExposure.climatePathChance(Age.NONE, open, 0.7f)).isCloseTo(0.35f, within(EPS));
        assertThat(WeatheringExposure.extraChance(Age.WEATHERED, open, 0.7f)).isCloseTo(0.7, within(1e-6));
        assertThat(WeatheringExposure.extraChance(Age.WEATHERED, new Site(0, 1, 0, 4, true), 0.3f)).isCloseTo(0.8, within(1e-6));
    }

    @Test
    void badSitesAreRejected() {
        assertThatThrownBy(() -> new Site(0, 0, 0, 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Site(2, 1, 0, 0, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
