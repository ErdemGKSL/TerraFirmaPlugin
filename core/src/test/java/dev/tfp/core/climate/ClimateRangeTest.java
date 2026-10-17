/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static dev.tfp.core.climate.ClimateRange.Result.HIGH;
import static dev.tfp.core.climate.ClimateRange.Result.LOW;
import static dev.tfp.core.climate.ClimateRange.Result.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link ClimateRange} and {@link ClimateRanges}. TFC has no numeric examples in the source comments
 * and no unit tests for this class at c3b4d9536, so the expected results come from TFC's {@code check} arithmetic
 * (inclusive bounds, LOW below the widened minimum, HIGH above the widened maximum) and from the shipped
 * {@code climate_range/crop/wheat.json} (hydration 15..85, temperature -7.0..22.0).
 */
class ClimateRangeTest {
    /** TFC's wheat window, {@code data/tfc/climate_range/crop/wheat.json}. */
    private static final ClimateRange WHEAT = new ClimateRange(15, 85, 0, -7f, 22f, 0f);

    @Test
    void hydrationBoundsAreInclusive() {
        assertThat(WHEAT.checkHydration(15, false)).isEqualTo(VALID);
        assertThat(WHEAT.checkHydration(85, false)).isEqualTo(VALID);
        assertThat(WHEAT.checkHydration(14, false)).isEqualTo(LOW);
        assertThat(WHEAT.checkHydration(86, false)).isEqualTo(HIGH);
        assertThat(WHEAT.checkHydration(50, false)).isEqualTo(VALID);
        assertThat(WHEAT.checkHydration(0, false)).isEqualTo(LOW);
        assertThat(WHEAT.checkHydration(100, false)).isEqualTo(HIGH);
    }

    @Test
    void temperatureBoundsAreInclusive() {
        assertThat(WHEAT.checkTemperature(-7f, false)).isEqualTo(VALID);
        assertThat(WHEAT.checkTemperature(22f, false)).isEqualTo(VALID);
        assertThat(WHEAT.checkTemperature(Math.nextDown(-7f), false)).isEqualTo(LOW);
        assertThat(WHEAT.checkTemperature(Math.nextUp(22f), false)).isEqualTo(HIGH);
        assertThat(WHEAT.checkTemperature(-30f, false)).isEqualTo(LOW);
        assertThat(WHEAT.checkTemperature(40f, false)).isEqualTo(HIGH);
    }

    @Test
    void wiggleWidensBothBoundsOnlyWhenAllowed() {
        ClimateRange wiggly = new ClimateRange(20, 80, 5, -10f, 30f, 2.5f);

        assertThat(wiggly.checkHydration(15, true)).isEqualTo(VALID);
        assertThat(wiggly.checkHydration(14, true)).isEqualTo(LOW);
        assertThat(wiggly.checkHydration(85, true)).isEqualTo(VALID);
        assertThat(wiggly.checkHydration(86, true)).isEqualTo(HIGH);
        assertThat(wiggly.checkHydration(15, false)).isEqualTo(LOW);
        assertThat(wiggly.checkHydration(85, false)).isEqualTo(HIGH);

        assertThat(wiggly.checkTemperature(-12.5f, true)).isEqualTo(VALID);
        assertThat(wiggly.checkTemperature(Math.nextDown(-12.5f), true)).isEqualTo(LOW);
        assertThat(wiggly.checkTemperature(32.5f, true)).isEqualTo(VALID);
        assertThat(wiggly.checkTemperature(Math.nextUp(32.5f), true)).isEqualTo(HIGH);
        assertThat(wiggly.checkTemperature(-11f, false)).isEqualTo(LOW);
    }

    @Test
    void widenedBoundsAreExposedAsTheCheckUsesThem() {
        ClimateRange wiggly = new ClimateRange(20, 80, 5, -10f, 30f, 2.5f);
        assertThat(wiggly.getMinHydration(true)).isEqualTo(15);
        assertThat(wiggly.getMaxHydration(true)).isEqualTo(85);
        assertThat(wiggly.getMinHydration(false)).isEqualTo(20);
        assertThat(wiggly.getMaxHydration(false)).isEqualTo(80);
        assertThat(wiggly.getMinTemperature(true)).isEqualTo(-12.5f);
        assertThat(wiggly.getMaxTemperature(true)).isEqualTo(32.5f);
        assertThat(wiggly.getMinTemperature(false)).isEqualTo(-10f);
        assertThat(wiggly.getMaxTemperature(false)).isEqualTo(30f);
    }

