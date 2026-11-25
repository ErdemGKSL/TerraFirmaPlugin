/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Golden values of TFC's {@code ConfigTextFunction} with the default config (PAR-DATA-04). */
class GuideConfigTest {
    @Test
    void durationsAreCalendarDaysOrHours() {
        assertThat(GuideConfig.duration(86400)).isEqualTo("3 day(s)"); // torchTicks: 72 player hours
        assertThat(GuideConfig.duration(288000)).isEqualTo("10 day(s)"); // candleTicks, composterTicks
        assertThat(GuideConfig.duration(108000)).isEqualTo("4 day(s)"); // jackOLanternTicks: 3.75 days rounds to 4
        assertThat(GuideConfig.duration(7200)).isEqualTo("6 hour(s)"); // pitKilnTicks
        assertThat(GuideConfig.duration(-1)).isEqualTo("an indefinite amount of time");
    }

    @Test
    void temperaturesAndDecayFactors() {
        assertThat(GuideConfig.climateTemperature(-9.4f)).isEqualTo("-9.4°C");
        assertThat(GuideConfig.climateTemperature(40f)).isEqualTo("40.0°C");
        assertThat(GuideConfig.temperature(1400)).isEqualTo("1400°C");
        assertThat(GuideConfig.decayMultiplier(0.5)).isEqualTo("2");
        assertThat(GuideConfig.decayMultiplier(0.8)).isEqualTo("1.25");
        assertThat(GuideConfig.decayMultiplier(1.25)).isEqualTo("0.8");
        assertThat(GuideConfig.decayMultiplier(0)).isEqualTo("∞");
    }

    @Test
    void heatLevelsCarryTfcColours() {
        GuideConfig config = new GuideConfig(null, new GuideNames(Map.of("tfc.enum.heat.orange", "Orange")));
        assertThat(config.heat(930)).isEqualTo("$(6)Orange");
        assertThat(config.heat(1500)).isEqualTo("$(f)Brilliant White");
        assertThat(config.heat(0)).isEmpty();
    }

    @Test
    void namesFallBackToTheirPath() {
        GuideNames names = new GuideNames(Map.of("item.tfc.firestarter", "Firestarter"));
        assertThat(names.item("tfc:firestarter")).isEqualTo("Firestarter");
        assertThat(names.item("minecraft:blue_dye")).isEqualTo("Blue Dye");
        assertThat(names.item("tfc:ceramic/ingot_mold[tfc:fluid={}]")).isEqualTo("Ingot Mold");
        assertThat(names.tag("c:rods/wooden")).isEqualTo("Wooden Rods");
        assertThat(names.tag("#c:strings")).isEqualTo("Strings");
    }
}
