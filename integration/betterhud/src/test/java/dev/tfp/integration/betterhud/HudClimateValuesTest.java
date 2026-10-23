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
        assertThat(HudClimateValues.TEXTS.keySet()).containsExactly("tfp_ambient_temp", "tfp_held_heat", "tfp_held_heat_color");
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

    /** The level names come from our en_us lang ({@code heat.level.*}, TFC's {@code tfc.enum.heat.*} en_us names). */
    @Test
    void levelNamesAreTheEnUsLangEntries() {
        // VERY_HOT 210..480: one star past 264; YELLOW_WHITE 1300..1400: two past 1340; ORANGE 930..1100: none at 931
        assertThat(HudClimateValues.heatText(300)).isEqualTo("Very Hot٭");
        assertThat(HudClimateValues.heatText(1350)).isEqualTo("Yellow White٭٭");
        assertThat(HudClimateValues.heatText(931)).isEqualTo("Orange");
    }

    /** The text and colour are empty exactly where TFC's {@code Heat.getHeat} (our {@link HeatLevel#of}) has no level. */
    @Test
    void textIsEmptyExactlyWhereThereIsNoHeatLevel() {
        float[] samples = {Float.NaN, -50f, -0.001f, 0f, Float.MIN_VALUE, 0.001f, 1f, 79.9f, 80f, 729.9f, 730f, 1499.9f,
                1500f, 1599f, 1600f, 2000f};
        for (float t : samples) {
            boolean hasLevel = HeatLevel.of(t).isPresent();
            assertThat(HudClimateValues.heatText(t).isEmpty()).as("text at %s", t).isEqualTo(!hasLevel);
            assertThat(HudClimateValues.heatColor(t).isEmpty()).as("colour at %s", t).isEqualTo(!hasLevel);
        }
        assertThat(HudClimateValues.heatText(1550)).isEqualTo("Brilliant White");
    }

    /** TFC {@code Heat.getColor}: GRAY, DARK_RED, RED, GOLD, YELLOW, WHITE by level group, as chat-colour hex codes. */
    @Test
    void heldHeatColourIsTfcHeatColour() {
        var colour = HudClimateValues.TEXTS.get("tfp_held_heat_color");
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(900))).isEqualTo("#ff5555"); // BRIGHT_RED: RED
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(40))).isEqualTo("#aaaaaa"); // WARMING: GRAY
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(479))).isEqualTo("#aaaaaa"); // VERY_HOT: GRAY
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(500))).isEqualTo("#aa0000"); // FAINT_RED
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(700))).isEqualTo("#aa0000"); // DARK_RED
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(1000))).isEqualTo("#ffaa00"); // ORANGE: GOLD
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(1150))).isEqualTo("#ffff55"); // YELLOW
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(1350))).isEqualTo("#ffff55"); // YELLOW_WHITE
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(1450))).isEqualTo("#ffffff"); // WHITE
        assertThat(colour.apply(HudSnapshot.DEFAULT.withHeldTemperature(1550))).isEqualTo("#ffffff"); // BRILLIANT_WHITE
        assertThat(colour.apply(HudSnapshot.DEFAULT)).isEmpty();
    }

    /** The number placeholder the popup condition reads is the snapshot value. */
    @Test
    void heldTempPlaceholderReturnsTheSnapshotValue() {
        assertThat(HudValues.NUMBERS.get("tfp_held_temp").applyAsDouble(HudSnapshot.DEFAULT.withHeldTemperature(900)))
                .isEqualTo(900);
    }
}
