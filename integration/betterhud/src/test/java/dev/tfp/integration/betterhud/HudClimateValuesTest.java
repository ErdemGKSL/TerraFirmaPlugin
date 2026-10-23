/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatLevel;
import dev.tfp.paper.integration.HudSnapshot;
import org.junit.jupiter.api.Test;

class HudClimateValuesTest {
    @Test
    void ambientTemperatureIsOneDecimalPlace() {
        assertThat(HudClimateValues.TEXTS.keySet()).containsExactly("tfp_ambient_temp", "tfp_held_heat");
        HudSnapshot reading = HudSnapshot.DEFAULT.withAmbientTemperature(12.34);
        assertThat(HudClimateValues.TEXTS.get("tfp_ambient_temp").apply(reading)).isEqualTo("12.3\u00B0C");
    }

    @Test
    void dashUntilTheFirstReading() {
        assertThat(HudClimateValues.TEXTS.get("tfp_ambient_temp").apply(HudSnapshot.DEFAULT)).isEqualTo("-");
    }

    /** PAR-HUD-08: the held heat text is TFC's COLOR style of the snapshot value (900 C: Bright Red, 4 stars as TFC). */
    @Test
    void heldHeatIsTheTfcLevelTextOfTheSnapshotValue() {
        var text = HudClimateValues.TEXTS.get("tfp_held_heat");
        // TFC: stars for i in 1..4 while t > 730 + i * 0.2 * 200 -> 770, 810, 850, 890 all passed at 900
        assertThat(text.apply(HudSnapshot.DEFAULT.withHeldTemperature(900))).isEqualTo("Bright Red\u066D\u066D\u066D\u066D");
        assertThat(text.apply(HudSnapshot.DEFAULT.withHeldTemperature(800))).isEqualTo("Bright Red\u066D");
        assertThat(text.apply(HudSnapshot.DEFAULT.withHeldTemperature(40))).isEqualTo("Warming\u066D\u066D");
        assertThat(text.apply(HudSnapshot.DEFAULT.withHeldTemperature(1550))).isEqualTo("Brilliant White");
        assertThat(text.apply(HudSnapshot.DEFAULT.withHeldTemperature(0))).isEmpty();
        assertThat(text.apply(HudSnapshot.DEFAULT)).isEmpty();
    }

    /** The en_us names of TFC's {@code tfc.enum.heat.*} keys. */
    @Test
    void levelNamesAreTfcEnUs() {
        assertThat(HudClimateValues.levelName(HeatLevel.VERY_HOT)).isEqualTo("Very Hot");
        assertThat(HudClimateValues.levelName(HeatLevel.YELLOW_WHITE)).isEqualTo("Yellow White");
        assertThat(HudClimateValues.levelName(HeatLevel.BRILLIANT_WHITE)).isEqualTo("Brilliant White");
        assertThat(HudClimateValues.levelName(HeatLevel.ORANGE)).isEqualTo("Orange");
    }

    /** The number placeholder the popup condition reads is the snapshot value. */
    @Test
    void heldTempPlaceholderReturnsTheSnapshotValue() {
        assertThat(HudValues.NUMBERS.get("tfp_held_temp").applyAsDouble(HudSnapshot.DEFAULT.withHeldTemperature(900)))
                .isEqualTo(900);
    }
}