    @Test
    void checkBothNeedsBothValid() {
        assertThat(WHEAT.checkBoth(50, 10f, false)).isTrue();
        assertThat(WHEAT.checkBoth(15, -7f, false)).isTrue();
        assertThat(WHEAT.checkBoth(14, 10f, false)).isFalse();
        assertThat(WHEAT.checkBoth(50, 23f, false)).isFalse();
        assertThat(WHEAT.checkBoth(14, 23f, false)).isFalse();

        ClimateRange wiggly = new ClimateRange(20, 80, 5, -10f, 30f, 2.5f);
        assertThat(wiggly.checkBoth(16, -11f, true)).isTrue();
        assertThat(wiggly.checkBoth(16, -11f, false)).isFalse();
    }

    @Test
    void noopAcceptsEverythingInsideMinusHundredToHundred() {
        assertThat(ClimateRange.NOOP.checkBoth(0, -100f, false)).isTrue();
        assertThat(ClimateRange.NOOP.checkBoth(100, 100f, false)).isTrue();
        assertThat(ClimateRange.NOOP.checkBoth(101, 0f, false)).isFalse();
        assertThat(ClimateRange.NOOP.checkTemperature(Math.nextDown(-100f), false)).isEqualTo(LOW);
    }

    @Test
    void sharedCheckIsTheWholeArithmetic() {
        assertThat(ClimateRanges.check(5f, 5f, 5f, 0f, false)).isEqualTo(VALID);
        assertThat(ClimateRanges.check(4.9f, 5f, 5f, 0f, false)).isEqualTo(LOW);
        assertThat(ClimateRanges.check(5.1f, 5f, 5f, 0f, false)).isEqualTo(HIGH);
        assertThat(ClimateRanges.check(4.5f, 5f, 5f, 0.5f, true)).isEqualTo(VALID);
        assertThat(ClimateRanges.check(4.5f, 5f, 5f, 0.5f, false)).isEqualTo(LOW);
    }

    @Test
    void decodesTfcKeysWithTfcDefaults() {
        ClimateRange wheat = ClimateRange.CODEC.decode(JsonParser.parseString(
                "{\"max_hydration\": 85, \"max_temperature\": 22.0, \"min_hydration\": 15, \"min_temperature\": -7.0}"), "wheat");
        assertThat(wheat).isEqualTo(WHEAT);

        ClimateRange banana = ClimateRange.CODEC.decode(JsonParser.parseString(
                "{\"max_temperature\": 41.2, \"min_hydration\": 25, \"min_temperature\": 9.8}"), "banana");
        assertThat(banana.maxHydration()).isEqualTo(100);
        assertThat(banana.minHydration()).isEqualTo(25);
        assertThat(banana.minTemperature()).isEqualTo(9.8f);
        assertThat(banana.maxTemperature()).isEqualTo(41.2f);
        assertThat(banana.checkTemperature(1000f, false)).isEqualTo(HIGH);

        ClimateRange open = ClimateRange.CODEC.decode(JsonParser.parseString("{}"), "open");
        assertThat(open).isEqualTo(new ClimateRange(0, 100, 0, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0f));
        assertThat(open.checkTemperature(-1e30f, false)).isEqualTo(VALID);
    }

    @Test
    void rejectsUnknownKeysAndNonFiniteNumbers() {
        assertThatThrownBy(() -> ClimateRange.CODEC.decode(JsonParser.parseString("{\"min_rainfall\": 3}"), "bad"))
                .hasMessageContaining("min_rainfall");
        assertThatThrownBy(() -> ClimateRange.CODEC.decode(JsonParser.parseString("{\"min_hydration\": 1.5}"), "bad"))
                .hasMessageContaining("min_hydration");
    }
}
