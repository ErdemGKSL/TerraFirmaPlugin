/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.health.HealthDisplayStyle;
import dev.tfp.paper.hud.HudStyles;
import dev.tfp.paper.hud.HudStylesHolder;
import dev.tfp.paper.integration.HudSnapshot;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** PAR-HUD-17: {@code tfp_ambient_temp} follows {@code hud.climate-temperature-style} through the published styles. */
class HudClimateValuesStyleTest {
    static final HudSnapshot WARM = HudSnapshot.DEFAULT.withAmbientTemperature(20);

    static HudStyles unit(ClimateTemperatureStyle style) {
        return new HudStyles(HealthDisplayStyle.DEFAULT, style, true, HudStyles.DisabledExperienceBarStyle.HOVER);
    }

    @AfterEach
    void reset() {
        HudStylesHolder.publish(HudStyles.DEFAULTS);
    }

    @Test
    void eachUnitAsTfc() {
        assertThat(HudClimateValues.ambientText(WARM, unit(ClimateTemperatureStyle.CELSIUS))).isEqualTo("20.0°C");
        assertThat(HudClimateValues.ambientText(WARM, unit(ClimateTemperatureStyle.FAHRENHEIT))).isEqualTo("68.0°F");
        assertThat(HudClimateValues.ambientText(WARM, unit(ClimateTemperatureStyle.RANKINE))).isEqualTo("527.7°R");
        // TFC's float arithmetic: 20 + 273.15f is 293.149994, so %.1f writes 293.1.
        assertThat(HudClimateValues.ambientText(WARM, unit(ClimateTemperatureStyle.KELVIN))).isEqualTo("293.1 K");
        for (ClimateTemperatureStyle s : ClimateTemperatureStyle.values()) {
            assertThat(HudClimateValues.ambientText(HudSnapshot.DEFAULT, unit(s))).isEqualTo("-");
        }
    }

    /**
     * TFC formats the float: 37.05 as a float is 37.049999, so CELSIUS shows 37.0 (our old ClimateFormat rounded the
     * double half-up to 37.1). Ledger row PAR-HUD-17.
     */
    @Test
    void celsiusFormatsTheFloatAsTfc() {
        HudSnapshot hot = HudSnapshot.DEFAULT.withAmbientTemperature(37.05);
        assertThat(HudClimateValues.ambientText(hot, HudStyles.DEFAULTS)).isEqualTo("37.0°C");
        assertThat(HudClimateValues.ambientText(HudSnapshot.DEFAULT.withAmbientTemperature(-0.04), HudStyles.DEFAULTS))
                .isEqualTo("0.0°C");
    }

    @Test
    void theRegisteredPlaceholderReadsThePublishedStyles() {
        Function<HudSnapshot, String> text = HudClimateValues.TEXTS.get("tfp_ambient_temp");
        assertThat(text.apply(WARM)).isEqualTo("20.0°C");
        HudStylesHolder.publish(unit(ClimateTemperatureStyle.FAHRENHEIT));
        assertThat(text.apply(WARM)).isEqualTo("68.0°F");
        HudStylesHolder.publish(HudStyles.DEFAULTS);
        assertThat(text.apply(WARM)).isEqualTo("20.0°C");
    }
}
