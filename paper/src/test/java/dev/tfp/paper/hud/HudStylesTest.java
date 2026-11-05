/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.health.HealthDisplay;
import dev.tfp.core.health.HealthDisplayStyle;
import dev.tfp.paper.hud.HudStyles.DisabledExperienceBarStyle;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HudStylesTest {
    @Test
    void absentSectionIsTfcsDefaultsWithoutWarnings() {
        HudStyles.Parsed p = HudStyles.parse(null);
        assertThat(p.styles()).isEqualTo(HudStyles.DEFAULTS);
        assertThat(p.warnings()).isEmpty();
        assertThat(HudStyles.parse(Map.of()).styles()).isEqualTo(HudStyles.DEFAULTS);
        assertThat(HudStyles.DEFAULTS.health()).isEqualTo(HealthDisplayStyle.TFC);
        assertThat(HudStyles.DEFAULTS.climate()).isEqualTo(ClimateTemperatureStyle.CELSIUS);
        assertThat(HudStyles.DEFAULTS.experienceBar()).isTrue();
        assertThat(HudStyles.DEFAULTS.disabledExperienceBar()).isEqualTo(DisabledExperienceBarStyle.HOVER);
    }

    @Test
    void readsEveryKeyByTfcsNames() {
        HudStyles.Parsed p = HudStyles.parse(Map.of(
                "health-display-style", "VANILLA",
                "climate-temperature-style", "fahrenheit",
                "disabled-experience-bar-style", "left_hotbar"));
        assertThat(p.warnings()).isEmpty();
        assertThat(p.styles()).isEqualTo(new HudStyles(HealthDisplayStyle.VANILLA, ClimateTemperatureStyle.FAHRENHEIT,
                true, DisabledExperienceBarStyle.LEFT_HOTBAR));
    }

    @Test
    void badValuesTakeTheDefaultWithAWarning() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("health-display-style", "HEARTS");
        m.put("climate-temperature-style", 5);
        m.put("enable-experience-bar", "no");
        m.put("disabled-experience-bar-style", null);
        m.put("colour", "red");
        HudStyles.Parsed p = HudStyles.parse(m);
        assertThat(p.styles()).isEqualTo(HudStyles.DEFAULTS);
        assertThat(p.warnings()).hasSize(5);
        assertThat(p.warnings()).anySatisfy(w -> assertThat(w).startsWith("hud.health-display-style: unknown style 'HEARTS'")
                .contains("TFC_CURRENT").endsWith("using TFC"));
        assertThat(p.warnings()).anySatisfy(w -> assertThat(w).isEqualTo("unknown key 'hud.colour' (ignored)"));
        assertThat(p.warnings()).anySatisfy(w -> assertThat(w).startsWith("hud.enable-experience-bar: expected true or false"));
    }

    @Test
    void nonMapSectionWarns() {
        HudStyles.Parsed p = HudStyles.parse("VANILLA");
        assertThat(p.styles()).isEqualTo(HudStyles.DEFAULTS);
        assertThat(p.warnings()).containsExactly("hud: expected a section, got 'VANILLA'; using defaults");
    }

    @Test
    void hiddenXpBarIsKeptButWarnsThatItIsNotBuilt() {
        HudStyles.Parsed p = HudStyles.parse(Map.of("enable-experience-bar", false, "disabled-experience-bar-style", "BUMP"));
        assertThat(p.styles().experienceBar()).isFalse();
        assertThat(p.styles().disabledExperienceBar()).isEqualTo(DisabledExperienceBarStyle.BUMP);
        assertThat(p.warnings()).singleElement().asString().contains("not supported yet");
    }

    @Test
    void toMapRoundTrips() {
        HudStyles s = new HudStyles(HealthDisplayStyle.VANILLA_CURRENT, ClimateTemperatureStyle.KELVIN, true,
                DisabledExperienceBarStyle.BUMP);
        assertThat(s.toMap().keySet()).containsExactlyElementsOf(HudStyles.KEYS);
        assertThat(HudStyles.parse(s.toMap()).styles()).isEqualTo(s);
        assertThat(HudStyles.parse(HudStyles.DEFAULTS.toMap()).warnings()).isEmpty();
    }

    @Test
    void textsFollowTheStyle() {
        HealthDisplay full = HealthDisplay.of(20f, 20f, 0.85f, HealthConfig.DEFAULT);
        assertThat(HudStyles.DEFAULTS.healthText(full, HealthConfig.DEFAULT)).isEqualTo("850/850");
        HudStyles vanillaF = HudStyles.parse(Map.of("health-display-style", "VANILLA",
                "climate-temperature-style", "FAHRENHEIT")).styles();
        assertThat(vanillaF.healthText(full, HealthConfig.DEFAULT)).isEqualTo("17.0/17.0");
        assertThat(HudStyles.DEFAULTS.temperatureText(20.0)).isEqualTo("20.0°C");
        assertThat(vanillaF.temperatureText(20.0)).isEqualTo("68.0°F");
        assertThat(vanillaF.temperatureText(Double.NaN)).isEqualTo(ClimateFormat.UNKNOWN);
    }
}
