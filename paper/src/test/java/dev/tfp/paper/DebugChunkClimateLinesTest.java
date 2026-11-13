/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.paper.hud.HudStyles;
import org.junit.jupiter.api.Test;

/** PAR-HUD-18: the climate lines of {@code /tfp debug chunk} use {@code hud.climate-temperature-style}, like the HUD. */
class DebugChunkClimateLinesTest {
    private static HudStyles styles(ClimateTemperatureStyle climate) {
        return new HudStyles(HudStyles.DEFAULTS.health(), climate, true, HudStyles.DEFAULTS.disabledExperienceBar());
    }

    private static final ClimateReading READING = new ClimateReading(1, 1200, 20f, 10f, 250f);

    @Test
    void climateLineInFahrenheit() {
        assertThat(TfpPlugin.climateLine(READING, styles(ClimateTemperatureStyle.FAHRENHEIT)))
                .isEqualTo("climate now: temperature 68.0°F, average 50.0°F, rainfall 250.0 mm (calendar tick 1200)");
    }

    @Test
    void climateLineInCelsiusIsTheDefault() {
        String expected = "climate now: temperature 20.0°C, average 10.0°C, rainfall 250.0 mm (calendar tick 1200)";
        assertThat(TfpPlugin.climateLine(READING, styles(ClimateTemperatureStyle.CELSIUS))).isEqualTo(expected);
        assertThat(TfpPlugin.climateLine(READING, HudStyles.DEFAULTS)).isEqualTo(expected);
    }

    @Test
    void climateLineInKelvinAndUnknown() {
        assertThat(TfpPlugin.climateLine(READING, styles(ClimateTemperatureStyle.KELVIN)))
                .as("TFC's float arithmetic: 20f + 273.15f prints as 293.1")
                .startsWith("climate now: temperature 293.1 K, average 283.1 K,");
        assertThat(TfpPlugin.climateLine(ClimateReading.unknown(0, 0), styles(ClimateTemperatureStyle.FAHRENHEIT)))
                .isEqualTo("climate now: unknown (no calendar reading yet, or not the overworld)");
    }

    @Test
    void hudLineFollowsTheStyle() {
        assertThat(TfpPlugin.hudTemperatureLine(20.0, true, styles(ClimateTemperatureStyle.FAHRENHEIT)))
                .isEqualTo("HUD tfp_ambient_temp: 68.0°F");
        assertThat(TfpPlugin.hudTemperatureLine(20.0, true, HudStyles.DEFAULTS))
                .isEqualTo("HUD tfp_ambient_temp: 20.0°C");
        assertThat(TfpPlugin.hudTemperatureLine(Double.NaN, true, HudStyles.DEFAULTS))
                .isEqualTo("HUD tfp_ambient_temp: " + ClimateFormat.UNKNOWN);
        assertThat(TfpPlugin.hudTemperatureLine(20.0, false, styles(ClimateTemperatureStyle.FAHRENHEIT)))
                .isEqualTo("HUD tfp_ambient_temp: not shown (no HUD plugin renders it)");
    }
}
