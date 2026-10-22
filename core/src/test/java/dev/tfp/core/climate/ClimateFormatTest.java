/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClimateFormatTest {
    @Test
    void temperatureHasOneDecimalPlaceAndAUnit() {
        assertThat(ClimateFormat.temperature(12.34)).isEqualTo("12.3\u00B0C");
        assertThat(ClimateFormat.temperature(20f)).isEqualTo("20.0\u00B0C");
        assertThat(ClimateFormat.temperature(-12.0)).isEqualTo("-12.0\u00B0C");
    }

    @Test
    void roundsHalfUpInTenthsAndNeverShowsNegativeZero() {
        assertThat(ClimateFormat.temperature(-3.26)).isEqualTo("-3.3\u00B0C");
        assertThat(ClimateFormat.temperature(-0.04)).isEqualTo("0.0\u00B0C");
        assertThat(ClimateFormat.temperature(0.0)).isEqualTo("0.0\u00B0C");
    }

    @Test
    void unknownValuesShowADash() {
        assertThat(ClimateFormat.temperature(Double.NaN)).isEqualTo("-");
        assertThat(ClimateFormat.temperature(Double.POSITIVE_INFINITY)).isEqualTo("-");
        assertThat(ClimateFormat.rainfall(Double.NaN)).isEqualTo("-");
    }

    @Test
    void rainfallUsesMillimetres() {
        assertThat(ClimateFormat.rainfall(250f)).isEqualTo("250.0 mm");
    }
}
