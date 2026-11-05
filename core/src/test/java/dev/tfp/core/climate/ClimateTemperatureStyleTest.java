/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Golden numbers exported from TFC's {@code ClimateTemperatureDisplayStyle} expressions (its float arithmetic and
 * {@code String.format("%.1f")}, a scratch program outside the repo at c3b4d9536).
 */
class ClimateTemperatureStyleTest {
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "20;20.0;68.0;527.7;293.1",
            "0;0.0;32.0;491.7;273.1",
            "-40;-40.0;-40.0;419.7;233.1",
            "12.3;12.3;54.1;513.8;285.4",
            "37.05;37.0;98.7;558.4;310.2",
            "100;100.0;212.0;671.7;373.1",
            "23.9;23.9;75.0;534.7;297.0",
            "-273.15;-273.1;-459.7;0.0;0.0",
    })
    void everyUnitMatchesTfc(float celsius, String c, String f, String r, String k) {
        assertThat(ClimateTemperatureStyle.CELSIUS.format(celsius)).isEqualTo(c + "°C");
        assertThat(ClimateTemperatureStyle.FAHRENHEIT.format(celsius)).isEqualTo(f + "°F");
        assertThat(ClimateTemperatureStyle.RANKINE.format(celsius)).isEqualTo(r + "°R");
        assertThat(ClimateTemperatureStyle.KELVIN.format(celsius)).isEqualTo(k + " K");
    }

    @Test
    void negativeZeroShowsAsZero() {
        // TFC writes "-0.0" for these; ours, like ClimateFormat, writes "0.0" (ledger divergence).
        assertThat(ClimateTemperatureStyle.FAHRENHEIT.format(-17.7778f)).isEqualTo("0.0°F");
        assertThat(ClimateTemperatureStyle.CELSIUS.format(-0.04f)).isEqualTo("0.0°C");
    }

    @Test
    void unknownValuesShowADash() {
        for (ClimateTemperatureStyle s : ClimateTemperatureStyle.values()) {
            assertThat(s.format(Float.NaN)).isEqualTo(ClimateFormat.UNKNOWN);
            assertThat(s.format(Float.NEGATIVE_INFINITY)).isEqualTo(ClimateFormat.UNKNOWN);
        }
    }

    @Test
    void defaultsAndOrderAreTfcs() {
        assertThat(ClimateTemperatureStyle.DEFAULT).isEqualTo(ClimateTemperatureStyle.CELSIUS);
        assertThat(ClimateTemperatureStyle.values()).extracting(Enum::name)
                .containsExactly("CELSIUS", "FAHRENHEIT", "RANKINE", "KELVIN");
    }

    @Test
    void ignoresTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(ClimateTemperatureStyle.KELVIN.format(20f)).isEqualTo("293.1 K");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void byName() {
        assertThat(ClimateTemperatureStyle.byName("fahrenheit")).hasValue(ClimateTemperatureStyle.FAHRENHEIT);
        assertThat(ClimateTemperatureStyle.byName("RÉAUMUR")).isEmpty();
        assertThat(ClimateTemperatureStyle.byName(null)).isEmpty();
    }
}
